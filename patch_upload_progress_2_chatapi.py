import io
path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """    @POST("chat/upload")
    suspend fun uploadMedia(
        @Header("Authorization") token: String,
        @Body request: UploadMediaRequest
    ): Response<UploadMediaResponse>

    @Multipart
    @POST("chat/upload-video")
    suspend fun uploadVideo(
        @Header("Authorization") token: String,
        @Part video: MultipartBody.Part
    ): Response<UploadMediaResponse>"""
new = """    @POST("chat/upload")
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
    ): Response<UploadMediaResponse>"""

assert content.count(old) == 1, "match failed"
content = content.replace(old, new)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched ChatApi.kt")
