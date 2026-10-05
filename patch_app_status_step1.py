#!/usr/bin/env python3
"""
STATUS STEP 1 (app) -- data layer
  * Room: nayi `status_cache` table (offline-first, Broadcast tab jaisa key/json pattern)
  * DB version 26 -> 27 + MIGRATION_26_27 (purana data safe, reset nahi hoga)
  * Retrofit: StatusApi (feed / create / view / delete / like / unlike) + RetrofitClient.statusApi

Chalane ka tareeka (MuwanChat--main repo root se, jahan `app/` folder hai):
    python3 patch_app_status_step1.py

Dobara chalane par koi nuksan nahi -- jo file pehle se patched hai wo skip ho jaati hai.
"""
import os
import sys

BASE = os.path.join("app", "src", "main", "java", "com", "muwan", "muwanchat")
DATA = os.path.join(BASE, "data")
NET = os.path.join(BASE, "network")


def read(path):
    # newline="" => file ki original line endings (LF/CRLF) bilkul waisi hi rehti hain
    with open(path, encoding="utf-8", newline="") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def die(msg):
    print("[ERROR] " + msg)
    sys.exit(1)


def eol_of(text):
    return "\r\n" if "\r\n" in text else "\n"


def create_file(path, content):
    if os.path.exists(path):
        print("[skip] pehle se hai   : " + path)
        return
    write(path, content)
    print("[new ] bana diya      : " + path)


def patch_file(path, marker, edits):
    """edits = [(old_single_line, new_text), ...]; har `old` file mein exactly 1 baar milna chahiye."""
    if not os.path.isfile(path):
        die("file nahi mili: " + path + "\nScript repo root se chalao (jahan app/ folder hai).")
    text = read(path)
    if marker in text:
        print("[skip] pehle se patched: " + path)
        return
    nl = eol_of(text)
    for old, new in edits:
        n = text.count(old)
        if n != 1:
            die("%s mein '%s' %d baar mila (1 hona chahiye). File badli hui lagti hai, patch ruk gaya." % (path, old, n))
        text = text.replace(old, new.replace("\n", nl))
    write(path, text)
    print("[edit] patch ho gaya  : " + path)


# ---------------------------------------------------------------------------
# 1) Room: entity + dao
# ---------------------------------------------------------------------------
ENTITY = '''package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Status tab ka offline-first cache. ChannelsCacheEntity jaisa hi pattern:
// key -> JSON string, DB already per-uid hai. Keys:
//   "feed"          -> last server feed (mine + friends)
//   "pending_views" -> offline me dekhe gaye status ids (online aate hi sync)
//   "pending_likes" -> offline me like/unlike (online aate hi sync)
// JSON isliye ki status fields badalne par (likes, link title...) baar-baar
// migration na likhni pade.
@Entity(tableName = "status_cache")
data class StatusCacheEntity(
    @PrimaryKey val key: String,
    val json: String
)
'''

DAO = '''package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StatusCacheDao {
    @Query("SELECT * FROM status_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): StatusCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StatusCacheEntity)

    @Query("DELETE FROM status_cache WHERE `key` = :key")
    suspend fun delete(key: String)
}
'''

