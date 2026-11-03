package com.muwan.muwanchat.data

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

    // ───── GUEST_P2A: "guest mode active" ─────
    // Plain prefs (Keystore nahi): guestId public key ka hash hai (secret nahi) aur ye flag
    // AuthDataStore.getUidBlocking() jaise hot paths se padha jaata hai.
    private const val STATE_PREFS = "guest_state"

    private fun state(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    fun markOnboarded(context: Context, guestId: String) {
        state(context).edit().putBoolean("onboarded", true).putString("gid", guestId).commit()
    }

    // Guest active = onboarded AUR koi auth login nahi. Auth login ho to auth hi jeetta hai.
    // Active nahi to "" (isi se AuthDataStore.getUidBlocking() purani tarah kaam karta hai).
    fun activeGuestId(context: Context): String {
        val s = state(context)
        if (!s.getBoolean("onboarded", false)) return ""
        if (AuthDataStore.getTokenBlocking(context).isNotEmpty()) return ""
        return s.getString("gid", null) ?: ""
    }

    fun isGuestActive(context: Context): Boolean = activeGuestId(context).isNotEmpty()

    // ───── GUEST_P2B1: apna invite (secret + expiry). Secret Keystore-encrypted prefs mein. ─────
    fun inviteSecret(context: Context): String? =
        prefs(context).getString("invite_secret", null)?.takeIf { it.isNotEmpty() }

    fun inviteExpiryMs(context: Context): Long = prefs(context).getLong("invite_exp", 0L)

    fun saveInvite(context: Context, secret: String, expiryMs: Long) {
        prefs(context).edit().putString("invite_secret", secret).putLong("invite_exp", expiryMs).apply()
    }
}
