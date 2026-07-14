path = "src/main/java/com/muwan/muwanchat/network/RequestsApi.kt"
with open(path, "r") as f:
    content = f.read()

old_tail = '''    @GET("requests/accepted")
    suspend fun getAccepted(
        @Header("Authorization") token: String
    ): Response<UsersSearchResponse>
}'''

new_tail = '''    @GET("requests/accepted")
    suspend fun getAccepted(
        @Header("Authorization") token: String
    ): Response<UsersSearchResponse>

    // Permanent unfriend -- "Accepted Users" settings screen se call hota hai.
    // Backend accepted request record delete karta hai + shared room data saaf
    // karta hai, aur otherUid ko "connection_removed" socket event emit karta hai.
    @DELETE("requests/accepted/{otherUid}")
    suspend fun removeConnection(
        @Header("Authorization") token: String,
        @Path("otherUid") otherUid: String
    ): Response<SimpleResponse>
}'''

assert old_tail in content, "RequestsApi interface tail anchor not found"
content = content.replace(old_tail, new_tail, 1)

with open(path, "w") as f:
    f.write(content)
print("RequestsApi.kt patched: DELETE requests/accepted/{otherUid} added")
