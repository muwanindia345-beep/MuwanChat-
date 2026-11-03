package com.muwan.muwanchat.data

// GUEST_P2B1
// Guest invite: ek secret banao -> uska SHA-256 hash server par (POST /guest/invite) -> link/QR mein
// guestId + secret + naam + country. Server secret kabhi nahi dekhta, sirf hash. Redeem karne wala secret
// link se leta hai aur POST /guest/invite/redeem mein bhejta hai (2B-2).
// Wahi invite dobara use hota hai jab tak ~1 ghanta se zyada bacha ho (server pe dobara POST karne se
// "used" counter zero ho jata hai, isliye har baar naya nahi banate).
import android.content.Context
import android.util.Base64
import com.muwan.muwanchat.network.GuestInviteRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.security.MessageDigest
import java.security.SecureRandom

object GuestInvite {

    class Info(val link: String, val expiresAtMs: Long)

    sealed class Result {
        class Ok(val info: Info) : Result()
        class Fail(val reason: String) : Result()
    }

    private const val USES = 10
    private const val TTL_HOURS = 168          // 7 din
    private const val REUSE_MIN_LEFT_MS = 60L * 60 * 1000

    private val lock = Mutex()

    suspend fun ensure(context: Context): Result = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                doEnsure(context.applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Fail(friendlyErrorMessage(e))
            }
        }
    }

    private suspend fun doEnsure(context: Context): Result {
        val session = GuestSession.ensure(context)
        val guestId = when (session) {
            is GuestSession.Result.Ok -> session.guestId
            is GuestSession.Result.Fail -> return Result.Fail(session.reason)
        }
        val token = GuestStore.token(context) ?: return Result.Fail("Guest session missing")

        val now = System.currentTimeMillis()
        val storedSecret = GuestStore.inviteSecret(context)
        val storedExpiry = GuestStore.inviteExpiryMs(context)

        val secret: String
        val expiry: Long
        if (storedSecret != null && storedExpiry - now >= REUSE_MIN_LEFT_MS) {
            secret = storedSecret
            expiry = storedExpiry
        } else {
            val fresh = newSecret()
            val res = RetrofitClient.guestApi.createInvite(
                "Bearer " + token,
                com.muwan.muwanchat.network.GuestInviteRequest(sha256Hex(fresh), USES, TTL_HOURS)
            )
            val body = res.body()
            if (!res.isSuccessful || body == null || body.success != true) {
                return Result.Fail("Invite failed (" + errorOf(res) + ")")
            }
            secret = fresh
            expiry = now + TTL_HOURS * 3600L * 1000L - 60_000L
            GuestStore.saveInvite(context, fresh, expiry)
        }

        val profile = try {
            MuwanChatDb.get(context, guestId).myProfileDao().get()
        } catch (_: Exception) {
            null
        }

        val json = JSONObject()
            .put("v", 1)
            .put("g", guestId)
            .put("s", secret)
            .put("n", (profile?.name ?: "").take(40))
            .put("c", (profile?.country ?: "").take(40))
            .toString()
        val payload = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val base = com.muwan.muwanchat.BuildConfig.BACKEND_URL.trimEnd('/')
        return Result.Ok(Info(base + "/g#" + payload, expiry))
    }

    private fun newSecret(): String {
        val b = ByteArray(24)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // Backend: crypto.createHash('sha256').update(secret) -- secret string ke UTF-8 bytes ka hash
    private fun sha256Hex(s: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (x in h) sb.append("%02x".format(x.toInt() and 0xff))
        return sb.toString()
    }

    private fun errorOf(res: Response<*>): String {
        val raw = try { res.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { JSONObject(raw ?: "").optString("error") } catch (_: Exception) { "" }
        return if (msg.isNotEmpty()) "HTTP " + res.code() + ": " + msg else "HTTP " + res.code()
    }
}
