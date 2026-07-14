path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path) as f:
    content = f.read()

old = '''    @PUT("groups/{roomId}/admins/{uid}")
    suspend fun setGroupAdmin(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("uid") uid: String,
        @Body request: SetAdminRequest
    ): Response<GroupActionResponse>'''

new = '''    @PUT("groups/{roomId}/admins/{uid}")
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
    ): Response<GroupActionResponse>'''

assert old in content
content = content.replace(old, new, 1)
with open(path, "w") as f:
    f.write(content)
print("ChatApi.kt patched: transferOwnership endpoint added")
