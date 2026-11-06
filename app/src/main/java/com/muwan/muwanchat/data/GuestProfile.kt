package com.muwan.muwanchat.data

// GUEST_PROF
// Guest profile sync: apna profile har contact ko E2EE "prof" envelope se, aur contact ka profile receive karke
// ConversationEntity (naam + photo) aur CachedUserProfileEntity (description/location/gender) mein.
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object GuestProfile {

    private const val MAX_SEND_AVATAR_CHARS = 30_000      // ~22KB jpeg -> envelope 64KB limit ke andar
    private const val MAX_RECV_AVATAR_CHARS = 60_000
    private const val MAX_AVATAR_DIM = 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("guest_prof", Context.MODE_PRIVATE)

    // Profile badla -> naya version; jin contacts ko is version ka profile nahi gaya unhe jayega
    fun markChanged(context: Context) {
        prefs(context).edit().putLong("ver", System.currentTimeMillis()).apply()
    }

    // 512px photo -> ~192px jpeg (envelope mein fit). Photo na ho to "".
    fun smallAvatar(b64: String?): String {
        if (b64.isNullOrBlank()) return ""
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
            val src = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return ""
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val target = minOf(192, side)
            val scaled = Bitmap.createScaledBitmap(square, target, target, true)
            var q = 75
            var out = ""
            while (q >= 30) {
                val bos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, bos)
                out = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                if (out.length <= MAX_SEND_AVATAR_CHARS) return out
                q -= 15
            }
            if (out.length <= MAX_SEND_AVATAR_CHARS) out else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun syncSoon(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                syncAll(app)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // Har contact jisko current version nahi gaya, use apna profile bhejo; success par yaad rakho.
    private suspend fun syncAll(app: Context) {
        syncLock.withLock {
            val myId = GuestStore.activeGuestId(app)
            if (myId.isEmpty()) return
            val db = MuwanChatDb.get(app, myId)
            val me = db.myProfileDao().get() ?: return
            val p = prefs(app)
            val ver = p.getLong("ver", 1L)
            val pending = db.conversationDao().getAll()
                .map { it.uid }
                .filter { it.startsWith("g_") && GuestPeers.get(app, it) != null }
                .distinct()
                .filter { p.getLong("sent_" + it, -1L) != ver }
            if (pending.isEmpty()) return
            val avatar = smallAvatar(me.avatar)
            for (uid in pending) {
                val body = JSONObject()
                    .put("n", (me.name ?: "").take(40))
                    .put("b", (me.bio ?: "").take(300))
                    .put("ci", (me.city ?: "").take(40))
                    .put("co", (me.country ?: "").take(40))
                    .put("g", (me.gender ?: "").take(40))
                    .put("a", avatar)
                    .put("ts", System.currentTimeMillis())
                if (GuestChat.sendProfile(app, uid, body)) {
                    p.edit().putLong("sent_" + uid, ver).apply()
                }
            }
        }
    }

    private fun validAvatar(a: String): Boolean {
        if (a.isEmpty() || a.length > MAX_RECV_AVATAR_CHARS) return false
        return try {
            val bytes = Base64.decode(a, Base64.NO_WRAP)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            o.outWidth in 1..MAX_AVATAR_DIM && o.outHeight in 1..MAX_AVATAR_DIM
        } catch (_: Exception) {
            false
        }
    }

    // Contact ka profile aaya (signature + guestId verify ho chuka): chat list/header + profile screen ke liye save.
    suspend fun applyIncoming(db: MuwanChatDb, roomId: String, from: String, body: JSONObject) {
        fun t(k: String, max: Int): String = body.optString(k).trim().take(max)
        val name = t("n", 40)
        val bio = t("b", 300)
        val city = t("ci", 40)
        val country = t("co", 40)
        val gender = t("g", 40)
        val a = body.optString("a")
        val avatar: String? = if (validAvatar(a)) a else null

        val conv = db.conversationDao().getByRoomId(roomId)
        if (conv != null) {
            db.conversationDao().upsertOne(
                conv.copy(username = name.ifBlank { conv.username }, avatar = avatar)
            )
        }
        db.cachedUserProfileDao().upsert(
            CachedUserProfileEntity(
                uid = from,
                username = "guest_" + from.takeLast(6),
                name = name.ifBlank { null },
                bio = bio.ifBlank { null },
                city = city.ifBlank { null },
                country = country.ifBlank { null },
                gender = gender.ifBlank { null },
                avatar = avatar,
                status = "friends"
            )
        )
    }
}
