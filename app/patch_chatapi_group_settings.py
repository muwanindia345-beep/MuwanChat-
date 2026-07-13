path = "src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path, "r") as f:
    content = f.read()

old_groupdata = """data class GroupData(
    val id: String,
    val name: String,
    val avatar: String?,
    val description: String? = "",
    val owner: String,
    val admins: List<String>,
    val members: List<String>,
    val createdAt: String,
    val memberProfiles: List<GroupMemberProfile> = emptyList()
)"""

new_groupdata = """data class GroupData(
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
)"""

assert old_groupdata in content, "GroupData anchor not found"
content = content.replace(old_groupdata, new_groupdata)

old_tail = """    @DELETE("chat/wallpaper/{roomId}")
    suspend fun deleteWallpaper(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<Map<String, Boolean>>
}"""

new_tail = """    @DELETE("chat/wallpaper/{roomId}")
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
}"""

assert old_tail in content, "interface tail anchor not found"
content = content.replace(old_tail, new_tail)

with open(path, "w") as f:
    f.write(content)
print("ChatApi.kt patched successfully")
