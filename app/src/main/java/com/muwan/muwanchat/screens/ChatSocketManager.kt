package com.muwan.muwanchat.screens

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

private const val CHAT_BACKEND_URL = "https://muwan-chat-backend-production.up.railway.app"

class ChatSocketManager(
    private val token: String,
    private val roomId: String,
    private val receiverUid: String,
    private val onNewMessage: (id: String, senderUid: String, content: String, createdAt: String) -> Unit,
    private val onPresenceChange: (uid: String, online: Boolean) -> Unit
) {
    var socket: Socket? = null
        private set

    fun connect() {
        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                transports = arrayOf("websocket")
            }
            val s = IO.socket(CHAT_BACKEND_URL, opts)

            s.on(Socket.EVENT_CONNECT) {
                s.emit("join_room", roomId)
                // Ask backend for current presence of the other user
                s.emit("check_presence", receiverUid)
            }

            s.on("new_message") { args ->
                val json = args[0] as? JSONObject ?: return@on
                onNewMessage(
                    json.optString("id"),
                    json.optString("sender_uid"),
                    json.optString("content"),
                    json.optString("created_at")
                )
            }

            // Backend should emit: { "uid": "xxxx", "online": true/false }
            s.on("user_online") { args ->
                val json = args[0] as? JSONObject ?: return@on
                val uid = json.optString("uid")
                if (uid == receiverUid) onPresenceChange(uid, true)
            }

            s.on("user_offline") { args ->
                val json = args[0] as? JSONObject ?: return@on
                val uid = json.optString("uid")
                if (uid == receiverUid) onPresenceChange(uid, false)
            }

            // Optional: backend can respond to check_presence with initial state
            s.on("presence_status") { args ->
                val json = args[0] as? JSONObject ?: return@on
                val uid = json.optString("uid")
                val online = json.optBoolean("online", false)
                if (uid == receiverUid) onPresenceChange(uid, online)
            }

            s.connect()
            socket = s
        } catch (_: Exception) {}
    }

    fun sendMessage(content: String) {
        socket?.let { s ->
            val json = JSONObject().apply {
                put("receiver_uid", receiverUid)
                put("content", content)
                put("type", "text")
            }
            s.emit("send_message", json)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
