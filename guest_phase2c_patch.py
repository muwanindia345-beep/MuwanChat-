import sys, os

# Usage (Termux, repo root MuwanChat se):   python guest_phase2c_patch.py .
# PEHLE Phase 1, 2A, 2B-1, 2B-2 (+ import fix) lage hone chahiye.
#
# GUEST PHASE 2C -- 1-to-1 E2EE chat (text) guest <-> guest:
#  * Naya E2EE: Ed25519 identity key se X25519 (koi nayi key backup nahi), har message ke liye ephemeral key,
#    ChaCha20-Poly1305 + HKDF, aur har message pe Ed25519 signature (sender asli hai, server nakli message nahi bana sakta).
#    Server sirf ciphertext relay karta hai.
#  * Invite QR/link mein ab sender ki public key bhi hoti hai (naya QR/link banana padega -- purane invite "Invalid" dikhayenge).
#  * Scan karte hi "hello" jaata hai -> jisne invite banaya uske phone mein bhi contact turant aa jata hai.
#  * Socket /guest se real-time; socket down ho to HTTP relay fallback; mailbox ke pending messages connect hote hi aate hain.
#  * Chat screen: send, reply, edit, delete-for-everyone, reactions, seen ticks -- sab local Room + encrypted envelopes.
#  * Media / voice / GIF / calls / pin / forward guest ke liye abhi "Coming Soon" popup.
#
# Naye files : data/GuestE2ee.kt, data/GuestPeers.kt, data/GuestChat.kt
# Patch      : GuestApi.kt, GuestInvite.kt, GuestInviteInbox.kt, ChatScreen.kt, ConversationListScreen.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likhta.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2C"

E2EE_KT = r'''package com.muwan.muwanchat.data

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
'''

PEERS_KT = r'''package com.muwan.muwanchat.data

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
'''

