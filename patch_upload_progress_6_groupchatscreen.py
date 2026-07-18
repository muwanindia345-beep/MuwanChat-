import io
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
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

# 2. uploadGroupMediaMessage
old = """private suspend fun uploadGroupMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image" && !skipCompression) groupCompressImageToBase64(context, uri)
            else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP) to (context.contentResolver.getType(uri) ?: "application/octet-stream")
            }
        }
        val filename = withContext(Dispatchers.IO) { groupChatGetFileName(context, uri) }
        val res = RetrofitClient.chatApi.uploadMedia(
            "Bearer $token",
            UploadMediaRequest(filename = filename, mime_type = mime, data = base64Data, category = category)
        )
        if (res.isSuccessful) {
            res.body()?.let { body ->
                val id = UUID.randomUUID().toString()
                ChatRepository.recordMessage(
                    db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
                    content = body.url, type = category, createdAt = nowIso(), myUid = myUid,
                    otherUsername = groupName, status = "PENDING",
                    fileName = body.file_name ?: filename, mimeType = body.mime_type ?: mime
                )
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, category, body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    // Group ke liye REST /send abhi backend mein support nahi karta (socket-only) —
                    // offline hone par message FAILED reh jayega, reconnect hote hi retry se send hoga
                    db.messageDao().updateStatus(id, "FAILED")
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        setUploading(false)
    }
}"""
new = """private suspend fun uploadGroupMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = withContext(Dispatchers.IO) { groupChatGetFileName(context, uri) }
    val guessedMime = context.contentResolver.getType(uri) ?: "application/octet-stream"

    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
            content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
            otherUsername = groupName, status = "FAILED",
            fileName = filename, mimeType = guessedMime
        )
        return
    }

    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
        content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
        otherUsername = groupName, status = "UPLOADING",
        fileName = filename, mimeType = guessedMime
    )
    UploadProgressTracker.start(id)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image" && !skipCompression) groupCompressImageToBase64(context, uri)
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
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, category, body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    // Group ke liye REST /send abhi backend mein support nahi karta (socket-only) —
                    // offline hone par message FAILED reh jayega, reconnect hote hi retry se send hoga
                    db.messageDao().updateStatus(id, "FAILED")
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
assert content.count(old) == 1, "match failed: uploadGroupMediaMessage"
content = content.replace(old, new)

# 3. uploadGroupVideoMessage
old = """private suspend fun uploadGroupVideoMessage(
    context: android.content.Context,
    uri: Uri,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val filename = withContext(Dispatchers.IO) { groupChatGetFileName(context, uri) }
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
                    db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
                    content = body.url, type = "video", createdAt = nowIso(), myUid = myUid,
                    otherUsername = groupName, status = "PENDING",
                    fileName = body.file_name ?: filename, mimeType = body.mime_type ?: mime
                )
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, "video", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    db.messageDao().updateStatus(id, "FAILED")
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        setUploading(false)
    }
}"""
new = """private suspend fun uploadGroupVideoMessage(
    context: android.content.Context,
    uri: Uri,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = withContext(Dispatchers.IO) { groupChatGetFileName(context, uri) }
    val mime = context.contentResolver.getType(uri) ?: "video/mp4"

    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
            content = uri.toString(), type = "video", createdAt = nowIso(), myUid = myUid,
            otherUsername = groupName, status = "FAILED",
            fileName = filename, mimeType = mime
        )
        return
    }

    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
        content = uri.toString(), type = "video", createdAt = nowIso(), myUid = myUid,
        otherUsername = groupName, status = "UPLOADING",
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
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, "video", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    db.messageDao().updateStatus(id, "FAILED")
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
assert content.count(old) == 1, "match failed: uploadGroupVideoMessage"
content = content.replace(old, new)

# 4. uploadGroupAudioMessage
old = """private suspend fun uploadGroupAudioMessage(
    context: android.content.Context,
    file: java.io.File,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
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
                    db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
                    content = body.url, type = "audio", createdAt = nowIso(), myUid = myUid,
                    otherUsername = groupName, status = "PENDING",
                    fileName = body.file_name ?: filename, mimeType = body.mime_type ?: mime
                )
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, "audio", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    db.messageDao().updateStatus(id, "FAILED")
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        setUploading(false)
        file.delete()
    }
}"""
new = """private suspend fun uploadGroupAudioMessage(
    context: android.content.Context,
    file: java.io.File,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = file.name
    val mime = "audio/mp4"
    val localUri = Uri.fromFile(file).toString()

    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
            content = localUri, type = "audio", createdAt = nowIso(), myUid = myUid,
            otherUsername = groupName, status = "FAILED",
            fileName = filename, mimeType = mime
        )
        return
    }

    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
        content = localUri, type = "audio", createdAt = nowIso(), myUid = myUid,
        otherUsername = groupName, status = "UPLOADING",
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
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, "audio", body.file_name ?: filename, body.mime_type ?: mime) { success ->
                        kotlinx.coroutines.GlobalScope.launch {
                            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                        }
                    }
                } else {
                    db.messageDao().updateStatus(id, "FAILED")
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
assert content.count(old) == 1, "match failed: uploadGroupAudioMessage"
content = content.replace(old, new)

# 5. remove now-unused screen-wide uploading flag
old = "    var uploadingMedia by remember { mutableStateOf(false) }\n"
assert content.count(old) == 1, "match failed: uploadingMedia var"
content = content.replace(old, "")

# 6. remove old global full-width progress bar
old = """        AnimatedVisibility(visible = uploadingMedia) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DarkAccent)
        }
"""
assert content.count(old) == 1, "match failed: global progress bar"
content = content.replace(old, "")

# 7. remaining callbacks become no-ops
count_before = content.count("{ uploadingMedia = it }")
assert count_before > 0, "match failed: no uploadingMedia callbacks found"
content = content.replace("{ uploadingMedia = it }", "{}")

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched GroupChatScreen.kt")
