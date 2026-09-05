package com.muwan.muwanchat.data

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

private const val CHAT_BACKEND_URL = "https://muwan-chat-backend-production-3ca2.up.railway.app"

sealed class SocketEvent {
    data class NewMessage(
        val id: String,
        val roomId: String,
        val senderUid: String,
        val content: String,
        val createdAt: String,
        val type: String = "text",
        val fileName: String? = null,
        val mimeType: String? = null,
        val replyToId: String? = null,
        val isForwarded: Boolean = false
    ) : SocketEvent()

    data class UserOnline(val uid: String) : SocketEvent()
    data class UserOffline(val uid: String) : SocketEvent()
    data class PresenceStatus(val uid: String, val online: Boolean) : SocketEvent()

    data class Typing(val uid: String, val roomId: String) : SocketEvent()
    data class StopTyping(val uid: String) : SocketEvent()

    data class MessagesSeen(
        val roomId: String,
        val seenBy: String,
        val fullySeenIds: List<String> = emptyList()
    ) : SocketEvent()

    // "Delete for Everyone" ka result — dusre user ki screen bhi isi se live update hoti hai
    data class MessageDeleted(val id: String, val roomId: String) : SocketEvent()

    // Edit Message ka result — sender ke alawa dusre device/user ki screen bhi isi se update hoti hai
    data class MessageEdited(val id: String, val roomId: String, val content: String) : SocketEvent()

    // Pin/Unpin shared hote hai — dono/sabhi participants ki screen isi se live update hoti hai
    data class MessagePinned(val id: String, val roomId: String, val pinnedAt: String) : SocketEvent()
    data class MessageUnpinned(val id: String, val roomId: String) : SocketEvent()

    // Reaction add/remove ka result — reactionsJson poori updated list hai (server se aata hai)
    data class ReactionUpdate(
        val id: String,
        val roomId: String,
        val reactionsJson: String,
        val reactorUid: String = "",
        val emoji: String = "",
        val added: Boolean = true
    ) : SocketEvent()

    data class MessagePreview(
        val id: String,
        val roomId: String,
        val title: String?,
        val description: String?,
        val image: String?,
        val url: String?
    ) : SocketEvent()

    data class NewRequest(
        val id: String,
        val senderUid: String,
        val username: String,
        val avatar: String?,
        val createdAt: String
    ) : SocketEvent()

    data class RequestAccepted(
        val roomId: String,
        val uid: String,
        val username: String,
        val avatar: String?
    ) : SocketEvent()

    // Naya pending join request (link se ya kisi member ke add karne se) --
    // sirf admins/owner ko emit hota hai (backend side filter). GroupInfoScreen
    // ka red dot isi se live update hota hai, poori list ke liye REST call.
    data class JoinRequest(
        val roomId: String,
        val uid: String,
        val username: String,
        val source: String
    ) : SocketEvent()

    // Kicked ya khud-leave -- selfLeave se client decide karta hai banner
    // dikhana hai ya nahi ("You were removed from group by @Admin").
    data class GroupRemoved(
        val roomId: String,
        val selfLeave: Boolean,
        val removedByUsername: String?,
        val groupDeleted: Boolean = false
    ) : SocketEvent()

    // Dusre user ne "Accepted Users" screen se humein remove kiya --
    // uid us user ka hai jisne remove kiya. roomId consumer khud banata
    // hai (sorted myUid+uid), backend sirf uid bhejta hai.
    data class ConnectionRemoved(val uid: String) : SocketEvent()

    // Group settings (onlyAdminsCanSend, membersCanAdd, etc.) admin ne change
    // kiye -- poora group object backend bhejta hai lekin yahan sirf roomId
    // nikalte hain, consumer REST se hi fresh GroupData fetch karta hai.
    data class GroupUpdated(val roomId: String) : SocketEvent()

    // ───────────── Call signaling events ────────────────────
    data class CallOfferReceived(
        val callId: String,
        val fromUid: String,
        val fromUsername: String,
        val callType: String, // "voice" | "video"
        val sdp: String
    ) : SocketEvent()

    data class CallAnswerReceived(val callId: String, val sdp: String) : SocketEvent()

    data class CallEndReceived(val callId: String, val reason: String) : SocketEvent()

    data class CallBusyReceived(val callId: String) : SocketEvent()

    data class IceCandidateReceived(
        val callId: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String
    ) : SocketEvent()
}

object AppSocketManager {

    private var socket: Socket? = null
    private var currentToken: String? = null

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private val _onlineUids = MutableStateFlow<Set<String>>(emptySet())
    val onlineUids: StateFlow<Set<String>> = _onlineUids.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingUsers: StateFlow<Map<String, String>> = _typingUsers.asStateFlow()

    val isConnected: Boolean
        get() = socket?.connected() == true

    fun connect(token: String) {
        if (socket != null && currentToken == token) {
            if (socket?.connected() != true) socket?.connect()
            return
        }

        if (socket != null) disconnect()

        currentToken = token
        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                transports = arrayOf("websocket")
                reconnection = true
            }
            val s = IO.socket(CHAT_BACKEND_URL, opts)