CHAT_KT = r'''package com.muwan.muwanchat.data

// GUEST_P2C
// Guest chat engine: /guest socket (real-time) + HTTP relay fallback, E2EE envelopes, Room mein local storage.
// Kinds: msg, hello, react, edit, del, seen. Har envelope ka mid unique (dedupe + idempotent retry).
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muwan.muwanchat.network.GuestEnvelopeOut
import com.muwan.muwanchat.network.GuestRelayRequest
import com.muwan.muwanchat.network.MessageReaction
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.screens.nowIso
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

object GuestChat {

    private const val TAG = "GuestChat"
    private const val PAGE = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectLock = Mutex()
    private val processLock = Mutex()
    private val gson = Gson()

    @Volatile private var socket: Socket? = null
    @Volatile private var socketToken: String? = null
    @Volatile private var openRoomId: String? = null
    @Volatile private var openPeerId: String? = null

    private val baseUrl: String get() = com.muwan.muwanchat.BuildConfig.BACKEND_URL.trimEnd('/')

    // ───────── connection ─────────

    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                connect(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "connect failed: " + e.message)
            }
        }
    }

    private suspend fun connect(app: Context) {
        connectLock.withLock {
            if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return@withLock
            val token = GuestStore.token(app) ?: return@withLock
            val cur = socket
            if (cur != null && socketToken == token) {
                if (!cur.connected()) cur.connect()
                return@withLock
            }
            if (cur != null) {
                cur.off()
                cur.disconnect()
            }
            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                transports = arrayOf("websocket")
                reconnection = true
            }
            val sock = IO.socket(baseUrl + "/guest", opts)
            sock.on("envelopes") { args ->
                val arr = args.getOrNull(0) as? JSONArray ?: return@on
                scope.launch {
                    try {
                        onEnvelopes(app, sock, arr)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "envelopes failed: " + e.message)
                    }
                }
            }
            sock.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.w(TAG, "socket error: " + (args.getOrNull(0)?.toString() ?: ""))
            }
            socket = sock
            socketToken = token
            sock.connect()
        }
    }

    fun onChatOpened(context: Context, roomId: String, peerUid: String) {
        val app = context.applicationContext
        openRoomId = roomId
        openPeerId = peerUid
        scope.launch {
            try {
                markOpenRead(app, roomId, peerUid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun onChatClosed(roomId: String) {
        if (openRoomId == roomId) {
            openRoomId = null
            openPeerId = null
        }
    }

    private suspend fun markOpenRead(app: Context, roomId: String, peerUid: String) {
        val myId = GuestStore.activeGuestId(app)
        if (myId.isEmpty()) return
        val db = MuwanChatDb.get(app, myId)
        db.conversationDao().clearUnread(roomId)
        db.messageDao().markSeen(roomId, myId)
        sendSeen(app, peerUid)
    }

    // ───────── receive ─────────

    private class Decoded(
        val from: String,
        val mid: String,
        val kind: String,
        val createdAt: String,
        val name: String,
        val pub: ByteArray,
        val body: JSONObject
    )

    private fun decode(app: Context, e: JSONObject, myId: String): Decoded? {
        return try {
            val from = e.optString("from")
            val mid = e.optString("mid")
            val kind = e.optString("kind", "msg").ifBlank { "msg" }
            val payload = e.optString("payload")
            if (from.isEmpty() || mid.isEmpty() || payload.isEmpty()) return null
            val seed = GuestStore.identity(app).seed
            val plain = GuestE2ee.open(seed, from, myId, mid, kind, payload) ?: return null
            val obj = JSONObject(String(plain, Charsets.UTF_8))
            val pub = GuestCrypto.fromB64(obj.getString("k"))
            if (pub.size != 32 || GuestCrypto.guestIdOf(pub) != from) return null
            val bodyStr = obj.getString("b")
            if (!GuestE2ee.verifyMsg(pub, obj.getString("s"), from, myId, mid, kind, bodyStr)) return null
            val created = e.optString("createdAt").ifBlank { nowIso() }
            Decoded(from, mid, kind, created, obj.optString("n").trim().take(40), pub, JSONObject(bodyStr))
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun onEnvelopes(app: Context, sock: Socket, arr: JSONArray) {
        val list = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
        list.sortBy { it.optString("id") }
        processLock.withLock {
            val myId = GuestStore.activeGuestId(app)
            if (myId.isEmpty()) return
            val ackIds = JSONArray()
            for (e in list) {
                val id = e.optString("id")
                val d = decode(app, e, myId)
                try {
                    if (d != null) applyEnvelope(app, myId, d)
                    if (id.isNotEmpty()) ackIds.put(id)   // kharab/nakli envelope bhi ack (drop), warna baar-baar aayega
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Log.w(TAG, "apply failed (will retry): " + ex.message)   // DB error: ack nahi, mailbox mein rahega
                }
            }
            if (ackIds.length() > 0) sock.emit("ack", ackIds)
            if (list.size >= PAGE) {
                val last = list.last().optString("id")
                if (last.isNotEmpty()) sock.emit("pending", JSONObject().put("after", last))
            }
        }
    }

    private suspend fun applyEnvelope(app: Context, myId: String, d: Decoded) {
        val db = MuwanChatDb.get(app, myId)
        GuestPeers.put(app, d.from, d.pub)
        val roomId = GuestInviteInbox.roomIdFor(myId, d.from)
        val shown = d.name.ifBlank { "Guest " + d.from.takeLast(4) }

        val existing = db.conversationDao().getByRoomId(roomId)
        if (existing == null) {
            ChatRepository.addConversationPlaceholder(db, roomId, d.from, shown, null)
        } else if (d.name.isNotBlank() && existing.username != d.name) {
            db.conversationDao().upsertOne(existing.copy(username = d.name))
        }

        when (d.kind) {
            "msg" -> {
                val text = d.body.optString("x")
                if (text.isBlank() || db.messageDao().getById(d.mid) != null) return
                ChatRepository.recordMessage(
                    db = db,
                    id = d.mid,
                    roomId = roomId,
                    senderUid = d.from,
                    receiverUid = myId,
                    content = text,
                    type = "text",
                    createdAt = d.createdAt,
                    myUid = myId,
                    otherUsername = shown,
                    replyToId = d.body.optString("r").ifBlank { null }
                )
                if (openRoomId == roomId) markOpenRead(app, roomId, d.from)
            }
            "react" -> {
                val target = db.messageDao().getById(d.body.optString("m")) ?: return
                if (target.roomId != roomId) return
                val emoji = d.body.optString("e")
                val type = object : TypeToken<List<MessageReaction>>() {}.type
                val current: List<MessageReaction> =
                    try { gson.fromJson<List<MessageReaction>>(target.reactions ?: "[]", type) ?: emptyList() } catch (_: Exception) { emptyList() }
                val next = current
                    .map { MessageReaction(it.emoji, it.userIds.filter { u -> u != d.from }) }
                    .filter { it.userIds.isNotEmpty() }
                    .toMutableList()
                if (emoji.isNotBlank()) {
                    val idx = next.indexOfFirst { it.emoji == emoji }
                    if (idx >= 0) next[idx] = MessageReaction(emoji, next[idx].userIds + d.from)
                    else next.add(MessageReaction(emoji, listOf(d.from)))
                }
                db.messageDao().updateReactions(target.id, gson.toJson(next))
                if (emoji.isNotBlank()) {
                    db.conversationDao().updateLastMessage(
                        roomId = roomId,
                        lastMessage = shown + " reacted " + emoji,
                        lastTime = nowIso(),
                        lastSenderUid = d.from,
                        myUid = myId
                    )
                }
            }
            "edit" -> {
                val target = db.messageDao().getById(d.body.optString("m")) ?: return
                val text = d.body.optString("x")
                if (target.roomId != roomId || target.senderUid != d.from || target.deleted || text.isBlank()) return
                db.messageDao().editMessage(target.id, text)
                ChatRepository.refreshLastMessagePreview(db, roomId)
            }
            "del" -> {
                val target = db.messageDao().getById(d.body.optString("m")) ?: return
                if (target.roomId != roomId || target.senderUid != d.from) return
                db.messageDao().markDeleted(target.id)
                ChatRepository.refreshLastMessagePreview(db, roomId)
            }
            "seen" -> db.messageDao().markMySentAsSeen(roomId, myId)
            else -> {}   // "hello" aur anjaan kinds: sirf contact + key save ho chuki
        }
    }

    // ───────── send ─────────

    private suspend fun sendEnvelope(app: Context, toUid: String, mid: String, kind: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val myId = GuestStore.activeGuestId(app)
            if (myId.isEmpty()) return@withContext false
            val ident = GuestStore.identity(app)
            val peer = GuestPeers.get(app, toUid) ?: return@withContext false
            val profile = try { MuwanChatDb.get(app, myId).myProfileDao().get() } catch (_: Exception) { null }
            val bodyStr = body.toString()
            val sig = GuestE2ee.signMsg(ident.seed, myId, toUid, mid, kind, bodyStr)
            val plain = JSONObject()
                .put("k", GuestCrypto.b64(ident.publicKey))
                .put("n", (profile?.name ?: "").take(40))
                .put("c", (profile?.country ?: "").take(40))
                .put("b", bodyStr)
                .put("s", sig)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val payload = GuestE2ee.seal(peer, myId, toUid, mid, kind, plain)
            relay(app, toUid, mid, kind, payload)
        }

    private suspend fun relay(app: Context, toUid: String, mid: String, kind: String, payload: String): Boolean {
        val sock = socket
        if (sock != null && sock.connected()) {
            val env = JSONObject().put("to", toUid).put("mid", mid).put("kind", kind).put("payload", payload)
            val viaSocket: Boolean? = withTimeoutOrNull(8000L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    sock.emit("relay", JSONArray().put(env), Ack { args ->
                        val r = args.getOrNull(0) as? JSONObject
                        val ok = if (r == null || !r.optBoolean("success")) {
                            false
                        } else {
                            r.optJSONArray("results")?.optJSONObject(0)?.optString("status") == "queued"
                        }
                        if (cont.isActive) cont.resume(ok)
                    })
                }
            }
            if (viaSocket != null) return viaSocket
        }
        // socket down / timeout: HTTP relay (same mid -> server dedupe, duplicate nahi banega)
        return try {
            if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return false
            val token = GuestStore.token(app) ?: return false
            val res = RetrofitClient.guestApi.relay(
                "Bearer " + token,
                GuestRelayRequest(listOf(GuestEnvelopeOut(toUid, mid, kind, payload)))
            )
            res.isSuccessful && res.body()?.results?.firstOrNull()?.status == "queued"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun newMid(prefix: String): String = prefix + UUID.randomUUID().toString()

    suspend fun sendText(app: Context, toUid: String, id: String, text: String, replyToId: String?): Boolean {
        val body = JSONObject().put("x", text).put("ts", System.currentTimeMillis())
        if (!replyToId.isNullOrBlank()) body.put("r", replyToId)
        return sendEnvelope(app.applicationContext, toUid, id, "msg", body)
    }

    suspend fun sendReaction(app: Context, toUid: String, targetMid: String, emoji: String): Boolean =
        sendEnvelope(
            app.applicationContext, toUid, newMid("r-"), "react",
            JSONObject().put("m", targetMid).put("e", emoji).put("ts", System.currentTimeMillis())
        )

    suspend fun sendEdit(app: Context, toUid: String, targetMid: String, text: String): Boolean =
        sendEnvelope(
            app.applicationContext, toUid, newMid("e-"), "edit",
            JSONObject().put("m", targetMid).put("x", text).put("ts", System.currentTimeMillis())
        )

    suspend fun sendDelete(app: Context, toUid: String, targetMid: String): Boolean =
        sendEnvelope(
            app.applicationContext, toUid, newMid("d-"), "del",
            JSONObject().put("m", targetMid).put("ts", System.currentTimeMillis())
        )

    suspend fun sendSeen(app: Context, toUid: String): Boolean =
        sendEnvelope(
            app.applicationContext, toUid, newMid("s-"), "seen",
            JSONObject().put("ts", System.currentTimeMillis())
        )

    // Invite accept hote hi: dusre ko batao ki hum judd gaye (uske phone mein contact + key aa jaati hai)
    fun sendHelloAsync(context: Context, toUid: String) {
        val app = context.applicationContext
        scope.launch {
            try {
                sendEnvelope(app, toUid, newMid("h-"), "hello", JSONObject().put("ts", System.currentTimeMillis()))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
'''

