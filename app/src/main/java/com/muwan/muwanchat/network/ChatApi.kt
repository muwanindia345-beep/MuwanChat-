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
    val isTyping: Boolean = false,
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0
)

data class ConversationsResponse(
    val conversations: List<ConversationItem>
)

data class MessagesResponse(
    val messages: List<MessageItem>
)

data class MessageReaction(
    val emoji: String,
    val userIds: List<String>
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
    val reply_to_id: String? = null,
    val deleted: Boolean = false,
    val edited: Boolean = false,
    val reactions: List<MessageReaction>? = null
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

data class EditMessageRequest(
    val content: String
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

data class DeletedMessagesResponse(
    val ids: List<String>
)

data class ReactRequest(
    val emoji: String
)

data class ReactResponse(
    val success: Boolean,
    val reactions: List<MessageReaction>? = null
)

data class WallpaperRequest(
    val type: String,
    val value: String
)

data class WallpaperData(
    val type: String,
    val value: String
)

data class WallpaperResponse(
    val wallpaper: WallpaperData?
)

data class GroupMemberProfile(
    val uid: String,
    val username: String,
    val avatar: String?,
    val isAdmin: Boolean,
    val isOwner: Boolean
)

data class GroupData(
    val id: String,
    val name: String,
    val avatar: String?,
    val owner: String,
    val admins: List<String>,
    val members: List<String>,
    val createdAt: String,
    val memberProfiles: List<GroupMemberProfile> = emptyList()
)

data class GroupResponse(
    val group: GroupData?
)

data class CreateGroupRequest(
    val name: String,
    val avatar: String?,
    val memberUids: List<String>
)

data class CreateGroupResponse(
    val success: Boolean,
    val group: GroupData?
)

interface ChatApi {
    @POST("groups/create")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): Response<CreateGroupResponse>

    @GET("groups/{roomId}")
    suspend fun getGroup(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<GroupResponse>

    @GET("chat/conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): Response<ConversationsResponse>

    @GET("chat/messages/{roomId}")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<MessagesResponse>

    // Reconciliation: is room me abhi tak kaunse messages "delete for everyone" ho chuke hain
    @GET("chat/deleted/{roomId}")
    suspend fun getDeletedMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<DeletedMessagesResponse>

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

    // roomId ab path me jaata hai — backend ab O(1) direct key lookup karta hai
    // (pehle poori app ke messages scan hote the, isliye delete/edit slow tha)
    @PUT("chat/message/{roomId}/{id}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String,
        @Body request: EditMessageRequest
    ): Response<SendMessageResponse>

    @DELETE("chat/message/{roomId}/{id}")
    suspend fun deleteMsgById(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    @POST("chat/message/{roomId}/{id}/react")
    suspend fun reactToMessage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String,
        @Body request: ReactRequest
    ): Response<ReactResponse>

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

    @POST("chat/wallpaper/{roomId}")
    suspend fun setWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: WallpaperRequest
    ): Response<Map<String, Boolean>>

    @GET("chat/wallpaper/{roomId}")
    suspend fun getWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<WallpaperResponse>

    @DELETE("chat/wallpaper/{roomId}")
    suspend fun deleteWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<Map<String, Boolean>>
}
