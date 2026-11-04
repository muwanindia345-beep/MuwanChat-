package com.muwan.muwanchat.data

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
