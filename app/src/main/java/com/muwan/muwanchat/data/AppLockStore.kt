package com.muwan.muwanchat.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pattern / PIN lock storage + brute-force protection.
 *
 * - The pattern / PIN itself is NEVER stored: only a salted PBKDF2 hash.
 * - Only ONE method is active at a time; saving a new one replaces the old.
 * - Lives in its own encrypted prefs file ("app_lock_secure"), separate from
 *   AuthDataStore, so lock state and login state can be reset independently.
 * - Failed attempts / lockout survive app restarts and process death.
 *
 * Policy (tweak the constants below):
 *   5 wrong attempts  -> blocked 5 minutes
 *   5 more wrong      -> blocked 10 minutes
 *   5 more wrong      -> account is logged out (local chat data is kept)
 */
object AppLockStore {

    const val MIN_DOTS = 4
    const val MIN_PIN = 4
    const val MAX_PIN = 6
    const val MAX_ATTEMPTS = 5

    // Lockout length after failure round 1 and round 2. Round 3 => logout.
    private val LOCKOUT_MS = longArrayOf(5 * 60_000L, 10 * 60_000L)

    private const val PREFS_NAME = "app_lock_secure"
    private const val KEY_METHOD = "method"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_PIN_LEN = "pin_len"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_ROUNDS = "failed_rounds"
    private const val KEY_LOCK_DURATION = "lock_duration"
    private const val KEY_LOCK_START_ELAPSED = "lock_start_elapsed"
    private const val KEY_LOCK_UNTIL_WALL = "lock_until_wall"

    const val METHOD_PATTERN = "pattern"
    const val METHOD_PIN = "pin"
    private const val PBKDF2_ITERATIONS = 10_000

    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Wrong(val attemptsLeft: Int) : VerifyResult()
        data class Locked(val remainingMs: Long) : VerifyResult()
        object LogoutRequired : VerifyResult()
    }

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val appCtx = context.applicationContext
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appCtx,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.also { cached = it }
        }
    }

    // ── State ───────────────────────────────────────────────────────────

    /** METHOD_PATTERN, METHOD_PIN, or null when no lock is set. */
    fun currentMethod(context: Context): String? {
        val p = prefs(context)
        if (p.getString(KEY_HASH, null).isNullOrEmpty()) return null
        return p.getString(KEY_METHOD, null)
    }

    fun isPatternEnabled(context: Context): Boolean = currentMethod(context) == METHOD_PATTERN

    fun isPinEnabled(context: Context): Boolean = currentMethod(context) == METHOD_PIN

    /** True if any lock method is set. */
    fun isLockEnabled(context: Context): Boolean = currentMethod(context) != null

    /** Length of the saved PIN (so the unlock screen can auto-submit). */
    fun pinLength(context: Context): Int =
        prefs(context).getInt(KEY_PIN_LEN, MIN_PIN).coerceIn(MIN_PIN, MAX_PIN)

    /** Number of fully-failed rounds so far (used to show the logout warning). */
    fun failedRounds(context: Context): Int = prefs(context).getInt(KEY_ROUNDS, 0)

    // ── Set / remove ────────────────────────────────────────────────────

    @Synchronized
    fun savePattern(context: Context, pattern: List<Int>) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .clear()
            .putString(KEY_METHOD, METHOD_PATTERN)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(pattern.joinToString("-"), salt))
            .apply()
    }

    /** Saves a 4-6 digit PIN (replaces any pattern/PIN lock and resets attempts). */
    @Synchronized
    fun savePin(context: Context, pin: String) {
        require(pin.length in MIN_PIN..MAX_PIN && pin.all { it in '0'..'9' }) { "Invalid PIN" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .clear()
            .putString(KEY_METHOD, METHOD_PIN)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(pin, salt))
            .putInt(KEY_PIN_LEN, pin.length)
            .apply()
    }

    /** Turns the lock off and forgets everything (pattern, attempts, lockout). */
    @Synchronized
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // ── Verify + brute-force protection ─────────────────────────────────

    fun remainingLockMs(context: Context): Long {
        val p = prefs(context)
        val duration = p.getLong(KEY_LOCK_DURATION, 0L)
        if (duration <= 0L) return 0L
        val startElapsed = p.getLong(KEY_LOCK_START_ELAPSED, -1L)
        val nowElapsed = SystemClock.elapsedRealtime()
        val remaining = if (startElapsed in 0..nowElapsed) {
            duration - (nowElapsed - startElapsed)
        } else {
            // Device rebooted since the lock started (elapsedRealtime reset) —
            // fall back to wall-clock time.
            p.getLong(KEY_LOCK_UNTIL_WALL, 0L) - System.currentTimeMillis()
        }
        return remaining.coerceIn(0L, duration)
    }

    fun verifyPattern(context: Context, pattern: List<Int>): VerifyResult =
        verify(context, pattern.joinToString("-"))

    fun verifyPin(context: Context, pin: String): VerifyResult = verify(context, pin)

    @Synchronized
    private fun verify(context: Context, secret: String): VerifyResult {
        val p = prefs(context)

        val locked = remainingLockMs(context)
        if (locked > 0L) return VerifyResult.Locked(locked)

        val saltB64 = p.getString(KEY_SALT, null)
        val savedHash = p.getString(KEY_HASH, null)
        if (saltB64.isNullOrEmpty() || savedHash.isNullOrEmpty()) {
            // Lock data missing/corrupt — don't trap the user behind a gate that can't open.
            return VerifyResult.Success
        }

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val candidate = hash(secret, salt)
        val match = MessageDigest.isEqual(candidate.toByteArray(), savedHash.toByteArray())

        if (match) {
            p.edit()
                .putInt(KEY_ATTEMPTS, 0)
                .putInt(KEY_ROUNDS, 0)
                .remove(KEY_LOCK_DURATION)
                .remove(KEY_LOCK_START_ELAPSED)
                .remove(KEY_LOCK_UNTIL_WALL)
                .apply()
            return VerifyResult.Success
        }

        val attempts = p.getInt(KEY_ATTEMPTS, 0) + 1
        if (attempts < MAX_ATTEMPTS) {
            p.edit().putInt(KEY_ATTEMPTS, attempts).apply()
            return VerifyResult.Wrong(MAX_ATTEMPTS - attempts)
        }

        // A full round of wrong attempts.
        val rounds = p.getInt(KEY_ROUNDS, 0) + 1
        if (rounds > LOCKOUT_MS.size) {
            return VerifyResult.LogoutRequired
        }
        val duration = LOCKOUT_MS[rounds - 1]
        p.edit()
            .putInt(KEY_ATTEMPTS, 0)
            .putInt(KEY_ROUNDS, rounds)
            .putLong(KEY_LOCK_DURATION, duration)
            .putLong(KEY_LOCK_START_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_LOCK_UNTIL_WALL, System.currentTimeMillis() + duration)
            .apply()
        return VerifyResult.Locked(duration)
    }

    // ── Hashing ─────────────────────────────────────────────────────────

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(
            secret.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            256
        )
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
