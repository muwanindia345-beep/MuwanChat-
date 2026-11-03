import sys, os

# Usage (Termux, repo root MuwanChat se):
#   python guest_phase1_patch.py .
#
# GUEST PHASE 1 -- app ko guest backend se jodta hai (key + session handshake).
# UI KUCH NAHI BADLA: Profile (guest) ka Save abhi bhi "Coming Soon" dikhata hai.
# Save dabane par peeche silent handshake chalta hai:
#     GET /guest/challenge -> Ed25519 se sign -> POST /guest/session -> guest token
# Guest ka data AuthDataStore mein NAHI jaata (alag "guest_secure" store), isliye Splash/Login
# ka flow aur auth users bilkul untouched hain.
#
# Naye files : data/GuestCrypto.kt, data/GuestStore.kt, data/GuestSession.kt, network/GuestApi.kt
# Patch      : app/build.gradle.kts, network/RetrofitClient.kt, screens/ProfileScreen.kt,
#              app/proguard-rules.pro
# Dobara chalane par "already patched" bolta hai. Koi anchor na mile to KUCH NAHI likhta.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P1"

# ───────────────────────── naye Kotlin files ─────────────────────────

CRYPTO_KT = r'''package com.muwan.muwanchat.data

// GUEST_P1
// Guest identity: Ed25519 keypair (phone par bana, phone se bahar nahi jaata).
// guestId = "g_" + sha256(publicKey).hex[0..32]  -- backend (lib/guestAuth.js) ke saath same formula.
// Android Keystore Ed25519 (API 33 se pehle) support nahi karta aur key bahar nikalne nahi deta
// (Drive backup ke liye key export chahiye), isliye BouncyCastle lightweight API use hui hai.
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

object GuestCrypto {

    // Backend yahi prefix lagake verify karta hai: Ed25519( UTF8("muwan-guest-v1:" + challenge) )
    const val SIG_DOMAIN = "muwan-guest-v1:"

    class Identity(val seed: ByteArray, val publicKey: ByteArray) {
        val guestId: String get() = guestIdOf(publicKey)
    }

    fun newIdentity(): Identity {
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)
        return fromSeed(seed)
    }

    fun fromSeed(seed: ByteArray): Identity {
        require(seed.size == 32) { "seed must be 32 bytes" }
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        return Identity(seed.copyOf(), pub)
    }

    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun signChallenge(seed: ByteArray, challenge: String): String =
        b64(sign(seed, (SIG_DOMAIN + challenge).toByteArray(Charsets.UTF_8)))

    fun guestIdOf(publicKey: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey)
        val hex = StringBuilder()
        for (b in hash) hex.append("%02x".format(b.toInt() and 0xff))
        return "g_" + hex.substring(0, 32)
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun fromB64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
'''

STORE_KT = r'''package com.muwan.muwanchat.data

// GUEST_P1
// Guest ki private key aur token ka secure store (AuthDataStore se ALAG file: "guest_secure").
// AuthDataStore.clearAuth() / logout is store ko kabhi nahi chhuta.
//
// IMPORTANT: AuthDataStore ki tarah yahan "prefs kharab -> delete karke dobara banao" NAHI kiya.
// Wahan sirf login dobara karna padta hai, yahan prefs delete = guest identity hamesha ke liye gayab.
// Isliye error aaye to upar wale ko jaane do, chup-chaap identity mat mitao.
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object GuestStore {

    private const val PREFS_NAME = "guest_secure"
    private const val SEED_KEY = "seed"
    private const val PUB_KEY = "pub"
    private const val GID_KEY = "gid"
    private const val TOKEN_KEY = "token"

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }
    }

    private fun build(appCtx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appCtx,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Pehli baar naya keypair banata hai aur save karta hai (commit = synchronous, crash par bhi na khoye).
    @Synchronized
    fun identity(context: Context): GuestCrypto.Identity {
        val p = prefs(context)
        val stored = p.getString(SEED_KEY, null)
        if (!stored.isNullOrEmpty()) {
            return GuestCrypto.fromSeed(GuestCrypto.fromB64(stored))
        }
        val id = GuestCrypto.newIdentity()
        p.edit()
            .putString(SEED_KEY, GuestCrypto.b64(id.seed))
            .putString(PUB_KEY, GuestCrypto.b64(id.publicKey))
            .putString(GID_KEY, id.guestId)
            .commit()
        return id
    }

    fun hasIdentity(context: Context): Boolean =
        !prefs(context).getString(SEED_KEY, null).isNullOrEmpty()

    fun guestIdOrNull(context: Context): String? = prefs(context).getString(GID_KEY, null)

    fun token(context: Context): String? =
        prefs(context).getString(TOKEN_KEY, null)?.takeIf { it.isNotEmpty() }

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(TOKEN_KEY, token).apply()
    }

    fun clearToken(context: Context) {
        prefs(context).edit().remove(TOKEN_KEY).apply()
    }
}
'''