API_DATA = r'''// GUEST_P2C -- HTTP relay (socket down hone par fallback)
data class GuestEnvelopeOut(
    val to: String,
    val mid: String,
    val kind: String,
    val payload: String
)

data class GuestRelayRequest(
    val envelopes: List<GuestEnvelopeOut>
)

data class GuestRelayResult(
    val mid: String?,
    val to: String?,
    val status: String?
)

data class GuestRelayResponse(
    val success: Boolean?,
    val results: List<GuestRelayResult>?,
    val error: String?
)

'''

def rd(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: anchor %d baar mila (1 hona chahiye). Kuch nahi likha." % (name, n))
        sys.exit(1)
    return s.replace(old, new)

writes = {}
skipped = []

def plan_new_file(rel, content):
    p = os.path.join(BASE, rel)
    if os.path.exists(p):
        if MARK in rd(p):
            skipped.append(rel)
            return
        print("FAIL: %s pehle se hai par GUEST_P2C wala nahi. Kuch nahi likha." % rel)
        sys.exit(1)
    writes[p] = content

def plan_patch(path, label, fn, need="GUEST_P2B2"):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label)
        return
    if need and need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle guest_phase2b2_patch.py lagao. Kuch nahi likha." % (label, need))
        sys.exit(1)
    writes[path] = fn(s)

