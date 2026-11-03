package com.muwan.muwanchat.data

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