API_KT = r'''package com.muwan.muwanchat.network

// GUEST_P1
// Guest backend (routes/guest.js): challenge -> sign -> session. Server guest ka koi record save nahi karta.
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class GuestChallengeResponse(
    val challenge: String?,
    val expiresInSec: Int?,
    val error: String?
)

// publicKey = Ed25519 raw 32 bytes (base64), signature = Ed25519(UTF8("muwan-guest-v1:" + challenge)) (base64)
data class GuestSessionRequest(
    val publicKey: String,
    val challenge: String,
    val signature: String
)

data class GuestSessionResponse(
    val success: Boolean?,
    val guestId: String?,
    val token: String?,
    val ttlDays: Int?,
    val error: String?
)

interface GuestApi {
    @GET("guest/challenge")
    suspend fun challenge(): Response<GuestChallengeResponse>

    @POST("guest/session")
    suspend fun session(@Body request: GuestSessionRequest): Response<GuestSessionResponse>
}
'''

SESSION_KT = r'''package com.muwan.muwanchat.data

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
'''

PROGUARD_BLOCK = r'''
# GUEST_P1 -- BouncyCastle (guest Ed25519). Sirf lightweight crypto API use hoti hai (reflection nahi),
# par optional references ke warnings R8 ko fail na karein isliye dontwarn.
-dontwarn org.bouncycastle.**
'''

# ───────────────────────── helpers ─────────────────────────

def rd(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: anchor %d baar mila (1 hona chahiye). Kuch nahi likha." % (name, n))
        sys.exit(1)
    return s.replace(old, new)

# ───────────────────────── plan (pehle sab check, phir likho) ─────────────────────────

writes = {}   # path -> new content
skipped = []

def plan_new_file(rel, content):
    p = os.path.join(BASE, rel)
    if os.path.exists(p):
        if MARK in rd(p):
            skipped.append(rel)
            return
        print("FAIL: %s pehle se hai par GUEST_P1 wala nahi. Kuch nahi likha." % rel)
        sys.exit(1)
    writes[p] = content

def plan_patch(path, label, fn):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label)
        return
    writes[path] = fn(s)

plan_new_file("data/GuestCrypto.kt", CRYPTO_KT)
plan_new_file("data/GuestStore.kt", STORE_KT)
plan_new_file("data/GuestSession.kt", SESSION_KT)
plan_new_file("network/GuestApi.kt", API_KT)

# --- build.gradle.kts: BouncyCastle dependency + packaging exclude
def patch_gradle(s):
    s = rep(s,
        '    implementation("io.github.webrtc-sdk:android:144.7559.09")\n',
        '    implementation("io.github.webrtc-sdk:android:144.7559.09")\n'
        '    // GUEST_P1 -- Ed25519 (guest identity). Lightweight API, Android Keystore Ed25519 API 33+ par hi hai.\n'
        '    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")\n',
        "gradle dependency")
    s = rep(s,
        '            excludes += "/META-INF/{AL2.0,LGPL2.1}"\n',
        '            excludes += "/META-INF/{AL2.0,LGPL2.1}"\n'
        '            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"  // GUEST_P1 (BouncyCastle)\n',
        "gradle packaging")
    return s
plan_patch(os.path.join(ROOT, "app/build.gradle.kts"), "build.gradle.kts", patch_gradle)

