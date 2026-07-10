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

private const val CHAT_BACKEND_URL = "https://muwan-chat-backend-production.up.railway.app"

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
        val replyToId: String? = null
    ) : SocketEvent()

    data class UserOnline(val uid: String) : SocketEvent()
    data class PresenceStatus(val uid: String, val online: Boolean) : SocketEvent()

    data class Typing(val uid: String, val roomId: String) : SocketEvent()
    data class StopTyping(val uid: String) : SocketEvent()

    data class MessagesSeen(val roomId: String, val seenBy: String) : SocketEvent()

    // "Delete for Everyone" ka result — dusre user ki screen bhi isi se live update hoti hai
    data class MessageDeleted(val id: String, val roomId: String) : SocketEvent()

    // Edit Message ka result — sender ke alawa dusre device/user ki screen bhi isi se update hoti hai
    data class MessageEdited(val id: String, val roomId: String, val content: String) : SocketEvent()

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
                        replyToId = if (json.isNull("reply_to_id")) null else json.optString("reply_to_id")
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
                _events.tryEmit(
                    SocketEvent.MessagesSeen(json.optString("room_id"), json.optString("seen_by"))
                )
            }

            s.on("message_deleted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageDeleted(json.optString("id"), json.optString("room_id"))
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

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        currentToken = null
        _onlineUids.value = emptySet()
        _typingUsers.value = emptyMap()
    }
}