# ---------------------------------------------------------------------------
# 2) Retrofit: StatusApi
# ---------------------------------------------------------------------------
STATUS_API = '''package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

// Backend: routes/status.js. Saari fields ka default hai taaki Gson kisi bhi
// missing field par crash na kare (like_count / liked / link_title backend
// patch ke baad aayenge -- tab tak default 0 / false / null rahenge).
data class StatusItemDto(
    val id: String = "",
    val type: String = "text",          // text | image | video | document | link
    val text: String = "",
    val bg_color: String? = null,
    val media_url: String? = null,
    val thumbnail_url: String? = null,
    val file_name: String? = null,
    val mime_type: String? = null,
    val link_url: String? = null,
    val link_title: String? = null,
    val created_at: String = "",        // ISO-8601, UTC
    val seen: Boolean = false,
    val view_count: Int = 0,
    val like_count: Int = 0,
    val liked: Boolean = false
)

data class StatusUserDto(
    val uid: String = "",
    val username: String = "Unknown",
    val avatar: String? = null,         // base64 (AvatarView yahi leta hai)
    val statuses: List<StatusItemDto> = emptyList(),
    val all_seen: Boolean = false,
    val latest_at: String = ""
)

data class StatusFeedResponse(
    val mine: List<StatusItemDto> = emptyList(),
    val friends: List<StatusUserDto> = emptyList()
)

data class CreateStatusBody(
    val type: String,
    val text: String? = null,
    val bg_color: String? = null,
    val media_url: String? = null,
    val thumbnail_url: String? = null,
    val file_name: String? = null,
    val mime_type: String? = null,
    val link_url: String? = null
)

data class CreateStatusResponse(
    val success: Boolean = false,
    val status: StatusItemDto? = null,
    val error: String? = null
)

data class StatusSimpleResponse(
    val success: Boolean = false,
    val error: String? = null
)

interface StatusApi {
    @GET("status/feed")
    suspend fun getFeed(
        @Header("Authorization") token: String
    ): Response<StatusFeedResponse>

    @POST("status")
    suspend fun create(
        @Header("Authorization") token: String,
        @Body body: CreateStatusBody
    ): Response<CreateStatusResponse>

    @POST("status/{id}/view")
    suspend fun markViewed(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>

    @DELETE("status/{id}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>

    // Likes: backend endpoints abhi nahi hain (backend patch Step B). Tab tak 404.
    @POST("status/{id}/like")
    suspend fun like(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>

    @DELETE("status/{id}/like")
    suspend fun unlike(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<StatusSimpleResponse>
}
'''

RETROFIT_BLOCK = '''
    val statusApi: StatusApi by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StatusApi::class.java)
    }
'''

MIGRATION_BLOCK = '''    // Status feature offline-first cache -- sirf ek NAYA table, koi existing
    // table/column touch nahi hota, isliye chats/messages/wallpaper/theme safe.
    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `status_cache` (" +
                    "`key` TEXT NOT NULL, " +
                    "`json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`key`))"
            )
        }
    }

'''


def main():
    if not os.path.isdir(BASE):
        die("%s folder nahi mila. Script ko MuwanChat--main repo root se chalao." % BASE)

    # 1) naye files
    create_file(os.path.join(DATA, "StatusCacheEntity.kt"), ENTITY)
    create_file(os.path.join(DATA, "StatusCacheDao.kt"), DAO)
    create_file(os.path.join(NET, "StatusApi.kt"), STATUS_API)

    # 2) MuwanChatDb.kt: entity + version 27 + dao
    patch_file(
        os.path.join(DATA, "MuwanChatDb.kt"),
        "StatusCacheEntity",
        [
            ("ChannelsCacheEntity::class]", "ChannelsCacheEntity::class, StatusCacheEntity::class]"),
            ("version = 26,", "version = 27,"),
            (
                "abstract fun channelsCacheDao(): ChannelsCacheDao",
                "abstract fun channelsCacheDao(): ChannelsCacheDao\n    abstract fun statusCacheDao(): StatusCacheDao",
            ),
        ],
    )

    # 3) DbMigrations.kt: MIGRATION_26_27 + ALL
    patch_file(
        os.path.join(DATA, "DbMigrations.kt"),
        "MIGRATION_26_27",
        [
            (
                "    val ALL: Array<Migration> = arrayOf(MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26)",
                MIGRATION_BLOCK
                + "    val ALL: Array<Migration> = arrayOf(MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)",
            ),
        ],
    )

    # 4) RetrofitClient.kt: statusApi (object ke aakhri `}` se pehle)
    rc = os.path.join(NET, "RetrofitClient.kt")
    if not os.path.isfile(rc):
        die("file nahi mili: " + rc)
    text = read(rc)
    if "statusApi" in text:
        print("[skip] pehle se patched: " + rc)
    else:
        nl = eol_of(text)
        idx = text.rstrip().rfind("}")
        if idx == -1:
            die("RetrofitClient.kt mein closing brace nahi mila.")
        text = text[:idx].rstrip() + nl + RETROFIT_BLOCK.replace("\n", nl) + text[idx:]
        write(rc, text)
        print("[edit] patch ho gaya  : " + rc)

    print("\nStep 1 done. Ab Android Studio mein Sync + Build karo (DB v27 schema JSON app/schemas mein ban jayega).")


if __name__ == "__main__":
    main()
