package com.muwan.muwanchat.data

// GUEST_GROUP
// Guest groups (app side). Backend sirf IDs + allowlist rakhta hai (routes/guestGroup.js); naam, photo,
// members ki keys aur messages sirf phones par. Message 1:1 jaise hi: har member ko alag E2EE envelope.
// Envelope kinds:
//   ginv  = group invite/update  { g, nm, av?, pk:{uid:pubB64}, ts }   (sirf admin bhejta hai)
//   gmsg  = group text message   { g, x, r?, ts }
// Receive par membership/admin hamesha SERVER se verify hoti hai (GET /guest/group/:id), sender ke daave se nahi.
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.muwan.muwanchat.screens.nowIso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GuestGroups {

    private val GROUP_ID = Regex("^gg_[0-9a-f]{32}$")
    private const val MAX_AVATAR_CHARS = 60_000

    class Group(val id: String, val name: String, val admin: String, val members: List<String>)

    // ok = server ne group bana diya; notSent = jinko invite envelope nahi pahunch saka (baad mein retry hoga)
    class CreateResult(val groupId: String?, val error: String?, val notSent: List<String>)

    private class Api(val code: Int, val json: JSONObject?)   // code <= 0: network / session fail

    private val baseUrl: String get() = com.muwan.muwanchat.BuildConfig.BACKEND_URL.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isGroupId(id: String?): Boolean = id != null && GROUP_ID.matches(id)

    // ───────── local storage (SharedPreferences, guest id ke hisaab se) ─────────

    private fun prefs(app: Context) =
        app.applicationContext.getSharedPreferences("guest_groups", Context.MODE_PRIVATE)

    private fun key(myId: String, gid: String) = "grp_" + myId + "_" + gid

    fun get(app: Context, myId: String, gid: String): Group? {
        val s = prefs(app).getString(key(myId, gid), null) ?: return null
        return try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("mem")
            val members = ArrayList<String>()
            for (i in 0 until arr.length()) members.add(arr.getString(i))
            Group(gid, o.getString("nm"), o.getString("adm"), members)
        } catch (_: Exception) {
            null
        }
    }

    private fun save(app: Context, myId: String, g: Group) {
        val o = JSONObject()
            .put("nm", g.name)
            .put("adm", g.admin)
            .put("mem", JSONArray(g.members))
        prefs(app).edit().putString(key(myId, g.id), o.toString()).apply()
    }

    // ───────── server API ─────────

    private suspend fun call(app: Context, method: String, path: String, body: JSONObject?): Api =
        withContext(Dispatchers.IO) {
            try {
                if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return@withContext Api(-1, null)
                val tk = GuestStore.token(app) ?: return@withContext Api(-1, null)
                val b = Request.Builder()
                    .url(baseUrl + path)
                    .header("Authorization", "Bearer " + tk)
                when (method) {
                    "GET" -> b.get()
                    else -> b.post((body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
                }
                http.newCall(b.build()).execute().use { r ->
                    val txt = r.body?.string() ?: ""
                    val j = try { JSONObject(txt) } catch (_: Exception) { null }
                    Api(r.code, j)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Api(-1, null)
            }
        }

    private fun stringList(arr: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotEmpty() && !out.contains(s)) out.add(s)
        }
        return out
    }

    // ───────── create (admin) ─────────

    // memberUids = chune hue guest contacts (apne aap ko mat daalo). avatarB64 = ProfileScreen jaisa 512px jpeg base64 (ya null).
    suspend fun create(app: Context, name: String, avatarB64: String?, memberUids: List<String>): CreateResult {
        val ctx = app.applicationContext
        val myId = GuestStore.activeGuestId(ctx)
        if (myId.isEmpty()) return CreateResult(null, "Guest session not active", emptyList())
        val cleanName = name.trim().take(40)
        if (cleanName.isEmpty()) return CreateResult(null, "Group name is required", emptyList())
        val members = memberUids.filter { it != myId }.distinct()
        if (members.isEmpty()) return CreateResult(null, "Select at least one member", emptyList())

        val api = call(ctx, "POST", "/guest/group", JSONObject().put("members", JSONArray(members)))
        val j = api.json
        if (api.code !in 200..299 || j == null) {
            val msg = j?.optString("error").orEmpty()
            return CreateResult(
                null,
                if (msg.isNotBlank()) msg else "Couldn't create group. Check your internet and try again.",
                emptyList()
            )
        }
        val gid = j.optString("id")
        if (!GROUP_ID.matches(gid)) return CreateResult(null, "Server error, try again", emptyList())
        val all = stringList(j.optJSONArray("members"))
        val group = Group(gid, cleanName, myId, all)
        save(ctx, myId, group)

        val avatar: String? = if (avatarB64.isNullOrBlank()) null else GuestProfile.smallAvatar(avatarB64).ifBlank { null }
        val db = MuwanChatDb.get(ctx, myId)
        ChatRepository.addConversationPlaceholder(db, gid, gid, cleanName, avatar, isGroup = true, memberCount = all.size)
        ChatRepository.recordMessage(
            db = db, id = "gsys-" + gid, roomId = gid, senderUid = myId, receiverUid = gid,
            content = "You created this group", type = "system", createdAt = nowIso(), myUid = myId,
            otherUsername = cleanName
        )

        // invite: sab members ki public keys saath mein (member ek doosre ko seedha E2EE bhej sakein)
        val pk = JSONObject()
        pk.put(myId, com.muwan.muwanchat.data.GuestCrypto.b64(GuestStore.identity(ctx).publicKey))
        for (uid in all) {
            if (uid == myId) continue
            GuestPeers.get(ctx, uid)?.let { pk.put(uid, GuestCrypto.b64(it)) }
        }
        val body = JSONObject()
            .put("g", gid)
            .put("nm", cleanName)
            .put("pk", pk)
            .put("ts", System.currentTimeMillis())
        if (avatar != null) body.put("av", avatar)

        val notSent = ArrayList<String>()
        for (uid in all) {
            if (uid == myId) continue
            val ok = GuestChat.sendEnvelope(ctx, uid, "gi-" + gid + "-" + uid.takeLast(8), "ginv", body)
            if (!ok) notSent.add(uid)
        }
        return CreateResult(gid, null, notSent)
    }

    // ───────── send ─────────

    // Har member ko (apne ko chhodkar) alag envelope. Same mid -> retry par server duplicate nahi banata.
    // true = sabko queue ho gaya. false = kisi ko nahi gaya (FAILED dikhao, retry par sab ko dobara).
    private suspend fun fanOut(app: Context, g: Group, myId: String, mid: String, kind: String, body: JSONObject): Boolean {
        var allOk = true
        for (uid in g.members) {
            if (uid == myId) continue
            if (!GuestChat.sendEnvelope(app, uid, mid, kind, body)) allOk = false
        }
        return allOk
    }

    suspend fun sendText(app: Context, gid: String, id: String, text: String, replyToId: String?): Boolean {
        val ctx = app.applicationContext
        val myId = GuestStore.activeGuestId(ctx)
        val g = get(ctx, myId, gid) ?: return false
        val body = JSONObject().put("g", gid).put("x", text).put("ts", System.currentTimeMillis())
        if (!replyToId.isNullOrBlank()) body.put("r", replyToId)
        return fanOut(ctx, g, myId, id, "gmsg", body)
    }

    // ───────── receive (GuestChat.applyEnvelope se bulaya jaata hai) ─────────

    suspend fun onEnvelope(
        app: Context, db: MuwanChatDb, myId: String, from: String, mid: String,
        kind: String, createdAt: String, senderName: String, body: JSONObject
    ) {
        when (kind) {
            "ginv" -> onInvite(app, db, myId, from, mid, createdAt, senderName, body)
            "gmsg" -> onMessage(app, db, myId, from, mid, createdAt, senderName, body)
        }
    }

    private fun validAvatar(a: String): Boolean {
        if (a.isEmpty() || a.length > MAX_AVATAR_CHARS) return false
        return try {
            val bytes = Base64.decode(a, Base64.NO_WRAP)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            o.outWidth in 1..1024 && o.outHeight in 1..1024
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun ensureSenderProfile(db: MuwanChatDb, uid: String, name: String) {
        if (name.isBlank() || db.cachedUserProfileDao().get(uid) != null) return
        db.cachedUserProfileDao().upsert(
            CachedUserProfileEntity(
                uid = uid, username = "guest_" + uid.takeLast(6), name = name,
                bio = null, city = null, country = null, gender = null, avatar = null, status = "friends"
            )
        )
    }

    private suspend fun onInvite(
        app: Context, db: MuwanChatDb, myId: String, from: String, mid: String,
        createdAt: String, senderName: String, body: JSONObject
    ) {
        val gid = body.optString("g")
        if (!GROUP_ID.matches(gid)) return
        val name = body.optString("nm").trim().take(40).ifBlank { "Group" }

        // keys save (GuestPeers.put har key ko guestIdOf se verify karta hai -- nakli key bina asar ke girti hai)
        val pk = body.optJSONObject("pk")
        if (pk != null) {
            val keys = pk.keys()
            while (keys.hasNext()) {
                val uid = keys.next()
                try {
                    GuestPeers.put(app, uid, GuestCrypto.fromB64(pk.getString(uid)))
                } catch (_: Exception) {
                }
            }
        }

        // asli membership/admin server se: sender ka daava kaafi nahi
        val api = call(app, "GET", "/guest/group/" + gid, null)
        if (api.code == 404 || api.code == 403) return                       // hum is group mein nahi: ignore
        val j = api.json
        if (api.code !in 200..299 || j == null) throw IOException("group verify failed")   // network: baad mein retry
        val admin = j.optString("admin")
        val members = stringList(j.optJSONArray("members"))
        if (admin != from || !members.contains(myId)) return

        val avatarRaw = body.optString("av")
        val avatar: String? = if (validAvatar(avatarRaw)) avatarRaw else null

        save(app, myId, Group(gid, name, admin, members))
        val existing = db.conversationDao().getByRoomId(gid)
        if (existing == null) {
            ChatRepository.addConversationPlaceholder(db, gid, gid, name, avatar, isGroup = true, memberCount = members.size)
            ensureSenderProfile(db, from, senderName)
            ChatRepository.recordMessage(
                db = db, id = "gsys-" + gid, roomId = gid, senderUid = from, receiverUid = gid,
                content = senderName.ifBlank { "Admin" } + " created this group", type = "system",
                createdAt = createdAt, myUid = myId, otherUsername = name
            )
        } else {
            db.conversationDao().upsertOne(
                existing.copy(username = name, avatar = avatar ?: existing.avatar, memberCount = members.size)
            )
        }
    }

    private suspend fun onMessage(
        app: Context, db: MuwanChatDb, myId: String, from: String, mid: String,
        createdAt: String, senderName: String, body: JSONObject
    ) {
        val gid = body.optString("g")
        if (!GROUP_ID.matches(gid)) return
        val group = get(app, myId, gid) ?: return                // invite abhi nahi aaya / hum member nahi
        if (!group.members.contains(from)) return                // sirf member hi bhej sakta hai
        val text = body.optString("x")
        if (text.isBlank() || db.messageDao().getById(mid) != null) return
        ensureSenderProfile(db, from, senderName)
        ChatRepository.recordMessage(
            db = db, id = mid, roomId = gid, senderUid = from, receiverUid = gid,
            content = text, type = "text", createdAt = createdAt, myUid = myId,
            otherUsername = group.name, replyToId = body.optString("r").ifBlank { null }
        )
        if (!GuestPush.visible) {
            GuestPush.showMessage(app, gid, group.name, senderName.ifBlank { "Guest" } + ": " + text)
        }
    }
}
