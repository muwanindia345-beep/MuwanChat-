package com.muwan.muwanchat.network

import okhttp3.MultipartBody
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
    val lastSenderUid: String = "",
    val isTyping: Boolean = false
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
    val avatar: String?,
    val file_name: String? = null,
    val mime_type: String? = null,
    val reply_to_id: String? = null
)

data class SendMessageRequest(
    val receiver_uid: String,
    val content: String,
    val type: String = "text",
    val file_name: String? = null,
    val mime_type: String? = null,
    val reply_to_id: String? = null
)

data class SendMessageResponse(
    val success: Boolean,
    val message: MessageItem?
)

data class UploadMediaRequest(
    val filename: String,
    val mime_type: String,
    val data: String,   // base64
    val category: String // image | document
)

data class UploadMediaResponse(
    val success: Boolean,
    val url: String,
    val file_name: String?,
    val mime_type: String?
)

data class LinkPreviewResponse(
    val title: String?,
    val description: String?,
    val image: String?,
    val site_name: String?,
    val url: String?
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

    @POST("chat/upload")
    suspend fun uploadMedia(
        @Header("Authorization") token: String,
        @Body request: UploadMediaRequest
    ): Response<UploadMediaResponse>

    @Multipart
    @POST("chat/upload-video")
    suspend fun uploadVideo(
        @Header("Authorization") token: String,
        @Part video: MultipartBody.Part
    ): Response<UploadMediaResponse>

    @GET("chat/link-preview")
    suspend fun linkPreview(
        @Header("Authorization") token: String,
        @Query("url") url: String
    ): Response<LinkPreviewResponse>
}
