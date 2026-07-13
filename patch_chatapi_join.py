import re

path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path) as f:
    content = f.read()

marker = "data class InviteRegenerateResponse("
new_models = '''data class JoinPreviewData(
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

''' + marker

assert marker in content, "marker not found, ChatApi.kt structure changed"
content = content.replace(marker, new_models, 1)

method_marker = '    @GET("chat/{roomId}/mute")'
new_methods = '''    @GET("groups/join/{code}")
    suspend fun getJoinPreview(
        @Header("Authorization") token: String,
        @Path("code") code: String
    ): Response<JoinPreviewResponse>

    @POST("groups/join/{code}")
    suspend fun joinViaCode(
        @Header("Authorization") token: String,
        @Path("code") code: String
    ): Response<JoinActionResponse>

''' + method_marker

assert method_marker in content, "method marker not found"
content = content.replace(method_marker, new_methods, 1)

with open(path, "w") as f:
    f.write(content)

print("ChatApi.kt patched: JoinPreviewData/JoinPreviewResponse/JoinActionResponse + getJoinPreview/joinViaCode added")
