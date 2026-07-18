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
    val onlineCount: Int = 0,
    val isRemoved: Boolean = false,
    val removedByUsername: String? = null
)

data class RemovalStatusResponse(
    val removed: Boolean,
    val removedByUsername: String? = null
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

data class LinkPreview(
    val url: String?,
    val title: String?,
    val description: String?,
    val image: String?
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
    val reactions: List<MessageReaction>? = null,
    val link_preview: LinkPreview? = null,
    val is_forwarded: Boolean = false
)

data class SendMessageRequest(
    val receiver_uid: String,
    val content: String,
    val type: String = "text",
    val file_name: String? = null,
    val mime_type: String? = null,
    val reply_to_id: String? = null,
    val is_forwarded: Boolean = false
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
    val description: String? = "",
    val owner: String,
    val admins: List<String>,
    val members: List<String>,
    val createdAt: String,
    val memberProfiles: List<GroupMemberProfile> = emptyList(),
    val inviteCode: String? = null,
    val joinApprovalRequired: Boolean = false,
    val membersCanAdd: Boolean = false,
    val onlyAdminsCanSend: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val pendingRequests: List<JoinRequestEntry> = emptyList()
)

data class JoinRequestEntry(
    val uid: String,
    val username: String,
    val avatar: String? = null,
    val requestedAt: String,
    val source: String, // "link" | "invited"
    val invitedBy: String? = null,
    val invitedByUsername: String? = null
)

data class JoinRequestsResponse(
    val requests: List<JoinRequestEntry>
)

data class GroupSettingsRequest(
    val joinApprovalRequired: Boolean? = null,
    val membersCanAdd: Boolean? = null,
    val onlyAdminsCanSend: Boolean? = null,
    val readReceiptsEnabled: Boolean? = null
)

data class JoinPreviewData(
    val id: String,
    val name: String,
    val avatar: String?,
    val description: String? = "",
    val memberCount: Int,
    val joinApprovalRequired: Boolean
)

data class JoinPreviewResponse(
    val preview: JoinPreviewData?,
    val alreadyMember: Boolean = false,
    val alreadyRequested: Boolean = false
)

data class JoinActionResponse(
    val success: Boolean,
    val joined: Boolean = false,
    val pending: Boolean = false,
    val group: GroupData? = null
)

data class InviteRegenerateResponse(
    val success: Boolean,
    val inviteCode: String?
)

data class MuteRequest(
    val muted: Boolean
)

data class MuteResponse(
    val success: Boolean,
    val muted: Boolean
)

data class MuteStatusResponse(
    val muted: Boolean
)

data class GroupResponse(
    val group: GroupData?
)

data class CreateGroupRequest(
    val name: String,
    val avatar: String?,
    val description: String? = null,
    val memberUids: List<String>
)

data class CreateGroupResponse(
    val success: Boolean,
    val group: GroupData?
)

data class EditGroupRequest(
    val name: String? = null,
    val avatar: String? = null,
    val description: String? = null
)

data class AddMembersRequest(
    val memberUids: List<String>
)

data class SetAdminRequest(
    val makeAdmin: Boolean
)

data class GroupActionResponse(
    val success: Boolean,
    val group: GroupData? = null
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

    @PUT("groups/{roomId}")
    suspend fun editGroup(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: EditGroupRequest
    ): Response<GroupActionResponse>

    @POST("groups/{roomId}/members")
    suspend fun addGroupMembers(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: AddMembersRequest
    ): Response<GroupActionResponse>

    @DELETE("groups/{roomId}/members/{uid}")
    suspend fun removeGroupMember(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String
    ): Response<GroupActionResponse>

    @PUT("groups/{roomId}/admins/{uid}")
    suspend fun setGroupAdmin(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String,
        @Body request: SetAdminRequest
    ): Response<GroupActionResponse>

    @PUT("groups/{roomId}/owner/{uid}")
    suspend fun transferOwnership(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String
    ): Response<GroupActionResponse>

    @GET("groups/{roomId}/removal-status")
    suspend fun getRemovalStatus(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<RemovalStatusResponse>

    @DELETE("groups/{roomId}")
    suspend fun deleteGroup(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<GroupActionResponse>

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
        @Body request: UploadMediaRequest,
        @Header("X-Upload-Track-Id") uploadId: String? = null
    ): Response<UploadMediaResponse>

    @Multipart
    @POST("chat/upload-video")
    suspend fun uploadVideo(
        @Header("Authorization") token: String,
        @Part video: MultipartBody.Part,
        @Header("X-Upload-Track-Id") uploadId: String? = null
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

    @GET("groups/{roomId}/join-requests")
    suspend fun getJoinRequests(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<JoinRequestsResponse>

    @POST("groups/{roomId}/join-requests/{uid}/approve")
    suspend fun approveJoinRequest(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String
    ): Response<GroupActionResponse>

    @POST("groups/{roomId}/join-requests/{uid}/reject")
    suspend fun rejectJoinRequest(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String
    ): Response<GroupActionResponse>

    @PUT("groups/{roomId}/settings")
    suspend fun updateGroupSettings(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: GroupSettingsRequest
    ): Response<GroupActionResponse>

    @POST("groups/{roomId}/invite/regenerate")
    suspend fun regenerateInvite(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<InviteRegenerateResponse>

    @GET("groups/join/{code}")
    suspend fun getJoinPreview(
        @Header("Authorization") token: String,
        @Path("code") code: String
    ): Response<JoinPreviewResponse>

    @POST("groups/join/{code}")
    suspend fun joinViaCode(
        @Header("Authorization") token: String,
        @Path("code") code: String
    ): Response<JoinActionResponse>

    @GET("chat/{roomId}/mute")
    suspend fun getMuteStatus(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<MuteStatusResponse>

    @PUT("chat/{roomId}/mute")
    suspend fun muteRoom(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Body request: MuteRequest
    ): Response<MuteResponse>
}
