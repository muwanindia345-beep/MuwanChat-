package com.muwan.muwanchat.data

// GUEST_P1
// Guest session: key banao (ek baar) -> challenge lo -> sign karo -> token lo. Token 7 din ka hai;
// expire hone se ~1 din pehle ya expire ke baad ensure() khud naya session le leta hai (key phone
// mein hai, user ko kuch karna nahi padta). Kabhi throw nahi karta -- Result.Fail deta hai.
import android.content.Context
import android.util.Base64
import com.muwan.muwanchat.network.GuestSessionRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

object GuestSession {

    sealed class Result {
        data class Ok(val guestId: String, val renewed: Boolean) : Result()
        data class Fail(val reason: String) : Result()
    }

    private val lock = Mutex()
    private const val RENEW_BEFORE_MS = 24L * 60 * 60 * 1000

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
        val id = GuestStore.identity(context)

        val existing = GuestStore.token(context)
        if (existing != null && tokenStillGood(existing)) return Result.Ok(id.guestId, false)

        val api = RetrofitClient.guestApi

        val ch = api.challenge()
        val challenge = ch.body()?.challenge
        if (!ch.isSuccessful || challenge.isNullOrEmpty()) {
            return Result.Fail("Challenge failed (" + errorOf(ch) + ")")
        }

        val signature = GuestCrypto.signChallenge(id.seed, challenge)
        val res = api.session(GuestSessionRequest(GuestCrypto.b64(id.publicKey), challenge, signature))
        val body = res.body()
        if (!res.isSuccessful || body == null || body.success != true) {
            return Result.Fail("Session failed (" + errorOf(res) + ")")
        }
        val token = body.token
        if (token.isNullOrEmpty()) return Result.Fail("Session failed (no token)")
        if (body.guestId != id.guestId) return Result.Fail("Guest ID mismatch")

        GuestStore.saveToken(context, token)
        return Result.Ok(id.guestId, true)
    }

    // JWT ke payload se "exp" padhta hai (signature check server karta hai, yahan sirf expiry dekhni hai)
    private fun tokenStillGood(token: String): Boolean = try {
        val payload = token.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8)
        val expMs = JSONObject(json).optLong("exp", 0L) * 1000L
        expMs - System.currentTimeMillis() > RENEW_BEFORE_MS
    } catch (_: Exception) {
        false
    }

    private fun errorOf(res: Response<*>): String {
        val raw = try { res.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { JSONObject(raw ?: "").optString("error") } catch (_: Exception) { "" }
        return if (msg.isNotEmpty()) "HTTP " + res.code() + ": " + msg else "HTTP " + res.code()
    }
}
