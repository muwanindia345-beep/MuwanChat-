package com.muwan.muwanchat.data

// GUEST_P2B2
// Guest invite ka "inbox": scan ya deep link se aaya raw invite yahan rakha jaata hai (memory mein), Chats home
// ka GuestInviteHost use confirm popup dikhata hai. Parse + redeem (contact add) bhi yahin.
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.muwan.muwanchat.network.GuestRedeemRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

object GuestInviteInbox {

    class Invite(val guestId: String, val secret: String, val name: String, val country: String, val pub: ByteArray)  // GUEST_P2C

    val pending = MutableStateFlow<String?>(null)

    fun offer(raw: String) {
        pending.value = raw.take(6000)
    }

    fun clear() {
        pending.value = null
    }

    private val GID_RE = Regex("^g_[0-9a-f]{32}$")
    private val PAYLOAD_RE = Regex("^[A-Za-z0-9_-]{16,4000}$")

    // Accepts: https://<backend>/g#<payload> | muwan://guest?p=<payload> | sirf <payload>
    private fun extractPayload(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 6000) return null
        val fragment = t.substringAfter('#', "")
        val candidate: String? = when {
            t.startsWith("muwan://", ignoreCase = true) -> Uri.parse(t).getQueryParameter("p")
            fragment.isNotEmpty() -> fragment
            else -> t
        }
        return candidate?.takeIf { PAYLOAD_RE.matches(it) }
    }

    // GUEST_P2D: lamba (payload) ya chhota (/g/CODE#SECRET, muwan://invite?c=&s=) -- dono chalte hain.
    class Resolved(val invite: Invite?, val error: String?)

    private val SHORT_RE = Regex("/g/([A-Za-z0-9]{10})/?#([A-Za-z0-9_-]{16,200})")
    private val CODE_RE = Regex("^[A-Za-z0-9]{10}$")
    private val SECRET_RE = Regex("^[A-Za-z0-9_-]{16,200}$")

    private fun parseShort(raw: String): Pair<String, String>? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 6000) return null
        if (t.startsWith("muwan://invite", ignoreCase = true)) {
            val u = Uri.parse(t)
            val c = u.getQueryParameter("c") ?: return null
            val s = u.getQueryParameter("s") ?: return null
            return if (CODE_RE.matches(c) && SECRET_RE.matches(s)) Pair(c, s) else null
        }
        val m = SHORT_RE.find(t) ?: return null
        return Pair(m.groupValues[1], m.groupValues[2])
    }

    // Chhote link ke liye server se public data laata hai; key ko guestId se verify karta hai (nakli key nahi chalegi).
    suspend fun resolve(raw: String): Resolved = withContext(Dispatchers.IO) {
        val ref = parseShort(raw)
        if (ref == null) return@withContext Resolved(parse(raw), null)
        try {
            val res = RetrofitClient.guestApi.getLink(ref.first)
            val b = res.body()
            if (res.code() == 404) {
                return@withContext Resolved(null, "This invite link is invalid or has expired. Ask your friend for a new one.")
            }
            if (!res.isSuccessful || b == null) {
                return@withContext Resolved(null, "Couldn't check this invite right now. Check your internet and try again.")
            }
            val g = b.g ?: ""
            val pub = try { GuestCrypto.fromB64(b.k ?: "") } catch (_: Exception) { ByteArray(0) }
            if (!GID_RE.matches(g) || pub.size != 32 || GuestCrypto.guestIdOf(pub) != g) {
                return@withContext Resolved(null, "This invite link is invalid.")
            }
            Resolved(Invite(g, ref.second, (b.n ?: "").trim().take(40), (b.c ?: "").trim().take(40), pub), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resolved(null, friendlyErrorMessage(e))
        }
    }

    fun parse(raw: String): Invite? {
        val payload = extractPayload(raw) ?: return null
        return try {
            val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8))
            val guestId = json.optString("g")
            val secret = json.optString("s")
            val pub = try { GuestCrypto.fromB64(json.optString("k")) } catch (_: Exception) { ByteArray(0) }  // GUEST_P2C
            if (json.optInt("v", 0) != 1) null
            else if (!GID_RE.matches(guestId)) null
            else if (secret.length < 16 || secret.length > 200) null
            else if (pub.size != 32 || GuestCrypto.guestIdOf(pub) != guestId) null  // key id se match honi chahiye
            else Invite(
                guestId,
                secret,
                json.optString("n").trim().take(40),
                json.optString("c").trim().take(40),
                pub
            )
        } catch (_: Exception) {
            null
        }
    }

    // Returns (ok, message). Secret sirf hamare apne backend ko jaata hai (host link se nahi liya jaata).
    suspend fun accept(context: Context, inv: Invite): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext
            val session = GuestSession.ensure(app)
            val myId = when (session) {
                is GuestSession.Result.Ok -> session.guestId
                is GuestSession.Result.Fail -> return@withContext Pair(false, session.reason)
            }
            if (inv.guestId == myId) return@withContext Pair(false, "This is your own invite.")
            val token = GuestStore.token(app) ?: return@withContext Pair(false, "Guest session missing.")

            val db = MuwanChatDb.get(app, myId)
            val shown = inv.name.ifBlank { "Guest " + inv.guestId.takeLast(4) }
            GuestPeers.put(app, inv.guestId, inv.pub)  // GUEST_P2C
            if (db.conversationDao().getByUid(inv.guestId) != null) {
                return@withContext Pair(true, shown + " is already in your chats.")
            }

            val res = RetrofitClient.guestApi.redeemInvite(
                "Bearer " + token,
                GuestRedeemRequest(inv.guestId, inv.secret)
            )
            if (!res.isSuccessful) {
                return@withContext if (res.code() == 404) {
                    Pair(false, "This invite is invalid, expired or already used up. Ask your friend for a new one.")
                } else {
                    Pair(false, "Couldn't add contact (" + errorOf(res) + ")")
                }
            }

            ChatRepository.addConversationPlaceholder(
                db = db,
                roomId = roomIdFor(myId, inv.guestId),
                uid = inv.guestId,
                username = shown,
                avatar = null
            )
            GuestChat.start(app)  // GUEST_P2C
            GuestChat.sendHelloAsync(app, inv.guestId)  // dusre ke phone mein bhi contact aa jaye
            Pair(true, shown + " added to your chats.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Pair(false, friendlyErrorMessage(e))
        }
    }

    // Dono taraf same roomId (sorted pair ka hash) -- chat (Phase 2C) isi se milegi.
    fun roomIdFor(a: String, b: String): String {
        val pair = if (a < b) a + ":" + b else b + ":" + a
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(pair.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder("gdm_")
        for (x in h.take(12)) sb.append("%02x".format(x.toInt() and 0xff))
        return sb.toString()
    }

    private fun errorOf(res: Response<*>): String {
        val raw = try { res.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { JSONObject(raw ?: "").optString("error") } catch (_: Exception) { "" }
        return if (msg.isNotEmpty()) "HTTP " + res.code() + ": " + msg else "HTTP " + res.code()
    }
}