            s.on("new_message") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.NewMessage(
                        id = json.optString("id"),
                        roomId = json.optString("room_id"),
                        senderUid = json.optString("sender_uid"),
                        content = json.optString("content"),
                        createdAt = json.optString("created_at"),
                        type = json.optString("type", "text"),
                        fileName = if (json.isNull("file_name")) null else json.optString("file_name"),
                        mimeType = if (json.isNull("mime_type")) null else json.optString("mime_type"),
                        replyToId = if (json.isNull("reply_to_id")) null else json.optString("reply_to_id"),
                        isForwarded = json.optBoolean("is_forwarded", false)
                    )
                )
            }

            s.on("user_online") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val uid = json.optString("uid")
                _onlineUids.update { it + uid }
                _events.tryEmit(SocketEvent.UserOnline(uid))
            }

            s.on("user_offline") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val uid = json.optString("uid")
                _onlineUids.update { it - uid }
                _typingUsers.update { it - uid }
                _events.tryEmit(SocketEvent.UserOffline(uid))
            }

            s.on("presence_status") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val uid = json.optString("uid")
                val online = json.optBoolean("online", false)
                _onlineUids.update { if (online) it + uid else it - uid }
                _events.tryEmit(SocketEvent.PresenceStatus(uid, online))
            }

            s.on("typing") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val uid = json.optString("uid")
                val roomId = json.optString("room_id")
                _typingUsers.update { it + (uid to roomId) }
                _events.tryEmit(SocketEvent.Typing(uid, roomId))
            }

            s.on("stop_typing") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val uid = json.optString("uid")
                _typingUsers.update { it - uid }
                _events.tryEmit(SocketEvent.StopTyping(uid))
            }

            s.on("messages_seen") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                // Group chats mein backend "fully_seen_ids" bhejta hai — sirf
                // woh messages jinhe SABHI group members ne dekh liya hai.
                // 1-1 chat mein yeh array nahi aata (khali rahega, koi farak
                // nahi padta kyunki ChatScreen isko use hi nahi karta).
                val fullySeenArr = json.optJSONArray("fully_seen_ids")
                val fullySeenIds = if (fullySeenArr != null) {
                    (0 until fullySeenArr.length()).map { fullySeenArr.getString(it) }
                } else emptyList()
                _events.tryEmit(
                    SocketEvent.MessagesSeen(
                        json.optString("room_id"),
                        json.optString("seen_by"),
                        fullySeenIds
                    )
                )
            }

            s.on("message_deleted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageDeleted(json.optString("id"), json.optString("room_id"))
                )
            }

            s.on("message_pinned") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessagePinned(
                        json.optString("id"),
                        json.optString("room_id"),
                        json.optString("pinned_at")
                    )
                )
            }

            s.on("message_unpinned") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageUnpinned(json.optString("id"), json.optString("room_id"))
                )
            }

            s.on("message_edited") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageEdited(
                        json.optString("id"),
                        json.optString("room_id"),
                        json.optString("content")
                    )
                )
            }

            s.on("reaction_update") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val reactionsJson = json.optJSONArray("reactions")?.toString() ?: "[]"
                _events.tryEmit(
                    SocketEvent.ReactionUpdate(
                        id = json.optString("id"),
                        roomId = json.optString("room_id"),
                        reactionsJson = reactionsJson,
                        reactorUid = json.optString("uid"),
                        emoji = json.optString("emoji"),
                        added = json.optBoolean("added", true)
                    )
                )
            }

            s.on("message_preview") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val preview = json.optJSONObject("preview") ?: return@on
                fun field(name: String) = preview.optString(name, "").ifEmpty { null }
                _events.tryEmit(
                    SocketEvent.MessagePreview(
                        id = json.optString("id"),
                        roomId = json.optString("room_id"),
                        title = field("title"),
                        description = field("description"),
                        image = field("image"),
                        url = field("url")
                    )
                )
            }

            s.on("new_request") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.NewRequest(
                        id = json.optString("id"),
                        senderUid = json.optString("sender_uid"),
                        username = json.optString("username"),
                        avatar = if (json.isNull("avatar")) null else json.optString("avatar"),
                        createdAt = json.optString("created_at")
                    )
                )
            }

            s.on("request_accepted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.RequestAccepted(
                        roomId = json.optString("room_id"),
                        uid = json.optString("uid"),
                        username = json.optString("username"),
                        avatar = if (json.isNull("avatar")) null else json.optString("avatar")
                    )
                )
            }

            s.on("join_request") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.JoinRequest(
                        roomId = json.optString("roomId"),
                        uid = json.optString("uid"),
                        username = json.optString("username"),
                        source = json.optString("source")
                    )
                )
            }

            s.on("group_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.GroupRemoved(
                        roomId = json.optString("roomId"),
                        selfLeave = json.optBoolean("selfLeave", true),
                        removedByUsername = if (json.isNull("removedByUsername")) null else json.optString("removedByUsername"),
                        groupDeleted = json.optBoolean("groupDeleted", false)
                    )
                )
            }

            s.on("connection_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.ConnectionRemoved(uid = json.optString("uid"))
                )
            }

            s.on("group_updated") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val groupId = json.optJSONObject("group")?.optString("id")
                if (!groupId.isNullOrBlank()) {
                    _events.tryEmit(SocketEvent.GroupUpdated(groupId))
                }
            }

            // ───────────── Call signaling ────────────────────
            s.on("call_offer") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.CallOfferReceived(
                        callId = json.optString("callId"),
                        fromUid = json.optString("from"),
                        fromUsername = json.optString("fromUsername"),
                        callType = json.optString("type"),
                        sdp = json.optString("sdp")
                    )
                )
            }

            s.on("call_answer") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.CallAnswerReceived(
                        callId = json.optString("callId"),
                        sdp = json.optString("sdp")
                    )
                )
            }

            s.on("call_end") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.CallEndReceived(
                        callId = json.optString("callId"),
                        reason = json.optString("reason")
                    )
                )
            }

            s.on("call_busy") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(SocketEvent.CallBusyReceived(callId = json.optString("callId")))
            }

            s.on("ice_candidate") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val candidateObj = json.optJSONObject("candidate") ?: return@on
                _events.tryEmit(
                    SocketEvent.IceCandidateReceived(
                        callId = json.optString("callId"),
                        sdpMid = candidateObj.optString("sdpMid").takeIf { it.isNotBlank() },
                        sdpMLineIndex = candidateObj.optInt("sdpMLineIndex"),
                        candidate = candidateObj.optString("candidate")
                    )
                )
            }

            s.connect()
            socket = s
        } catch (_: Exception) {}
    }

    fun joinRoom(roomId: String) {
        socket?.emit("join_room", roomId)
    }

    fun leaveRoom(roomId: String) {
        socket?.emit("leave_room", roomId)
    }

    fun checkPresence(uid: String) {
        socket?.emit("check_presence", uid)
    }

    fun sendMessage(
        id: String,
        receiverUid: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        isForwarded: Boolean = false,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("receiver_uid", receiverUid)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
            put("is_forwarded", isForwarded)
        }
        s.emit("send_message", arrayOf(json), Ack { args ->
            val res = args.getOrNull(0) as? JSONObject
            onAck(res?.optBoolean("success", false) ?: false)
        })
    }

    fun sendTyping(roomId: String, receiverUid: String) {
        val json = JSONObject().apply {
            put("room_id", roomId)
            put("receiver_uid", receiverUid)
        }
        socket?.emit("typing", json)
    }

    fun sendStopTyping(receiverUid: String) {
        val json = JSONObject().apply { put("receiver_uid", receiverUid) }
        socket?.emit("stop_typing", json)
    }

    // ── Group versions — receiver_uid ki jagah room_id bhejte hain, backend
    // socket/chat.js "group_" prefix se already detect karta hai ──
    fun sendGroupMessage(
        id: String,
        roomId: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        isForwarded: Boolean = false,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("room_id", roomId)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
            put("is_forwarded", isForwarded)
        }
        s.emit("send_message", arrayOf(json), Ack { args ->
            val res = args.getOrNull(0) as? JSONObject
            onAck(res?.optBoolean("success", false) ?: false)
        })
    }

    fun sendGroupTyping(roomId: String) {
        val json = JSONObject().apply { put("room_id", roomId) }
        socket?.emit("typing", json)
    }

    fun sendGroupStopTyping(roomId: String) {
        val json = JSONObject().apply { put("room_id", roomId) }
        socket?.emit("stop_typing", json)
    }

    // ─────────────────────── Call signaling ───────────────────────
    fun sendCallOffer(
        callId: String,
        toUid: String,
        callType: String, // "voice" | "video"
        sdp: String,
        onAck: (success: Boolean, error: String?) -> Unit = { _, _ -> }
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false, "Not connected")
            return
        }
        val json = JSONObject().apply {
            put("callId", callId)
            put("to", toUid)
            put("type", callType)
            put("sdp", sdp)
        }
        s.emit("call_offer", arrayOf(json), Ack { args ->
            val res = args.getOrNull(0) as? JSONObject
            onAck(res?.optBoolean("success", false) ?: false, res?.optString("error"))
        })
    }

    fun sendCallAnswer(callId: String, sdp: String, onAck: (Boolean) -> Unit = {}) {
        val json = JSONObject().apply {
            put("callId", callId)
            put("sdp", sdp)
        }
        socket?.emit("call_answer", arrayOf(json), Ack { args ->
            val res = args.getOrNull(0) as? JSONObject
            onAck(res?.optBoolean("success", false) ?: false)
        })
    }

    fun sendCallReject(callId: String) {
        socket?.emit("call_reject", JSONObject().apply { put("callId", callId) })
    }

    fun sendCallEnd(callId: String) {
        socket?.emit("call_end", JSONObject().apply { put("callId", callId) })
    }

    fun sendIceCandidate(callId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        val json = JSONObject().apply {
            put("callId", callId)
            put("candidate", JSONObject().apply {
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
                put("candidate", candidate)
            })
        }
        socket?.emit("ice_candidate", json)
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        currentToken = null
        _onlineUids.value = emptySet()
        _typingUsers.value = emptyMap()
    }
}
