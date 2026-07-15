package com.muwan.muwanchat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Pehle ye plain DataStore (Preferences) use karta tha — JWT token +
// MuwanDB secret_key/anon_key sab plaintext XML/proto file mein disk pe
// padhe rehte the (rooted device ya adb backup se seedha extractable).
// Ab EncryptedSharedPreferences use karte hain: values AES256-GCM se
// encrypted, key Android Keystore mein hardware-backed rehti hai (app
// uninstall hote hi key bhi gayab).
//
// Public API (function names/signatures) jaan-bujh kar same rakha hai jaisa
// purana DataStore-based AuthDataStore tha — LoginScreen/SplashScreen/
// ProfileScreen/etc. mein kahin kuch badalne ki zaroorat nahi.
private const val PREFS_NAME = "auth_secure"

private const val USERNAME_KEY   = "username"
private const val EMAIL_KEY      = "email"
private const val TOKEN_KEY      = "token"
private const val UID_KEY        = "uid"
private const val ANON_KEY       = "anon_key"
private const val SECRET_KEY     = "secret_key"
private const val DB_NAME_KEY    = "db_name"
private const val LOGIN_TYPE_KEY = "login_type"
private const val NOTIFICATIONS_ENABLED_KEY = "notifications_enabled"

object AuthDataStore {

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun saveAuth(
        context: Context,
        username: String,
        email: String,
        token: String,
        uid: String = "",
        anonKey: String,
        secretKey: String,
        dbName: String,
        loginType: String
    ) {
        prefs(context).edit()
            .putString(USERNAME_KEY, username)
            .putString(EMAIL_KEY, email)
            .putString(TOKEN_KEY, token)
            .putString(UID_KEY, uid)
            .putString(ANON_KEY, anonKey)
            .putString(SECRET_KEY, secretKey)
            .putString(DB_NAME_KEY, dbName)
            .putString(LOGIN_TYPE_KEY, loginType)
            .apply()
    }

    private fun getString(context: Context, key: String): Flow<String?> =
        flow { emit(prefs(context).getString(key, null)) }

    fun getUsername(context: Context): Flow<String?>  = getString(context, USERNAME_KEY)
    fun getEmail(context: Context): Flow<String?>     = getString(context, EMAIL_KEY)
    fun getToken(context: Context): Flow<String?>     = getString(context, TOKEN_KEY)
    fun getUid(context: Context): Flow<String?>       = getString(context, UID_KEY)
    fun getAnonKey(context: Context): Flow<String?>   = getString(context, ANON_KEY)
    fun getSecretKey(context: Context): Flow<String?> = getString(context, SECRET_KEY)
    fun getDbName(context: Context): Flow<String?>    = getString(context, DB_NAME_KEY)
    fun getLoginType(context: Context): Flow<String?> = getString(context, LOGIN_TYPE_KEY)

    suspend fun setUsername(context: Context, username: String) {
        prefs(context).edit().putString(USERNAME_KEY, username).apply()
    }

    suspend fun setEmail(context: Context, email: String) {
        prefs(context).edit().putString(EMAIL_KEY, email).apply()
    }

    fun getNotificationsEnabled(context: Context): Flow<Boolean> =
        flow { emit(prefs(context).getBoolean(NOTIFICATIONS_ENABLED_KEY, true)) }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled).apply()
    }

    fun isLoggedIn(context: Context): Flow<Boolean> =
        flow { emit(!prefs(context).getString(TOKEN_KEY, null).isNullOrEmpty()) }

    suspend fun clearAuth(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // SharedPreferences reads already synchronous/in-memory-cached hain,
    // isliye ab runBlocking ki bhi zaroorat nahi (purane DataStore version
    // mein thi) — Room DB init aur Coil interceptor dono ke liye safe.
    fun getUidBlocking(context: Context): String =
        prefs(context).getString(UID_KEY, null) ?: ""

    fun getTokenBlocking(context: Context): String =
        prefs(context).getString(TOKEN_KEY, null) ?: ""

    // Purana plaintext DataStore file (agar pehle se installed app upgrade
    // ho raha hai) disk pe padha reh sakta tha token/secret_key ke saath —
    // ek baar app start pe delete kar do taaki plaintext copy na bache.
    // User ko sirf ek dafa dobara login karna padega, uske baad normal.
    fun wipeLegacyPlaintextStore(context: Context) {
        val legacyFile = java.io.File(context.filesDir, "datastore/auth.preferences_pb")
        if (legacyFile.exists()) legacyFile.delete()
    }
}