plan_new_file("data/GuestE2ee.kt", E2EE_KT)
plan_new_file("data/GuestPeers.kt", PEERS_KT)
plan_new_file("data/GuestChat.kt", CHAT_KT)

# --- GuestApi.kt
def p_api(s):
    s = rep(s, 'interface GuestApi {\n', API_DATA + 'interface GuestApi {\n', "api data")
    s = rep(s,
        '        @Body request: GuestRedeemRequest\n    ): Response<GuestInviteResponse>\n',
        '        @Body request: GuestRedeemRequest\n    ): Response<GuestInviteResponse>\n'
        '\n'
        '    // GUEST_P2C\n'
        '    @POST("guest/relay")\n'
        '    suspend fun relay(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @Body request: GuestRelayRequest\n'
        '    ): Response<GuestRelayResponse>\n',
        "api relay")
    return s
plan_patch(os.path.join(BASE, "network/GuestApi.kt"), "GuestApi.kt", p_api)

# --- GuestInvite.kt : payload mein public key
def p_inv(s):
    return rep(s,
        '            .put("c", (profile?.country ?: "").take(40))\n            .toString()\n',
        '            .put("c", (profile?.country ?: "").take(40))\n'
        '            .put("k", GuestCrypto.b64(GuestStore.identity(context).publicKey))  // GUEST_P2C: E2EE ke liye public key\n'
        '            .toString()\n',
        "invite key")
