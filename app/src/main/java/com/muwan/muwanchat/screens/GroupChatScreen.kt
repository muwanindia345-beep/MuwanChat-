package com.muwan.muwanchat.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AudioRecorder
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.ChatWallpaperEntity
import com.muwan.muwanchat.data.DocumentOpener
import com.muwan.muwanchat.data.DeletedMessageEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.data.UploadProgressTracker
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.SendMessageRequest
import com.muwan.muwanchat.network.EditMessageRequest
import com.muwan.muwanchat.network.ReactRequest
import com.google.gson.Gson
import com.muwan.muwanchat.network.UploadMediaRequest
import com.muwan.muwanchat.util.isNetworkAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

// ─── Helpers: file info + image compression (ChatScreen.kt se duplicate —
// wo private hain us file mein, yahan se access nahi ho sakte) ─────────────
private fun createGroupCameraCaptureUri(context: android.content.Context): Uri {
    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
}

private fun createGroupCameraVideoCaptureUri(context: android.content.Context): Uri {
    val videosDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val videoFile = File(videosDir, "VID_${System.currentTimeMillis()}.mp4")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
}

private fun groupChatGetFileName(context: android.content.Context, uri: Uri): String {
    var name = "file"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
    }
    return name
}

private fun groupCompressImageToBase64(context: android.content.Context, uri: Uri): Pair<String, String> {
    val input = context.contentResolver.openInputStream(uri)
    val original = BitmapFactory.decodeStream(input)
    input?.close()
    val maxDim = 1280
    val bitmap = if (original.width > maxDim || original.height > maxDim) {
        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
        Bitmap.createScaledBitmap(original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true)
    } else original
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP) to "image/jpeg"
}

