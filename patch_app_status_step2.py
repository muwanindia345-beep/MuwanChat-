#!/usr/bin/env python3
"""
STATUS STEP 2 (app) -- Repository (offline-first) + real time labels
  * data/StatusRepository.kt : cache-first feed, refresh, markSeen, toggleLike, create, delete
  * data/StatusTime.kt       : "5 minutes ago" / "Today, 9:14 AM" / "Yesterday, 10:02 PM"

Sirf 2 NAYI files banti hain -- koi existing file touch nahi hoti.
Step 1 pehle chal chuka hona chahiye (StatusApi + status_cache table).

Chalane ka tareeka (MuwanChat--main repo root se):
    python3 patch_app_status_step2.py
"""
import os
import sys

DATA = os.path.join("app", "src", "main", "java", "com", "muwan", "muwanchat", "data")
NET_API = os.path.join("app", "src", "main", "java", "com", "muwan", "muwanchat", "network", "StatusApi.kt")
DAO = os.path.join(DATA, "StatusCacheDao.kt")


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def create_file(path, content):
    if os.path.exists(path):
        print("[skip] pehle se hai : " + path)
        return
    write(path, content)
    print("[new ] bana diya    : " + path)


TIME_KT = '''package com.muwan.muwanchat.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Status ka real time label (WhatsApp jaisa). 24 ghante TTL hai, isliye sirf
// "minutes ago" / "Today" / "Yesterday" aate hain.
object StatusTime {

    fun label(createdAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMin = ((nowMs - createdAtMs) / 60_000L).coerceAtLeast(0L)
        if (diffMin < 1L) return "Just now"
        if (diffMin < 60L) return if (diffMin == 1L) "1 minute ago" else "$diffMin minutes ago"

        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(createdAtMs))
        val dayDiff = Math.round((startOfDay(nowMs) - startOfDay(createdAtMs)) / 86_400_000.0).toInt()
        return when {
            dayDiff <= 0 -> "Today, $time"
            dayDiff == 1 -> "Yesterday, $time"
            else -> time
        }
    }

    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
'''