plan_patch(os.path.join(BASE, "data/GuestInvite.kt"), "GuestInvite.kt", p_inv, need="GUEST_P2B1")

# --- GuestInviteInbox.kt : key parse/verify, peer save, hello
def p_inbox(s):
    s = rep(s,
        'class Invite(val guestId: String, val secret: String, val name: String, val country: String)',
        'class Invite(val guestId: String, val secret: String, val name: String, val country: String, val pub: ByteArray)  // GUEST_P2C',
        "inbox class")
    s = rep(s,
        '            val secret = json.optString("s")\n'
        '            if (json.optInt("v", 0) != 1) null\n',
        '            val secret = json.optString("s")\n'
        '            val pub = try { GuestCrypto.fromB64(json.optString("k")) } catch (_: Exception) { ByteArray(0) }  // GUEST_P2C\n'
        '            if (json.optInt("v", 0) != 1) null\n',
        "inbox pub")
    s = rep(s,
        '            else if (secret.length < 16 || secret.length > 200) null\n'
        '            else Invite(\n'
        '                guestId,\n'
        '                secret,\n'
        '                json.optString("n").trim().take(40),\n'
        '                json.optString("c").trim().take(40)\n'
        '            )\n',
        '            else if (secret.length < 16 || secret.length > 200) null\n'
        '            else if (pub.size != 32 || GuestCrypto.guestIdOf(pub) != guestId) null  // key id se match honi chahiye\n'
        '            else Invite(\n'
        '                guestId,\n'
        '                secret,\n'
        '                json.optString("n").trim().take(40),\n'
        '                json.optString("c").trim().take(40),\n'
        '                pub\n'
        '            )\n',
        "inbox parse")
    s = rep(s,
        '            val shown = inv.name.ifBlank { "Guest " + inv.guestId.takeLast(4) }\n'
        '            if (db.conversationDao().getByUid(inv.guestId) != null) {\n',
        '            val shown = inv.name.ifBlank { "Guest " + inv.guestId.takeLast(4) }\n'
        '            GuestPeers.put(app, inv.guestId, inv.pub)  // GUEST_P2C\n'
        '            if (db.conversationDao().getByUid(inv.guestId) != null) {\n',
        "inbox peer")
    s = rep(s,
        '            Pair(true, shown + " added to your chats.")\n        } catch (e: CancellationException) {',
        '            GuestChat.start(app)  // GUEST_P2C\n'
        '            GuestChat.sendHelloAsync(app, inv.guestId)  // dusre ke phone mein bhi contact aa jaye\n'
        '            Pair(true, shown + " added to your chats.")\n        } catch (e: CancellationException) {',
        "inbox hello")
    return s
