package com.muwan.muwanchat.data

// GUEST_P2C
// Guest E2EE: sender har message ke liye ephemeral X25519 key banata hai, receiver ki Ed25519 identity key ko
// X25519 mein badalkar (birational map) ECDH karta hai -> HKDF -> ChaCha20-Poly1305.
// Receiver apni Ed25519 seed se X25519 private key nikalta hai (libsodium wala tareeka: SHA-512(seed)[0..32]).
// Payload = base64( 0x01 | ephemeralPub(32) | nonce(12) | ciphertext+tag ).
// Authenticity: har message ke andar sender ki Ed25519 signature hoti hai (domain "muwan-guest-msg-v1:").
import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

object GuestE2ee {

    private const val VERSION = 1
    private const val INFO = "muwan-guest-e2ee-v1"
    private const val SIG_DOMAIN = "muwan-guest-msg-v1:"
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val rng = SecureRandom()

    // Ed25519 public key (y) -> X25519 public key (u = (1+y)/(1-y) mod p)
    fun edPubToX25519(edPub: ByteArray): ByteArray {
        require(edPub.size == 32) { "bad public key" }
        val y = edPub.copyOf()
        y[31] = (y[31].toInt() and 0x7f).toByte()
        val be = ByteArray(32)
        for (i in 0 until 32) be[i] = y[31 - i]
        val yy = BigInteger(1, be)
        val num = BigInteger.ONE.add(yy).mod(P)
        val den = BigInteger.ONE.subtract(yy).mod(P)
        require(den.signum() != 0) { "bad public key" }
        val u = num.multiply(den.modInverse(P)).mod(P)
        val b = u.toByteArray()
        val out = ByteArray(32)
        for (i in 0 until 32) {
            val idx = b.size - 1 - i
            out[i] = if (idx >= 0) b[idx] else 0
        }
        return out
    }

    private fun xPrivFromSeed(seed: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").digest(seed).copyOfRange(0, 32)

    private fun agree(priv: ByteArray, pub: ByteArray): ByteArray {
        val a = X25519Agreement()
        a.init(X25519PrivateKeyParameters(priv, 0))
        val out = ByteArray(a.agreementSize)
        a.calculateAgreement(X25519PublicKeyParameters(pub, 0), out, 0)
        return out
    }

    private fun deriveKey(shared: ByteArray, from: String, to: String): ByteArray {
        val h = HKDFBytesGenerator(SHA256Digest())
        h.init(HKDFParameters(shared, null, (INFO + "|" + from + "|" + to).toByteArray(Charsets.UTF_8)))
        val k = ByteArray(32)
        h.generateBytes(k, 0, 32)
        return k
    }

    private fun aad(from: String, to: String, mid: String, kind: String): ByteArray =
        (from + "|" + to + "|" + mid + "|" + kind).toByteArray(Charsets.UTF_8)

    private fun aead(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val c = ChaCha20Poly1305()
        c.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(c.getOutputSize(input.size))
        var n = c.processBytes(input, 0, input.size, out, 0)
        n += c.doFinal(out, n)
        return out.copyOf(n)
    }

    fun seal(recipientEdPub: ByteArray, from: String, to: String, mid: String, kind: String, plaintext: ByteArray): String {
        val esk = X25519PrivateKeyParameters(rng)
        val epk = esk.generatePublicKey().encoded
        val shared = agree(esk.encoded, edPubToX25519(recipientEdPub))
        val key = deriveKey(shared, from, to)
        val nonce = ByteArray(12)
        rng.nextBytes(nonce)
        val ct = aead(true, key, nonce, aad(from, to, mid, kind), plaintext)
        val out = ByteArray(1 + 32 + 12 + ct.size)
        out[0] = VERSION.toByte()
        System.arraycopy(epk, 0, out, 1, 32)
        System.arraycopy(nonce, 0, out, 33, 12)
        System.arraycopy(ct, 0, out, 45, ct.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    // null = kharab / tampered / kisi aur ke liye
    fun open(mySeed: ByteArray, from: String, to: String, mid: String, kind: String, payloadB64: String): ByteArray? {
        return try {
            val raw = Base64.decode(payloadB64, Base64.NO_WRAP)
            if (raw.size < 1 + 32 + 12 + 16 || raw[0].toInt() != VERSION) return null
            val epk = raw.copyOfRange(1, 33)
            val nonce = raw.copyOfRange(33, 45)
            val ct = raw.copyOfRange(45, raw.size)
            val shared = agree(xPrivFromSeed(mySeed), epk)
            val key = deriveKey(shared, from, to)
            aead(false, key, nonce, aad(from, to, mid, kind), ct)
        } catch (_: Exception) {
            null
        }
    }

    private fun sigInput(from: String, to: String, mid: String, kind: String, body: String): ByteArray =
        (SIG_DOMAIN + from + ":" + to + ":" + mid + ":" + kind + ":" + body).toByteArray(Charsets.UTF_8)

    fun signMsg(seed: ByteArray, from: String, to: String, mid: String, kind: String, body: String): String =
        GuestCrypto.b64(GuestCrypto.sign(seed, sigInput(from, to, mid, kind, body)))

    fun verifyMsg(pub: ByteArray, sigB64: String, from: String, to: String, mid: String, kind: String, body: String): Boolean {
        return try {
            val msg = sigInput(from, to, mid, kind, body)
            val v = Ed25519Signer()
            v.init(false, Ed25519PublicKeyParameters(pub, 0))
            v.update(msg, 0, msg.size)
            v.verifySignature(GuestCrypto.fromB64(sigB64))
        } catch (_: Exception) {
            false
        }
    }
}
