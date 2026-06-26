package com.muwan.muwanchat.network

import retrofit2.Response
import retrofit2.http.*

data class ConversationItem(
    val id: Int,
    val sender_uid: String,
    val receiver_uid: String,
    val room_id: String,
    val content: String,
    val type: String,
    val seen: Int,
    val created_at: String,
    val username: String,
    val avatar: String?
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
    val username: String,
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
}
