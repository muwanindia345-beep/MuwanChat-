package com.muwan.muwanchat.data

// GUEST_P2C
// Guest chat engine: /guest socket (real-time) + HTTP relay fallback, E2EE envelopes, Room mein local storage.
// Kinds: msg, hello, react, edit, del, seen. Har envelope ka mid unique (dedupe + idempotent retry).
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muwan.muwanchat.network.GuestAckRequest  // GUEST_P2F
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        if (!GuestStore.isGuestActive(app)) return  // GUEST_LOGOUT
        GuestPush.ensureRegistered(app)  // GUEST_P2F: FCM token server ko
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

    // GUEST_LOGOUT -- socket band, open chat bhula do, online list khali
    fun stop() {
        try {
            socket?.off()
            socket?.disconnect()
        } catch (_: Exception) {
        }
        socket = null
        socketToken = null
        openRoomId = null
        openPeerId = null
        _online.value = emptySet()
    }

    private suspend fun connect(app: Context) {
        connectLock.withLock {
            if (!GuestStore.isGuestActive(app)) return@withLock  // GUEST_LOGOUT
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
            sock.on(Socket.EVENT_CONNECT) {  // GUEST_P2E
                GuestProfile.syncSoon(app)  // GUEST_PROF: pending profile updates
                GuestMedia.resume(app)  // GUEST_MEDIA: adhoore downloads
                scope.launch {
                    try {
                        refreshPresence(app)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
            sock.on(Socket.EVENT_DISCONNECT) { _online.value = emptySet() }
            sock.on("presence") { args ->
                val o = args.getOrNull(0) as? JSONObject ?: return@on
                val id = o.optString("id")
                if (id.isEmpty()) return@on
                _online.value = if (o.optBoolean("online")) _online.value + id else _online.value - id
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
                refreshPresence(app)  // GUEST_P2E
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
        GuestPush.cancel(app, roomId)  // GUEST_P2F
        sendSeen(app, peerUid)
    }

    // GUEST_P2E ───────── presence ─────────
    private val _online = MutableStateFlow<Set<String>>(emptySet())
    val onlineUids: StateFlow<Set<String>> = _online

    // Contacts ka online status server se (sirf wahi jo dono taraf se allowed hain). Baad ke changes push se aate hain.
    private suspend fun refreshPresence(app: Context) {
        val sock = socket ?: return
        if (!sock.connected()) return
        val myId = GuestStore.activeGuestId(app)
        if (myId.isEmpty()) return
        val ids = MuwanChatDb.get(app, myId).conversationDao().getAll()
            .map { it.uid }.filter { it.startsWith("g_") }.distinct().take(200)
        if (ids.isEmpty()) return
        sock.emit("presence", JSONArray(ids), Ack { args ->
            val arr = (args.getOrNull(0) as? JSONObject)?.optJSONArray("online")
            if (arr != null) {
                val s = HashSet<String>()
                for (i in 0 until arr.length()) s.add(arr.optString(i))
                _online.value = s
            }
        })
    }

    fun refreshPresenceNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                refreshPresence(app)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
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
        if (d.kind == "ginv" || d.kind == "gmsg") {  // GUEST_GROUP_HOOK: group envelopes 1:1 chat nahi banate
            GuestGroups.onEnvelope(app, db, myId, d.from, d.mid, d.kind, d.createdAt, d.name, d.body)
            return
        }
        val roomId = GuestInviteInbox.roomIdFor(myId, d.from)
        val shown = d.name.ifBlank { "Guest " + d.from.takeLast(4) }

        val existing = db.conversationDao().getByRoomId(roomId)
        if (existing == null) {
            ChatRepository.addConversationPlaceholder(db, roomId, d.from, shown, null)
            GuestProfile.syncSoon(app)  // GUEST_PROF: naye contact ko apna profile bhejo
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
                    replyToId = d.body.optString("r").ifBlank { null },
                    isForwarded = d.body.optInt("f") == 1  // GUEST_ACT
                )
                if (!GuestPush.visible) GuestPush.showMessage(app, roomId, shown, text)  // GUEST_P2F: decrypted notification
                if (openRoomId == roomId && GuestPush.visible) markOpenRead(app, roomId, d.from)
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
            "prof" -> GuestProfile.applyIncoming(db, roomId, d.from, d.body)  // GUEST_PROF
            "pin" -> {  // GUEST_ACT
                val pm = d.body.optString("m")
                val target = db.messageDao().getById(pm) ?: return
                if (target.roomId != roomId) return
                GuestPins.set(app, roomId, pm, d.body.optInt("on") == 1)
            }
            "media" -> {  // GUEST_MEDIA
                if (db.messageDao().getById(d.mid) != null) return
                val inc = GuestMedia.parseIncoming(d.mid, d.body) ?: return
                ChatRepository.recordMessage(
                    db = db, id = d.mid, roomId = roomId, senderUid = d.from, receiverUid = myId,
                    content = inc.url, type = inc.type, createdAt = d.createdAt, myUid = myId,
                    otherUsername = shown, fileName = inc.fileName, mimeType = inc.mime, replyToId = inc.replyTo
                )
                GuestMedia.enqueue(app, d.mid, d.body)
                if (!GuestPush.visible) GuestPush.showMessage(app, roomId, shown, GuestMedia.label(inc.type))
                if (openRoomId == roomId && GuestPush.visible) markOpenRead(app, roomId, d.from)
            }
            else -> {}   // "hello" aur anjaan kinds: sirf contact + key save ho chuki
        }
    }

    // GUEST_P2F ───────── push wake: mailbox HTTP se ─────────
    fun onPushWake(context: Context) {
        val app = context.applicationContext
        if (!GuestStore.isGuestActive(app)) return  // GUEST_LOGOUT
        scope.launch {
            try {
                syncMailbox(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "push sync failed: " + e.message)
            }
        }
    }

    // App wapas screen par aayi aur koi chat khuli hai: background mein aaye messages read mark + notification hatao
    fun onAppVisible(context: Context) {
        val room = openRoomId
        val peer = openPeerId
        if (room != null && peer != null) onChatOpened(context, room, peer)
    }

    // Socket ke bina: pending envelopes HTTP se (page-wise), wahi decrypt/save/notify, phir ack.
    suspend fun syncMailbox(app: Context) {
        processLock.withLock {
            if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return
            val token = GuestStore.token(app) ?: return
            val myId = GuestStore.activeGuestId(app)
            if (myId.isEmpty()) return
            var after: String? = null
            for (page in 0 until 10) {
                val res = RetrofitClient.guestApi.mailbox("Bearer " + token, after, PAGE)
                if (!res.isSuccessful) return
                val bodyText = res.body()?.string() ?: return
                val arr = JSONObject(bodyText).optJSONArray("envelopes") ?: return
                if (arr.length() == 0) return
                val list = ArrayList<JSONObject>()
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
                list.sortBy { it.optString("id") }
                val ackIds = ArrayList<String>()
                for (e in list) {
                    val id = e.optString("id")
                    val d = decode(app, e, myId)
                    try {
                        if (d != null) applyEnvelope(app, myId, d)
                        if (id.isNotEmpty()) ackIds.add(id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Log.w(TAG, "apply failed (will retry): " + ex.message)
                    }
                }
                if (ackIds.isNotEmpty()) {
                    RetrofitClient.guestApi.ackMailbox("Bearer " + token, GuestAckRequest(ackIds))
                }
                after = list.last().optString("id")
                if (list.size < PAGE) return
            }
        }
    }

    // ───────── send ─────────

    internal suspend fun sendEnvelope(app: Context, toUid: String, mid: String, kind: String, body: JSONObject): Boolean =  // GUEST_GROUP_HOOK
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

    // GUEST_ACT
    suspend fun sendKind(app: Context, toUid: String, mid: String, kind: String, body: JSONObject): Boolean =
        sendEnvelope(app.applicationContext, toUid, mid, kind, body)

    // GUEST_MEDIA
    suspend fun sendMedia(app: Context, toUid: String, mid: String, body: JSONObject): Boolean =
        sendEnvelope(app.applicationContext, toUid, mid, "media", body)

    // GUEST_PROF
    suspend fun sendProfile(app: Context, toUid: String, body: JSONObject): Boolean =
        sendEnvelope(app.applicationContext, toUid, newMid("p-"), "prof", body)

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
