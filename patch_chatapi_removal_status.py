path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path) as f:
    content = f.read()

old = '''    @PUT("groups/{roomId}/owner/{uid}")
    suspend fun transferOwnership(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String
    ): Response<GroupActionResponse>'''

new = '''    @PUT("groups/{roomId}/owner/{uid}")
    suspend fun transferOwnership(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String
    ): Response<GroupActionResponse>

    @GET("groups/{roomId}/removal-status")
    suspend fun getRemovalStatus(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<RemovalStatusResponse>'''

assert old in content
content = content.replace(old, new, 1)

# response model add karo, ConversationsResponse ke paas
old2 = "data class ConversationsResponse("
new2 = '''data class RemovalStatusResponse(
    val removed: Boolean,
    val removedByUsername: String? = null
)

data class ConversationsResponse('''
assert old2 in content
content = content.replace(old2, new2, 1)

with open(path, "w") as f:
    f.write(content)
print("ChatApi.kt patched: getRemovalStatus endpoint + RemovalStatusResponse added")