// ─── Upload + send: photo/document (base64 → MuwanBox) — GROUP version ────
// receiverUid ki jagah groupId jaata hai (MessageEntity/ConversationEntity
// mein storage ke liye hi hai, query roomId se hoti hai — functionally safe)
private suspend fun uploadGroupMediaMessage(
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
    displayType: String = category,
    setUploading: (Boolean) -> Unit
) {
    val id = UUID.randomUUID().toString()
    val filename = withContext(Dispatchers.IO) { groupChatGetFileName(context, uri) }
    val guessedMime = context.contentResolver.getType(uri) ?: "application/octet-stream"

    if (!isNetworkAvailable(context)) {
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
            content = uri.toString(), type = displayType, createdAt = nowIso(), myUid = myUid,
            otherUsername = groupName, status = "FAILED",
            fileName = filename, mimeType = guessedMime
        )
        return
    }

    ChatRepository.recordMessage(
        db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = groupId,
        content = uri.toString(), type = displayType, createdAt = nowIso(), myUid = myUid,
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
                    AppSocketManager.sendGroupMessage(id, roomId, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime) { success ->
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
}

// ─── Upload + send: video (multipart → Cloudinary) — GROUP version ────────
private suspend fun uploadGroupVideoMessage(
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
}

// ─── Upload + send: voice message (base64 → MuwanBox, category=audio) — GROUP version ─
private suspend fun uploadGroupAudioMessage(
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
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    navController: NavController,
    groupId: String,
    groupName: String,
    groupAvatar: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val currentWallpaper by db.chatWallpaperDao().observeByRoomId(groupId).collectAsState(initial = null)

    val messageEntities by db.messageDao().observeMessages(groupId).collectAsState(initial = emptyList())
    val conversationEntity by db.conversationDao().observeByRoomId(groupId).collectAsState(initial = null)

    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }

    // Member uid -> username map — group info se ek baar load hota hai, typing
    // display aur future GroupInfoScreen navigation ke liye use hoga
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberAvatars by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var memberCount by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<GroupData?>(null) }
    var showAdminsSheet by remember { mutableStateOf(false) }
    val isAdmin = group?.admins?.contains(myUid) == true
    val onlineUidsForAdmins by AppSocketManager.onlineUids.collectAsState()

    // Group mein multiple log type kar sakte hain — shared typingUsers map
    // (uid -> roomId) se isi room ke typers nikal lete hain, apna khud ka uid chhodke
    val sharedTypingUsers by AppSocketManager.typingUsers.collectAsState()
    val typingUids = remember(sharedTypingUsers, groupId, myUid) {
        sharedTypingUsers.filterValues { it == groupId }.keys.filter { it != myUid }.toSet()
    }
    var typingJob by remember { mutableStateOf<Job?>(null) }

    val messages = remember(messageEntities, myUid) {
        val base = messageEntities.map { it.toChatMessage(myUid) }
        val byId = base.associateBy { it.id }
        base.map { msg ->
            val reply = msg.replyToId?.let { byId[it] }
            if (reply != null) msg.copy(replyTo = reply) else msg
        }
    }

    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    var comingSoonFeature by remember { mutableStateOf<String?>(null) }
    var fullscreenImage by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenVideo by remember { mutableStateOf<ChatMessage?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder(context) }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showVoiceRecorder = true
        else Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }
    var showCustomEmojiField by remember { mutableStateOf(false) }
    val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    // ── Ye sab functions ChatScreen jaisa hi hain (receiverUid kahin use nahi
    // hota inme, roomId/db/myToken se kaam chalta hai) — as-is reuse ──
    val reactionInFlight = remember { mutableStateOf(setOf<String>()) }

    fun sendReaction(messageId: String, emoji: String) {
        if (reactionInFlight.value.contains(messageId)) return
        reactionInFlight.value = reactionInFlight.value + messageId

        val current = messages.firstOrNull { it.id == messageId }?.reactions ?: emptyList()
        val hadSameEmoji = current.any { it.emoji == emoji && it.userIds.contains(myUid) }
        val optimistic = current
            .map { com.muwan.muwanchat.network.MessageReaction(it.emoji, it.userIds.filter { u -> u != myUid }) }
            .filter { it.userIds.isNotEmpty() }
            .toMutableList()
        if (!hadSameEmoji) {
            val groupIdx = optimistic.indexOfFirst { it.emoji == emoji }
            if (groupIdx >= 0) {
                optimistic[groupIdx] = com.muwan.muwanchat.network.MessageReaction(emoji, optimistic[groupIdx].userIds + myUid)
            } else {
                optimistic.add(com.muwan.muwanchat.network.MessageReaction(emoji, listOf(myUid)))
            }
        }
        scope.launch { db.messageDao().updateReactions(messageId, Gson().toJson(optimistic)) }

        scope.launch {
            try {
                val res = RetrofitClient.chatApi.reactToMessage(
                    "Bearer $myToken", groupId, messageId, ReactRequest(emoji)
                )
                res.body()?.reactions?.let { reactions ->
                    db.messageDao().updateReactions(messageId, Gson().toJson(reactions))
                }
            } catch (_: Exception) {
            } finally {
                reactionInFlight.value = reactionInFlight.value - messageId
            }
        }
    }

    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedMessageIds = emptySet()
    }

    fun reactToSelected(emoji: String) {
        val id = selectedMessageIds.firstOrNull() ?: return
        showReactionPicker = false
        showCustomEmojiField = false
        customEmojiInput = ""
        sendReaction(id, emoji)
        exitSelectionMode()
    }

    fun toggleMessageSelection(id: String) {
        selectedMessageIds = if (selectedMessageIds.contains(id)) selectedMessageIds - id else selectedMessageIds + id
        if (selectedMessageIds.isEmpty()) isSelectionMode = false
    }

    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
        }
        exitSelectionMode()
    }

    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", groupId, id)
                } catch (_: Exception) {}
                db.messageDao().markDeleted(id)
            }
        }
        exitSelectionMode()
    }

    fun startEditingSelected() {
        val msg = messages.firstOrNull { it.id == selectedMessageIds.firstOrNull() }
        if (msg != null && msg.sent && msg.type == "text" && !msg.isDeleted) {
            editingMessage = msg
            input = msg.text
            replyTo = null
            exitSelectionMode()
        }
    }

    fun cancelEditing() {
        editingMessage = null
        input = ""
    }

    fun submitEdit() {
        val msg = editingMessage ?: return
        val newText = input.trim()
        if (newText.isBlank()) return
        editingMessage = null
        input = ""
        scope.launch {
            db.messageDao().editMessage(msg.id, newText)
            try {
                RetrofitClient.chatApi.editMessage("Bearer $myToken", groupId, msg.id, EditMessageRequest(newText))
            } catch (_: Exception) {}
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {}
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupVideoMessage(context, it, myToken, groupId, myUid, groupId, groupName, db) {} } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "document", myToken, groupId, myUid, groupId, groupName, db) {} } }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db, displayType = "music") {} } }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri -> scope.launch { uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {} } }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createGroupCameraCaptureUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createGroupCameraCaptureUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            cameraVideoUri?.let { uri -> scope.launch { uploadGroupVideoMessage(context, uri, myToken, groupId, myUid, groupId, groupName, db) {} } }
        }
    }
    val cameraVideoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createGroupCameraVideoCaptureUri(context)
            cameraVideoUri = uri
            cameraVideoLauncher.launch(uri)
        }
    }
    fun launchVideoCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createGroupCameraVideoCaptureUri(context)
            cameraVideoUri = uri
            cameraVideoLauncher.launch(uri)
        } else {
            cameraVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Send logic — receiverUid ki jagah room_id-based group send ──
    fun sendMessageWithId(
        id: String, content: String, createdAt: String, isRetry: Boolean,
        type: String = "text", fileName: String? = null, mimeType: String? = null,
        replyToId: String? = null
    ) {
        if (!isRetry) {
            typingJob?.cancel()
            if (AppSocketManager.isConnected) {
                AppSocketManager.sendGroupStopTyping(groupId)
            }
            scope.launch {
                ChatRepository.recordMessage(
                    db = db,
                    id = id,
                    roomId = groupId,
                    senderUid = myUid,
                    receiverUid = groupId,
                    content = content,
                    type = type,
                    createdAt = createdAt,
                    myUid = myUid,
                    otherUsername = groupName,
                    status = "PENDING",
                    fileName = fileName,
                    mimeType = mimeType,
                    replyToId = replyToId
                )
            }
        } else {
            scope.launch { db.messageDao().updateStatus(id, "PENDING") }
        }
        if (AppSocketManager.isConnected) {
            val timeoutJob = scope.launch {
                delay(8000)
                if (isActive) db.messageDao().updateStatus(id, "FAILED")
            }
            AppSocketManager.sendGroupMessage(id, groupId, content, type, fileName, mimeType, replyToId) { success ->
                timeoutJob.cancel()
                scope.launch {
                    db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                }
            }
        } else {
            // Group ke liye REST /send abhi socket-only hai — offline pe FAILED
            // reh jayega, socket reconnect hote hi user Retry se bhej sakta hai
            scope.launch { db.messageDao().updateStatus(id, "FAILED") }
        }
    }

    fun sendMessage() {
        if (editingMessage != null) { submitEdit(); return }
        val text = input.trim()
        if (text.isBlank()) return
        val id = UUID.randomUUID().toString()
        val createdAt = nowIso()
        val replyId = replyTo?.id
        input = ""
        replyTo = null
        sendMessageWithId(id, text, createdAt, isRetry = false, replyToId = replyId)
    }

    fun retryMessage(msg: ChatMessage) {
        sendMessageWithId(
            msg.id, msg.mediaUrl ?: msg.text, nowIso(), isRetry = true,
            type = msg.type, fileName = msg.fileName, mimeType = msg.mimeType,
            replyToId = msg.replyToId
        )
    }

    var hasDoneInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (!hasDoneInitialScroll) {
                listState.scrollToItem(messages.size - 1)
                hasDoneInitialScroll = true
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token
        myUid = AuthDataStore.getUid(context).first() ?: ""

        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(groupId)

        // Group info fetch — member names (typing display ke liye) + count
        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
            }
        } catch (_: Exception) {}

        try {
            if (db.chatWallpaperDao().getByRoomId(groupId) == null) {
                val res = RetrofitClient.chatApi.getWallpaper("Bearer $token", groupId)
                res.body()?.wallpaper?.let { wp ->
                    db.chatWallpaperDao().upsert(ChatWallpaperEntity(groupId, wp.type, wp.value))
                }
            }
        } catch (_: Exception) {}

        try {
            val res = RetrofitClient.chatApi.getMessages("Bearer $token", groupId)
            if (res.isSuccessful) {
                ChatRepository.syncMessages(db, res.body()?.messages ?: emptyList())
            }
        } catch (_: Exception) {}

        try {
            val delRes = RetrofitClient.chatApi.getDeletedMessages("Bearer $token", groupId)
            if (delRes.isSuccessful) {
                ChatRepository.reconcileDeleted(db, delRes.body()?.ids ?: emptyList())
            }
        } catch (_: Exception) {}

        try {
            RetrofitClient.chatApi.markSeen("Bearer $token", groupId)
            ChatRepository.clearUnread(db, groupId)
        } catch (_: Exception) {}
    }

    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        AppSocketManager.events.collect { event ->
            when (event) {
                is SocketEvent.NewMessage -> {
                    if (event.roomId == groupId) {
                        ChatRepository.recordMessage(
                            db = db,
                            id = event.id,
                            roomId = groupId,
                            senderUid = event.senderUid,
                            receiverUid = groupId,
                            content = event.content,
                            type = event.type,
                            createdAt = event.createdAt.ifBlank { nowIso() },
                            myUid = myUid,
                            otherUsername = groupName,
                            fileName = event.fileName,
                            mimeType = event.mimeType,
                            replyToId = event.replyToId,
                            isForwarded = event.isForwarded
                        )
                        if (event.senderUid != myUid) {
                            try {
                                RetrofitClient.chatApi.markSeen("Bearer $myToken", groupId)
                            } catch (_: Exception) {}
                            ChatRepository.clearUnread(db, groupId)
                        }
                    }
                }
                is SocketEvent.GroupRemoved -> {
                    if (event.roomId == groupId && !event.selfLeave) {
                        db.conversationDao().markRemoved(
                            groupId,
                            if (event.groupDeleted) "GROUP_DELETED" else (event.removedByUsername ?: "Admin")
                        )
                    }
                }
                is SocketEvent.GroupUpdated -> {
                    if (event.roomId == groupId) {
                        scope.launch {
                            try {
                                val res = RetrofitClient.chatApi.getGroup("Bearer $myToken", groupId)
                                res.body()?.group?.let { g -> group = g }
                            } catch (_: Exception) {}
                        }
                    }
                }
                is SocketEvent.MessagesSeen -> {
                    if (event.roomId == groupId) {
                        db.messageDao().markMySentAsSeen(groupId, myUid)
                    }
                }
                is SocketEvent.MessageDeleted -> {
                    if (event.roomId == groupId) {
                        scope.launch { db.messageDao().markDeleted(event.id) }
                    }
                }
                is SocketEvent.MessageEdited -> {
                    if (event.roomId == groupId) {
                        scope.launch { db.messageDao().editMessage(event.id, event.content) }
                    }
                }
                is SocketEvent.ReactionUpdate -> {
                    if (event.roomId == groupId) {
                        scope.launch { db.messageDao().updateReactions(event.id, event.reactionsJson) }
                    }
                }
                is SocketEvent.MessagePreview -> {
                    if (event.roomId == groupId) {
                        scope.launch {
                            db.messageDao().updateLinkPreview(
                                event.id, event.title, event.description, event.image, event.url
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }

    DisposableEffect(groupId) {
        onDispose {
            AppSocketManager.leaveRoom(groupId)
        }
    }
Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exitSelectionMode() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Text(
                    "${selectedMessageIds.size} selected",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                val canEditSelected = selectedMessageIds.size == 1 &&
                    messages.firstOrNull { it.id == selectedMessageIds.first() }
                        ?.let { it.sent && it.type == "text" && !it.isDeleted } == true

                if (canEditSelected) {
                    IconButton(onClick = { startEditingSelected() }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                }

                val canReact = selectedMessageIds.size == 1 &&
                    messages.firstOrNull { it.id == selectedMessageIds.first() }?.isDeleted == false

                if (canReact) {
                    IconButton(onClick = { showReactionPicker = true }) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "React", tint = Color.White)
                    }
                }

                val canForward = selectedMessageIds.isNotEmpty() &&
                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.isDeleted == false }

                if (canForward) {
                    IconButton(onClick = {
                        val toForward = messages.filter { selectedMessageIds.contains(it.id) }
                        ForwardMessageSelection.set(toForward)
                        exitSelectionMode()
                        navController.navigate(com.muwan.muwanchat.navigation.Screen.Forward.route)
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Forward", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = { if (selectedMessageIds.isNotEmpty()) showBulkDeleteConfirm = true },
                    enabled = selectedMessageIds.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = if (selectedMessageIds.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF555577)
                    )
                }
            }
        } else {
            // Same look as ChatHeader (1:1), bas online/offline dot ki jagah
            // member count / typing text, aur naam-tap se GroupInfoScreen khulega.
            // ChatHeader.kt bilkul untouched — ye alag chhota composable hai.
            GroupChatHeader(
                groupName = groupName,
                groupAvatar = groupAvatar ?: conversationEntity?.avatar,
                memberCount = memberCount,
                typingUsernames = typingUids.mapNotNull { memberNames[it] },
                onBack = { navController.popBackStack() },
                onHeaderTap = {
                    navController.navigate(Screen.GroupInfo.createRoute(groupId))
                },
                onVideoCall = { comingSoonFeature = "📹 Group Video Call" },
                onVoiceCall = { comingSoonFeature = "📞 Group Voice Call" },
                onMenuClick = { showMenuSheet = true }
            )
        }

        if (showMenuSheet) {
            ChatWallpaperSheet(
                onDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(groupId))
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            WallpaperPreviewBackground(currentWallpaper)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        myUid = myUid,
                        senderAvatar = memberAvatars[msg.senderUid],
                        senderName = if (msg.senderUid != myUid) memberNames[msg.senderUid] else null,
                        onReactionLongPress = { id, emoji -> sendReaction(id, emoji) },
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedMessageIds.contains(msg.id),
                        onTap = { toggleMessageSelection(msg.id) },
                        onSwipeReply = { replyTo = it },
                        onImageTap = { _ -> fullscreenImage = msg },
                        onVideoTap = { _ -> fullscreenVideo = msg },
                        onDocumentTap = { url, fileName, mimeType ->
                            scope.launch {
                                DocumentOpener.openDocument(
                                    context, url, myToken, fileName ?: "document", mimeType
                                )
                            }
                        },
                        onLinkTap = { url ->
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        },
                        onRetry = { retryMessage(it) },
                        onReplyTap = { targetId ->
                            val index = messages.indexOfFirst { it.id == targetId }
                            if (index >= 0) {
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        }
                    )
                }
            }
        }


        AnimatedVisibility(visible = replyTo != null) {
            replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkInputBg)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("↩ Reply", color = DarkAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(reply.text.take(50), color = Color(0xFF888888),
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { replyTo = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close",
                            tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("✏ Editing message", color = DarkAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { cancelEditing() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel edit",
                        tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                }
            }
        }

        AnimatedVisibility(visible = showEmojiPicker) {
            EmojiPickerRow { emoji -> input += emoji }
        }
        if (conversationEntity?.isRemoved == true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (conversationEntity?.removedByUsername == "GROUP_DELETED")
                        "This group was deleted by the owner"
                    else
                        "You were removed from this group by @${conversationEntity?.removedByUsername ?: "Admin"}",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (group?.onlyAdminsCanSend == true && !isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .clickable { showAdminsSheet = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val onlyText = buildAnnotatedString {
                    append("Only ")
                    withStyle(SpanStyle(color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)) {
                        append("admins")
                    }
                    append(" can send messages")
                }
                Text(onlyText, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            ChatInputBar(
                input = input,
                onInputChange = {
                    input = it
                    if (showEmojiPicker) showEmojiPicker = false

                    if (AppSocketManager.isConnected) {
                        if (it.isNotBlank()) {
                            AppSocketManager.sendGroupTyping(groupId)
                            typingJob?.cancel()
                            typingJob = scope.launch {
                                delay(2500)
                                AppSocketManager.sendGroupStopTyping(groupId)
                            }
                        } else {
                            typingJob?.cancel()
                            AppSocketManager.sendGroupStopTyping(groupId)
                        }
                    }
                },
                showEmojiPicker = showEmojiPicker,
                onToggleEmojiPicker = {
                    showEmojiPicker = !showEmojiPicker
                    if (showEmojiPicker) keyboardController?.hide() else keyboardController?.show()
                },
                onPickImage = { showMediaSheet = true },
                onSend = { sendMessage() },
                onGifReceived = { uri, _, release ->
                    scope.launch {
                        uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db, skipCompression = true, setUploading = {})
                        release()
                    }
                },
                onVoiceMessage = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        showVoiceRecorder = true
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }

    if (showMediaSheet) {
        MediaPickerSheet(
            onDismiss = { showMediaSheet = false },
            onSelectPhoto = { photoPicker.launch("image/*") },
            onSelectVideo = { videoPicker.launch("video/*") },
            onSelectDocument = { docPicker.launch(arrayOf("*/*")) },
            onSelectMusic = { musicPicker.launch("audio/*") },
            onSelectCamera = { launchCamera() },
            onSelectRecordVideo = { launchVideoCamera() }
        )
    }

    if (showVoiceRecorder) {
        VoiceRecorderSheet(
            recorder = audioRecorder,
            onCancel = { showVoiceRecorder = false },
            onSend = { file ->
                showVoiceRecorder = false
                scope.launch {
                    uploadGroupAudioMessage(context, file, myToken, groupId, myUid, groupId, groupName, db) {}
                }
            }
        )
    }

    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    fullscreenImage?.let { mediaMsg ->
        FullscreenImageViewer(
            model = mediaMsg.mediaUrl ?: "",
            onDismiss = { fullscreenImage = null },
            onSendReply = { replyText ->
                sendMessageWithId(UUID.randomUUID().toString(), replyText, nowIso(), isRetry = false, replyToId = mediaMsg.id)
                fullscreenImage = null
            }
        )
    }

    fullscreenVideo?.let { mediaMsg ->
        FullscreenVideoPlayer(
            url = mediaMsg.mediaUrl ?: "",
            onDismiss = { fullscreenVideo = null },
            onSendReply = { replyText ->
                sendMessageWithId(UUID.randomUUID().toString(), replyText, nowIso(), isRetry = false, replyToId = mediaMsg.id)
                fullscreenVideo = null
            }
        )
    }

    if (showReactionPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
        }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(DarkHeader)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    quickReactions.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { reactToSelected(emoji) }
                                .padding(6.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A55))
                            .clickable { showCustomEmojiField = !showCustomEmojiField },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Custom emoji", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                if (showCustomEmojiField) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customEmojiInput,
                            onValueChange = { customEmojiInput = it },
                            placeholder = { Text("Type an emoji…", fontSize = 13.sp, color = Color(0xFF888888)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccent,
                                unfocusedBorderColor = Color(0xFF444466)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { if (customEmojiInput.isNotBlank()) reactToSelected(customEmojiInput.trim()) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(DarkAccent)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "React", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showBulkDeleteConfirm) {
        val allMine = messages.filter { selectedMessageIds.contains(it.id) }.all { it.sent }
        val count = selectedMessageIds.size
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete $count message${if (count > 1) "s" else ""}?") },
            text = {
                Text(
                    if (allMine)
                        "'Delete for Everyone' sabki screen se hatayega, 'Delete for Me' sirf aapki screen se."
                    else
                        "Yeh sirf aapki screen se delete honge."
                )
            },
            confirmButton = {
                if (allMine) {
                    androidx.compose.material3.TextButton(onClick = {
                        deleteSelectedForEveryone()
                        showBulkDeleteConfirm = false
                    }) { Text("Delete for Everyone", color = Color(0xFFE05555)) }
                } else {
                    androidx.compose.material3.TextButton(onClick = {
                        deleteSelectedForMe()
                        showBulkDeleteConfirm = false
                    }) { Text("Delete for Me", color = Color(0xFFE05555)) }
                }
            },
            dismissButton = {
                Row {
                    if (allMine) {
                        androidx.compose.material3.TextButton(onClick = {
                            deleteSelectedForMe()
                            showBulkDeleteConfirm = false
                        }) { Text("Delete for Me") }
                    }
                    androidx.compose.material3.TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showAdminsSheet) {
        ModalBottomSheet(onDismissRequest = { showAdminsSheet = false }, containerColor = DarkHeader) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Group Admins",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val admins = group?.memberProfiles?.filter { it.isAdmin || it.isOwner } ?: emptyList()
                admins.forEach { admin ->
                    val isOnline = onlineUidsForAdmins.contains(admin.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAdminsSheet = false
                                navController.navigate(Screen.UserProfile.createRoute(admin.uid))
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AvatarView(
                                avatarBase64 = admin.avatar,
                                fallbackText = admin.username,
                                size = 44.dp,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        if (isOnline) Color(0xFF2ECC71) else Color(0xFF666688),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(2.dp, DarkHeader, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(admin.username, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (isOnline) "Online" else "Offline",
                                color = if (isOnline) Color(0xFF2ECC71) else Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── GroupChatHeader — ChatHeader.kt jaisa hi UI, bas group-specific data.
// ChatHeader.kt bilkul untouched hai, ye ek naya alag composable hai. ───────
@Composable
private fun GroupChatHeader(
    groupName: String,
    groupAvatar: String?,
    memberCount: Int,
    typingUsernames: List<String>,
    onBack: () -> Unit,
    onHeaderTap: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkHeader)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable { onHeaderTap() }
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            AvatarView(
                avatarBase64 = groupAvatar,
                fallbackText = groupName,
                size = 38.dp,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(groupName, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val statusText = when {
                    typingUsernames.isNotEmpty() ->
                        if (typingUsernames.size == 1) "${typingUsernames[0]} is typing..."
                        else "${typingUsernames.size} people typing..."
                    else -> "$memberCount member${if (memberCount != 1) "s" else ""}"
                }
                Text(
                    statusText,
                    color = if (typingUsernames.isNotEmpty()) DarkAccent else Color(0xFF888888),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row {
            IconButton(onClick = onVideoCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.Call, contentDescription = "Call",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMenuClick) {
                Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