REPO_KT = '''package com.muwan.muwanchat.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muwan.muwanchat.network.CreateStatusBody
import com.muwan.muwanchat.network.CreateStatusResponse
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.StatusFeedResponse
import com.muwan.muwanchat.network.StatusItemDto
import com.muwan.muwanchat.util.isNetworkAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// UI ke models (screens sirf yahi dekhenge, DTO nahi)
// ---------------------------------------------------------------------------
data class StatusItem(
    val id: String,
    val uid: String,
    val type: String,            // text | image | video | document | link
    val text: String,
    val bgColor: String?,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val fileName: String?,
    val mimeType: String?,
    val linkUrl: String?,
    val linkTitle: String?,
    val createdAtMs: Long,
    val seen: Boolean,
    val viewCount: Int,
    val likeCount: Int,
    val liked: Boolean
)

data class StatusUser(
    val uid: String,
    val username: String,
    val avatar: String?,         // base64 -> AvatarView
    val items: List<StatusItem>,
    val allSeen: Boolean,
    val latestAtMs: Long
)

data class StatusFeed(
    val mine: List<StatusItem>,
    val users: List<StatusUser>
) {
    /** "Recent updates": jinke saare status abhi tak nahi dekhe (orange ring) */
    val recent: List<StatusUser> get() = users.filter { !it.allSeen }

    /** "Viewed updates": saare dekh liye (grey ring) */
    val viewed: List<StatusUser> get() = users.filter { it.allSeen }
}

// ---------------------------------------------------------------------------
// Offline-first repository
//   screen khulte hi cachedFeed() (turant, offline bhi) -> phir refresh() background me.
//   Offline me dekhe gaye status / likes "pending" me jaate hain aur net aate hi sync hote hain.
// ---------------------------------------------------------------------------
class StatusRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val uid: String by lazy { AuthDataStore.getUidBlocking(appContext) }
    private val dao: StatusCacheDao by lazy {
        MuwanChatDb.get(appContext, uid).statusCacheDao()
    }

    companion object {
        const val TTL_MS = 24L * 60L * 60L * 1000L
        private const val KEY_FEED = "feed"
        private const val KEY_PENDING_VIEWS = "pending_views"
        private const val KEY_PENDING_LIKES = "pending_likes"

        // Mutex non-reentrant hai: jo functions "Locked" se khatam hote hain unhe
        // sirf lock ke andar se bulao, aur lock ke andar se dobara lock mat lo.
        private val lock = Mutex()
    }

    // ----------------------------- READ ------------------------------------

    /** Cache se turant feed (expired hata ke, pending seen/likes laga ke). Cache kabhi bana hi nahi to null. */
    suspend fun cachedFeed(): StatusFeed? = withContext(Dispatchers.IO) {
        lock.withLock {
            val dto = readFeedDtoLocked() ?: return@withLock null
            buildFeedLocked(dto)
        }
    }

    /** Server se fresh feed. true = cache update hua. Offline/error par false (cache jaisa tha waisa). */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(appContext)) return@withContext false
        try {
            val token = AuthDataStore.getToken(appContext).first() ?: return@withContext false
            syncPending(token)
            val res = RetrofitClient.statusApi.getFeed("Bearer $token")
            val body = res.body()
            if (res.isSuccessful && body != null) {
                lock.withLock { dao.upsert(StatusCacheEntity(KEY_FEED, gson.toJson(body))) }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // ----------------------------- ACTIONS ---------------------------------

    /** Status khola -> turant seen (offline bhi), server ko baad me bata dete hain. */
    suspend fun markSeen(statusId: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                writeStringSetLocked(KEY_PENDING_VIEWS, readStringSetLocked(KEY_PENDING_VIEWS) + statusId)
                mutateFeedLocked { feed ->
                    feed.copy(friends = feed.friends.map { u ->
                        val list = u.statuses.map { if (it.id == statusId) it.copy(seen = true) else it }
                        u.copy(statuses = list, all_seen = list.all { it.seen })
                    })
                }
            }
            trySyncNow()
        }
    }

    /** Heart tap. Local turant badalta hai, server sync baad me (backend likes patch ke baad kaam karega). */
    suspend fun toggleLike(statusId: String, like: Boolean) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                writeLikeMapLocked(readLikeMapLocked() + (statusId to like))
                mutateFeedLocked { feed ->
                    feed.copy(friends = feed.friends.map { u ->
                        u.copy(statuses = u.statuses.map {
                            if (it.id == statusId && it.liked != like) {
                                it.copy(
                                    liked = like,
                                    like_count = (it.like_count + if (like) 1 else -1).coerceAtLeast(0)
                                )
                            } else it
                        })
                    })
                }
            }
            trySyncNow()
        }
    }

    /** Naya status post (net zaroori; media/thumbnail pehle upload ho chuka hona chahiye). */
    suspend fun create(body: CreateStatusBody): Result<StatusItem> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(appContext)) {
            return@withContext Result.failure<StatusItem>(Exception("No internet connection"))
        }
        try {
            val token = AuthDataStore.getToken(appContext).first()
                ?: return@withContext Result.failure<StatusItem>(Exception("Please log in again"))
            val res = RetrofitClient.statusApi.create("Bearer $token", body)
            val created = res.body()?.status
            if (res.isSuccessful && res.body()?.success == true && created != null) {
                lock.withLock {
                    mutateFeedLocked(createIfMissing = true) { feed -> feed.copy(mine = feed.mine + created) }
                }
                Result.success(mapItem(created, uid))
            } else {
                Result.failure<StatusItem>(Exception(errorOf(res, "Couldn't post status")))
            }
        } catch (_: Exception) {
            Result.failure<StatusItem>(Exception("Couldn't post status. Check your connection."))
        }
    }

    /** Apna status delete (net zaroori). */
    suspend fun delete(statusId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(appContext)) return@withContext false
        try {
            val token = AuthDataStore.getToken(appContext).first() ?: return@withContext false
            val res = RetrofitClient.statusApi.delete("Bearer $token", statusId)
            if (res.isSuccessful) {
                lock.withLock {
                    mutateFeedLocked { feed -> feed.copy(mine = feed.mine.filter { it.id != statusId }) }
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // ----------------------------- SYNC ------------------------------------

    private suspend fun trySyncNow() {
        if (!isNetworkAvailable(appContext)) return
        try {
            val token = AuthDataStore.getToken(appContext).first() ?: return
            syncPending(token)
        } catch (_: Exception) {
        }
    }

    // Lock yahan NAHI pakadte network call ke dauran -- sirf chhote read/write ke liye.
    private suspend fun syncPending(token: String) {
        val bearer = "Bearer $token"

        val views = lock.withLock { readStringSetLocked(KEY_PENDING_VIEWS) }
        for (id in views) {
            val done = try {
                val r = RetrofitClient.statusApi.markViewed(bearer, id)
                r.isSuccessful || r.code() == 404 || r.code() == 403   // expire/hata hua -> dobara try nahi
            } catch (_: Exception) {
                false
            }
            if (done) {
                lock.withLock {
                    writeStringSetLocked(KEY_PENDING_VIEWS, readStringSetLocked(KEY_PENDING_VIEWS) - id)
                }
            }
        }

        val likes = lock.withLock { readLikeMapLocked() }
        for ((id, want) in likes) {
            val done = try {
                val r = if (want) {
                    RetrofitClient.statusApi.like(bearer, id)
                } else {
                    RetrofitClient.statusApi.unlike(bearer, id)
                }
                r.isSuccessful || r.code() == 404 || r.code() == 403
            } catch (_: Exception) {
                false
            }
            if (done) {
                lock.withLock { writeLikeMapLocked(readLikeMapLocked() - id) }
            }
        }
    }

    // ----------------------------- CACHE HELPERS ---------------------------

    private suspend fun readFeedDtoLocked(): StatusFeedResponse? {
        val json = try { dao.get(KEY_FEED)?.json } catch (_: Exception) { null } ?: return null
        return try { gson.fromJson(json, StatusFeedResponse::class.java) } catch (_: Exception) { null }
    }

    private suspend fun mutateFeedLocked(
        createIfMissing: Boolean = false,
        transform: (StatusFeedResponse) -> StatusFeedResponse
    ) {
        val base = readFeedDtoLocked() ?: if (createIfMissing) StatusFeedResponse() else return
        dao.upsert(StatusCacheEntity(KEY_FEED, gson.toJson(transform(base))))
    }

    private suspend fun readStringSetLocked(key: String): Set<String> {
        val json = try { dao.get(key)?.json } catch (_: Exception) { null } ?: return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            (gson.fromJson<List<String>>(json, type) ?: emptyList()).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private suspend fun writeStringSetLocked(key: String, value: Set<String>) {
        if (value.isEmpty()) dao.delete(key)
        else dao.upsert(StatusCacheEntity(key, gson.toJson(value.toList())))
    }

    private suspend fun readLikeMapLocked(): Map<String, Boolean> {
        val json = try { dao.get(KEY_PENDING_LIKES)?.json } catch (_: Exception) { null } ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Boolean>>() {}.type
            gson.fromJson<Map<String, Boolean>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun writeLikeMapLocked(value: Map<String, Boolean>) {
        if (value.isEmpty()) dao.delete(KEY_PENDING_LIKES)
        else dao.upsert(StatusCacheEntity(KEY_PENDING_LIKES, gson.toJson(value)))
    }

    // ----------------------------- MAPPING ---------------------------------

    private suspend fun buildFeedLocked(dto: StatusFeedResponse): StatusFeed {
        val now = System.currentTimeMillis()
        val pendingViews = readStringSetLocked(KEY_PENDING_VIEWS)
        val pendingLikes = readLikeMapLocked()

        fun item(d: StatusItemDto, owner: String): StatusItem? {
            val created = parseIso(d.created_at)
            if (created + TTL_MS <= now) return null            // 24 ghante pure -> hata do
            var liked = d.liked
            var likes = d.like_count
            val want = pendingLikes[d.id]
            if (want != null && want != d.liked) {
                liked = want
                likes = (likes + if (want) 1 else -1).coerceAtLeast(0)
            }
            return mapItem(d, owner).copy(
                seen = d.seen || d.id in pendingViews,
                likeCount = likes,
                liked = liked
            )
        }

        val mine = dto.mine.mapNotNull { item(it, uid) }.sortedBy { it.createdAtMs }
        val users = dto.friends.mapNotNull { u ->
            val items = u.statuses.mapNotNull { item(it, u.uid) }.sortedBy { it.createdAtMs }
            if (items.isEmpty()) null
            else StatusUser(
                uid = u.uid,
                username = u.username,
                avatar = u.avatar,
                items = items,
                allSeen = items.all { it.seen },
                latestAtMs = items.last().createdAtMs
            )
        }.sortedByDescending { it.latestAtMs }

        return StatusFeed(mine = mine, users = users)
    }

    private fun mapItem(d: StatusItemDto, owner: String) = StatusItem(
        id = d.id,
        uid = owner,
        type = d.type,
        text = d.text,
        bgColor = d.bg_color,
        mediaUrl = d.media_url,
        thumbnailUrl = d.thumbnail_url,
        fileName = d.file_name,
        mimeType = d.mime_type,
        linkUrl = d.link_url,
        linkTitle = d.link_title,
        createdAtMs = parseIso(d.created_at),
        seen = d.seen,
        viewCount = d.view_count,
        likeCount = d.like_count,
        liked = d.liked
    )

    private fun errorOf(res: retrofit2.Response<*>, fallback: String): String {
        return try {
            val raw = res.errorBody()?.string()
            gson.fromJson(raw, CreateStatusResponse::class.java)?.error ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    // minSdk 24 par java.time safe nahi, isliye SimpleDateFormat.
    private fun parseIso(s: String): Long {
        val patterns = arrayOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (p in patterns) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                val t = f.parse(s)?.time
                if (t != null) return t
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
'''


def main():
    if not os.path.isdir(DATA):
        print("[ERROR] %s folder nahi mila. Script ko MuwanChat--main repo root se chalao." % DATA)
        sys.exit(1)
    if not (os.path.isfile(NET_API) and os.path.isfile(DAO)):
        print("[ERROR] Step 1 pehle chalao (StatusApi.kt / StatusCacheDao.kt nahi mile).")
        sys.exit(1)

    create_file(os.path.join(DATA, "StatusTime.kt"), TIME_KT)
    create_file(os.path.join(DATA, "StatusRepository.kt"), REPO_KT)
    print("\nStep 2 done. Sync + Build karo. Agla: Step 3 (Status tab UI).")


if __name__ == "__main__":
    main()
