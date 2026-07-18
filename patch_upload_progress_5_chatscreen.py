import io
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

# 1. imports
old = """import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import com.muwan.muwanchat.network.EditMessageRequest
import com.muwan.muwanchat.network.ReactRequest
import com.google.gson.Gson
import com.muwan.muwanchat.network.UploadMediaRequest"""
new = """import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.data.UploadProgressTracker
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import com.muwan.muwanchat.network.EditMessageRequest
import com.muwan.muwanchat.network.ReactRequest
import com.google.gson.Gson
import com.muwan.muwanchat.network.UploadMediaRequest
import com.muwan.muwanchat.util.isNetworkAvailable"""
assert content.count(old) == 1, "match failed: imports"
content = content.replace(old, new)

# 2. remove now-unused screen-wide uploading flag
old = "    var uploadingMedia by remember { mutableStateOf(false) }\n"
assert content.count(old) == 1, "match failed: uploadingMedia var"
content = content.replace(old, "")

# 3. remove old global full-width progress bar (replaced by per-message layer)
old = """        AnimatedVisibility(visible = uploadingMedia) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DarkAccent)
        }

        AnimatedVisibility(visible = replyTo != null) {"""
new = """        AnimatedVisibility(visible = replyTo != null) {"""
assert content.count(old) == 1, "match failed: global progress bar"
content = content.replace(old, new)

# 4. all remaining "{ uploadingMedia = it }" callbacks become no-ops
count_before = content.count("{ uploadingMedia = it }")
assert count_before > 0, "match failed: no uploadingMedia callbacks found"
content = content.replace("{ uploadingMedia = it }", "{}")

# 5. uploadMediaMessage — instant local preview + real progress + no-network guard
old = """private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image" && !skipCompression) compressImageToBase64(context, uri)
            else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP) to (context.contentResolver.getType(uri) ?: "application/octet-stream")
            }
        }
        val filename = withContext(Dispatchers.IO) { getFileName(context, uri) }
        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = category)
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                val id = UUID.randomUUID().toString()
                ChatRepository.recordMessage(
                    db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
                    content = body.url, type = category, createdAt = nowIso(), myUid = myUid,
                    otherUsername = receiverUsername, status = "PENDING",
                    fileName = body.file_name ?: filename, mimeType = body.mime_type ?: mime
                )
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, category, body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, category, body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            }
      }
    } catch (_: Exception) {
    } finally {
        setUploading(false)
    }
}"""
new = """private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = withContext(Dispatchers.IO) { getFileName(context, uri) }
    val guessedMime = context.contentResolver.getType(uri) ?: "application/octet-stream"

    // Network hi nahi hai — seedha FAILED, koi "uploading" progress layer nahi dikhega
    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
            content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
            otherUsername = receiverUsername, status = "FAILED",
            fileName = filename, mimeType = guessedMime
        )
        return
    }

    // Bubble turant local file se dikhega, upload background me chalega
    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
        content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
        otherUsername = receiverUsername, status = "UPLOADING",
        fileName = filename, mimeType = guessedMime
    )
    UploadProgressTracker.start(id)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image" && !skipCompression) compressImageToBase64(context, uri)
            else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP) to guessedMime
            }
        }
        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = category),
            uploadId = id
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                db.messageDao().updateMediaContent(id, body.url, "PENDING")
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, category, body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, category, body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            } ?: db.messageDao().updateStatus(id, "FAILED")
        } else {
            db.messageDao().updateStatus(id, "FAILED")
        }
    } catch (_: Exception) {
        db.messageDao().updateStatus(id, "FAILED")
    } finally {
        UploadProgressTracker.clear(id)
        setUploading(false)
    }
}"""
assert content.count(old) == 1, "match failed: uploadMediaMessage"
content = content.replace(old, new)