plan_patch(os.path.join(BASE, "data/GuestInviteInbox.kt"), "GuestInviteInbox.kt", p_inbox)

# --- ConversationListScreen.kt : guest chat engine start
def p_conv(s):
    return rep(s,
        '    if (isGuest) GuestInviteHost()  // GUEST_P2B2: invite confirm popup\n',
        '    if (isGuest) GuestInviteHost()  // GUEST_P2B2: invite confirm popup\n'
        '    if (isGuest) androidx.compose.runtime.LaunchedEffect(Unit) {  // GUEST_P2C: socket + mailbox\n'
        '        com.muwan.muwanchat.data.GuestChat.start(context)\n'
        '    }\n',
        "conv start")
plan_patch(os.path.join(BASE, "screens/ConversationListScreen.kt"), "ConversationListScreen.kt", p_conv)

# --- ChatScreen.kt
def p_chat(s):
    G = "com.muwan.muwanchat.data.GuestChat"
    s = rep(s,
        '    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }\n    val currentWallpaper',
        '    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }\n'
        '    val isGuest = remember { com.muwan.muwanchat.data.GuestStore.isGuestActive(context) }  // GUEST_P2C\n'
        '    val currentWallpaper',
        "chat isGuest")
    # open chat
    s = rep(s,
        '    LaunchedEffect(Unit) {\n        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect\n        myToken = token\n',
        '    LaunchedEffect(Unit) {\n'
        '        if (isGuest) {  // GUEST_P2C: guest ke liye alag raasta (auth token/API nahi)\n'
        '            myUid = AuthDataStore.getUidBlocking(context)\n'
        '            ' + G + '.start(context)\n'
        '            ' + G + '.onChatOpened(context, roomId, receiverUid)\n'
        '            return@LaunchedEffect\n'
        '        }\n'
        '        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect\n        myToken = token\n',
        "chat open")
    # send text
    s = rep(s,
        '        if (AppSocketManager.isConnected) {\n            val timeoutJob = scope.launch {\n',
        '        if (isGuest) {  // GUEST_P2C\n'
        '            scope.launch {\n'
        '                val ok = ' + G + '.sendText(context, receiverUid, id, content, replyToId)\n'
        '                db.messageDao().updateStatus(id, if (ok) "SENT" else "FAILED")\n'
        '            }\n'
        '            return\n'
        '        }\n'
        '        if (AppSocketManager.isConnected) {\n            val timeoutJob = scope.launch {\n',
        "chat send")
    # reaction
    s = rep(s,
        '        scope.launch {\n            try {\n                val res = RetrofitClient.chatApi.reactToMessage(\n',
        '        if (isGuest) {  // GUEST_P2C\n'
        '            scope.launch {\n'
        '                try {\n'
        '                    ' + G + '.sendReaction(context, receiverUid, messageId, if (hadSameEmoji) "" else emoji)\n'
        '                } finally {\n'
        '                    reactionInFlight.value = reactionInFlight.value - messageId\n'
        '                }\n'
        '            }\n'
        '            return\n'
        '        }\n'
        '        scope.launch {\n            try {\n                val res = RetrofitClient.chatApi.reactToMessage(\n',
        "chat react")
    # delete for me (server call skip)
    s = rep(s,
        '                    RetrofitClient.chatApi.hideMessageForMe("Bearer $myToken", roomId, id)\n',
        '                    if (!isGuest) RetrofitClient.chatApi.hideMessageForMe("Bearer $myToken", roomId, id)\n',
        "chat delme")
    # delete for everyone
    s = rep(s,
        '                        val res = RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)\n'
        '                        res.isSuccessful || res.code() == 404   // 404 = server pe pehle se nahi hai\n',
        '                        if (isGuest) {  // GUEST_P2C\n'
        '                            ' + G + '.sendDelete(context, receiverUid, id)\n'
        '                        } else {\n'
        '                            val res = RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)\n'
        '                            res.isSuccessful || res.code() == 404   // 404 = server pe pehle se nahi hai\n'
        '                        }\n',
        "chat delall")
    # edit
    s = rep(s,
        '                RetrofitClient.chatApi.editMessage("Bearer $myToken", roomId, msg.id, EditMessageRequest(newText))\n',
        '                if (isGuest) ' + G + '.sendEdit(context, receiverUid, msg.id, newText)  // GUEST_P2C\n'
        '                else RetrofitClient.chatApi.editMessage("Bearer $myToken", roomId, msg.id, EditMessageRequest(newText))\n',
        "chat edit")
    # leave
    s = rep(s,
        '            AppSocketManager.leaveRoom(roomId)\n',
        '            AppSocketManager.leaveRoom(roomId)\n'
        '            if (isGuest) ' + G + '.onChatClosed(roomId)  // GUEST_P2C\n',
        "chat leave")
    # Coming Soon gates
    s = rep(s,
        '            onPickImage = { showMediaSheet = true },\n',
        '            onPickImage = { if (isGuest) comingSoonFeature = "📎 Media sharing" else showMediaSheet = true },  // GUEST_P2C\n',
        "gate media")
    s = rep(s,
        '            onVoiceMessage = {\n',
        '            onVoiceMessage = {\n'
        '                if (isGuest) { comingSoonFeature = "🎤 Voice messages"; return@ChatInputBar }  // GUEST_P2C\n',
        "gate voice")
    s = rep(s,
        '            onGifReceived = { uri, kbMime, release ->\n',
        '            onGifReceived = { uri, kbMime, release ->\n'
        '                if (isGuest) { comingSoonFeature = "🎬 GIFs"; release(); return@ChatInputBar }  // GUEST_P2C\n',
        "gate gif")
    s = rep(s,
        '                onVideoCall = { // VIDEO_P4\n',
        '                onVideoCall = { // VIDEO_P4\n'
        '                    if (isGuest) { comingSoonFeature = "📹 Video calls"; return@ChatHeader }  // GUEST_P2C\n',
        "gate vcall")
    s = rep(s,
        '                onVoiceCall = {\n',
        '                onVoiceCall = {\n'
        '                    if (isGuest) { comingSoonFeature = "📞 Voice calls"; return@ChatHeader }  // GUEST_P2C\n',
        "gate call")
    s = rep(s,
        '                    IconButton(onClick = {\n                        val idsToPin = selectedMessageIds.toList()\n',
        '                    IconButton(onClick = {\n'
        '                        if (isGuest) { comingSoonFeature = "📌 Pin messages"; exitSelectionMode(); return@IconButton }  // GUEST_P2C\n'
        '                        val idsToPin = selectedMessageIds.toList()\n',
        "gate pin")
    s = rep(s,
        '                    IconButton(onClick = {\n                        val toForward = messages.filter { selectedMessageIds.contains(it.id) }\n',
        '                    IconButton(onClick = {\n'
        '                        if (isGuest) { comingSoonFeature = "↪️ Forward messages"; exitSelectionMode(); return@IconButton }  // GUEST_P2C\n'
        '                        val toForward = messages.filter { selectedMessageIds.contains(it.id) }\n',
        "gate forward")
    return s
plan_patch(os.path.join(BASE, "screens/ChatScreen.kt"), "ChatScreen.kt", p_chat, need=None)

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
print("\nGuest Phase 2C patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