# --- RetrofitClient.kt: guestApi
def patch_retrofit(s):
    return rep(s,
        '            .create(StatusApi::class.java)\n    }\n',
        '            .create(StatusApi::class.java)\n    }\n'
        '\n'
        '    // GUEST_P1 -- guest backend (/guest/*). Auth wale APIs se alag.\n'
        '    val guestApi: GuestApi by lazy {\n'
        '        Retrofit.Builder()\n'
        '            .baseUrl(CHAT_BACKEND_URL)\n'
        '            .client(okHttpClient)\n'
        '            .addConverterFactory(GsonConverterFactory.create())\n'
        '            .build()\n'
        '            .create(GuestApi::class.java)\n'
        '    }\n',
        "RetrofitClient")
plan_patch(os.path.join(BASE, "network/RetrofitClient.kt"), "RetrofitClient.kt", patch_retrofit)

# --- ProfileScreen.kt: guest Save -> silent handshake, UI Coming Soon hi rehta hai
def patch_profile(s):
    s = rep(s,
        'import kotlinx.coroutines.launch\n',
        'import kotlinx.coroutines.launch\n'
        '\n'
        '// GUEST_P1: true = BETA build ke Coming Soon popup mein handshake ka result bhi dikhta hai (test ke liye).\n'
        '// Production build mein ye kabhi nahi dikhta. Test ho jaye to false kar do.\n'
        'private const val GUEST_P1_SHOW_BETA_RESULT = true\n',
        "profile const")
    s = rep(s,
        '    var showGuestComingSoon by remember { mutableStateOf(false) }  // GUEST_UI\n',
        '    var showGuestComingSoon by remember { mutableStateOf(false) }  // GUEST_UI\n'
        '    var guestBetaResult by remember { mutableStateOf("") }  // GUEST_P1\n',
        "profile state")
    s = rep(s,
        '        if (isGuest) { showGuestComingSoon = true; return }  // GUEST_UI\n',
        '        if (isGuest) {  // GUEST_P1\n'
        '            // Phase 1: UI Coming Soon hi rehta hai; peeche silent backend handshake (key + session).\n'
        '            showGuestComingSoon = true\n'
        '            scope.launch {\n'
        '                val r = com.muwan.muwanchat.data.GuestSession.ensure(context)\n'
        '                val isBeta = com.muwan.muwanchat.BuildConfig.APPLICATION_ID.endsWith(".beta")\n'
        '                if (isBeta) {\n'
        '                    android.util.Log.i("GuestSession", r.toString())\n'
        '                    if (GUEST_P1_SHOW_BETA_RESULT) {\n'
        '                        guestBetaResult = when (r) {\n'
        '                            is com.muwan.muwanchat.data.GuestSession.Result.Ok ->\n'
        '                                "\\n\\n[beta] backend connected\\n" + r.guestId\n'
        '                            is com.muwan.muwanchat.data.GuestSession.Result.Fail ->\n'
        '                                "\\n\\n[beta] backend FAILED\\n" + r.reason\n'
        '                        }\n'
        '                    }\n'
        '                }\n'
        '            }\n'
        '            return\n'
        '        }\n',
        "profile save")
    s = rep(s,
        '            ComingSoonDialog(feature = "👤 Guest Mode", onDismiss = { showGuestComingSoon = false })\n',
        '            ComingSoonDialog(feature = "👤 Guest Mode" + guestBetaResult, onDismiss = { showGuestComingSoon = false })  // GUEST_P1\n',
        "profile dialog")
    return s
plan_patch(os.path.join(BASE, "screens/ProfileScreen.kt"), "ProfileScreen.kt", patch_profile)

# --- proguard-rules.pro
PRO = os.path.join(ROOT, "app/proguard-rules.pro")
def patch_pro(s):
    if not s.endswith("\n"):
        s += "\n"
    return s + PROGUARD_BLOCK
plan_patch(PRO, "proguard-rules.pro", patch_pro)

# ───────────────────────── likho ─────────────────────────

if not writes:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

for path, content in writes.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK  " + os.path.relpath(path, ROOT))
for x in skipped:
    print("skip (pehle se hai) " + x)
print("\nGuest Phase 1 patch lag gaya. Ab: git add -A && git commit && git push (beta build banega).")