# 6. uploadVideoMessage — same pattern for multipart video upload
old = """private suspend fun uploadVideoMessage(
    context: android.content.Context,
    uri: Uri,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val filename = withContext(Dispatchers.IO) { getFileName(context, uri) }
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }
        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("video", filename, requestBody)
        val res = RetrofitClient.chatApi.uploadVideo("Bearer $token", part)
        if (res.isSuccessful) {
            res.body()?.let { body ->
                val id = UUID.randomUUID().toString()
                ChatRepository.recordMessage(
                    db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
                    content = body.url, type = "video", createdAt = nowIso(), myUid = myUid,
                    otherUsername = receiverUsername, status = "PENDING",
                    fileName = body.file_name ?: filename, mimeType = body.mime_type ?: mime
                )
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, "video", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, "video", body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        setUploading(false)
    }
}"""
new = """private suspend fun uploadVideoMessage(
    context: android.content.Context,
    uri: Uri,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = withContext(Dispatchers.IO) { getFileName(context, uri) }
    val mime = context.contentResolver.getType(uri) ?: "video/mp4"

    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
            content = uri.toString(), type = "video", createdAt = nowIso(), myUid = myUid,
            otherUsername = receiverUsername, status = "FAILED",
            fileName = filename, mimeType = mime
        )
        return
    }

    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
        content = uri.toString(), type = "video", createdAt = nowIso(), myUid = myUid,
        otherUsername = receiverUsername, status = "UPLOADING",
        fileName = filename, mimeType = mime
    )
    UploadProgressTracker.start(id)
    try {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }
        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("video", filename, requestBody)
        val res = RetrofitClient.chatApi.uploadVideo("Bearer $token", part, uploadId = id)
        if (res.isSuccessful) {
            res.body()?.let { body ->
                db.messageDao().updateMediaContent(id, body.url, "PENDING")
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, "video", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, "video", body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            } ?: db.messageDao().updateStatus(id, "FAILED")
        } else {
            db.messageDao().updateStatus(id, "FAILED")
        }
    } catch (_: Exception) {
        db.messageDao().updateStatus(id, "FAILED")
    } finally {
        UploadProgressTracker.clear(id)
        setUploading(false)
    }
}"""
assert content.count(old) == 1, "match failed: uploadVideoMessage"
content = content.replace(old, new)

# 7. uploadAudioMessage — same pattern for recorded voice notes
old = """private suspend fun uploadAudioMessage(
    context: android.content.Context,
    file: java.io.File,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val base64Data = withContext(Dispatchers.IO) {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }
        val filename = file.name
        val mime = "audio/mp4"
        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = "audio")
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                val id = UUID.randomUUID().toString()
                ChatRepository.recordMessage(
                    db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
                    content = body.url, type = "audio", createdAt = nowIso(), myUid = myUid,
                    otherUsername = receiverUsername, status = "PENDING",
                    fileName = body.file_name ?: filename, mimeType = body.mime_type ?: mime
                )
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, "audio", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, "audio", body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        setUploading(false)
        file.delete()
    }
}"""
new = """private suspend fun uploadAudioMessage(
    context: android.content.Context,
    file: java.io.File,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = file.name
    val mime = "audio/mp4"
    val localUri = Uri.fromFile(file).toString()

    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
            content = localUri, type = "audio", createdAt = nowIso(), myUid = myUid,
            otherUsername = receiverUsername, status = "FAILED",
            fileName = filename, mimeType = mime
        )
        return
    }

    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
        content = localUri, type = "audio", createdAt = nowIso(), myUid = myUid,
        otherUsername = receiverUsername, status = "UPLOADING",
        fileName = filename, mimeType = mime
    )
    UploadProgressTracker.start(id)
    try {
        val base64Data = withContext(Dispatchers.IO) {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }
        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = "audio"),
            uploadId = id
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                db.messageDao().updateMediaContent(id, body.url, "PENDING")
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(id, receiverUid, body.url, "audio", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    try {
                        val sendRes = RetrofitClient.chatApi.sendMessage(
                            "Bearer $token",
                            SendMessageRequest(receiverUid, body.url, "audio", body.file_name ?: filename, body.mime_type ?: mime)
                        )
                        db.messageDao().updateStatus(id, if (sendRes.isSuccessful) "SENT" else "FAILED")
                    } catch (_: Exception) {
                        db.messageDao().updateStatus(id, "FAILED")
                    }
                }
            } ?: db.messageDao().updateStatus(id, "FAILED")
        } else {
            db.messageDao().updateStatus(id, "FAILED")
        }
    } catch (_: Exception) {
        db.messageDao().updateStatus(id, "FAILED")
    } finally {
        UploadProgressTracker.clear(id)
        setUploading(false)
        file.delete()
    }
}"""
assert content.count(old) == 1, "match failed: uploadAudioMessage"
content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched ChatScreen.kt")
