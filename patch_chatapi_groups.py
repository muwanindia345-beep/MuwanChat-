import io
path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """data class GroupData(
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
)"""

new1 = """data class GroupData(
    val id: String,
    val name: String,
    val avatar: String?,
    val description: String? = "",
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
)"""

old2 = """    @GET("groups/{roomId}")
    suspend fun getGroup(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<GroupResponse>"""

new2 = """    @GET("groups/{roomId}")
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

    @DELETE("groups/{roomId}")
    suspend fun deleteGroup(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<GroupActionResponse>"""

for old, new, label in [(old1, new1, "data classes"), (old2, new2, "retrofit methods")]:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched ChatApi.kt")
