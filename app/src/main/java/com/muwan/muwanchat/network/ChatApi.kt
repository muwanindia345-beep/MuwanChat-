package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

data class ConversationItem(
    val room_id: String,
    val uid: String,
    val username: String,
    val avatar: String?,
    val lastMessage: String,
    val lastTime: String,
    val isOnline: Boolean = false,
    val unreadCount: Int = 0,
    val lastSenderUid: String = ""
)

data class ConversationsResponse(
    val conversations: List<ConversationItem>
)

data class MessagesResponse(
    val messages: List<MessageItem>
)

data class MessageItem(
    val id: String,
    val sender_uid: String,
    val receiver_uid: String,
    val room_id: String,
    val content: String,
    val type: String,
    val seen: Int,
    val created_at: String,
    val username: String?,
    val avatar: String?
)

data class SendMessageRequest(
    val receiver_uid: String,
    val content: String,
    val type: String = "text"
)

data class SendMessageResponse(
    val success: Boolean,
    val message: MessageItem?
)

interface ChatApi {
    @GET("chat/conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): Response<ConversationsResponse>

    @GET("chat/messages/{roomId}")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<MessagesResponse>

    @POST("chat/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: SendMessageRequest
    ): Response<SendMessageResponse>

    @PUT("chat/seen/{roomId}")
    suspend fun markSeen(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<Map<String, Boolean>>

    @DELETE("chat/message/{id}")
    suspend fun deleteMsgById(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    @DELETE("chat/conversation/{roomId}")
    suspend fun deleteConversation(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<Map<String, Boolean>>
}
