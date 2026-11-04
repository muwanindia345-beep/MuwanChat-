package com.muwan.muwanchat.data

// GUEST_P2C
// Contact ki public key (secret nahi hai, isliye normal prefs). Key hamesha verify hoti hai:
// guestIdOf(key) == uid, warna save nahi hoti -- koi key badalkar contact ban nahi sakta.
import android.content.Context

object GuestPeers {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("guest_peers", Context.MODE_PRIVATE)

    fun get(context: Context, uid: String): ByteArray? {
        val s = prefs(context).getString("pk_" + uid, null) ?: return null
        return try {
            GuestCrypto.fromB64(s).takeIf { it.size == 32 }
        } catch (_: Exception) {
            null
        }
    }

    fun put(context: Context, uid: String, pub: ByteArray) {
        if (pub.size != 32 || GuestCrypto.guestIdOf(pub) != uid) return
        prefs(context).edit().putString("pk_" + uid, GuestCrypto.b64(pub)).apply()
    }
}
