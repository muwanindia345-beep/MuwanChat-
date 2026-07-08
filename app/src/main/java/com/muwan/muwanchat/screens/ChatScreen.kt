package com.muwan.muwanchat.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import com.muwan.muwanchat.network.UploadMediaRequest
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
import java.util.*

// ─── Helpers: file info + image compression (upload se pehle) ─────────────
private fun getFileName(context: android.content.Context, uri: Uri): String {
    var name = "file"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
    }
    return name
}

private fun compressImageToBase64(context: android.content.Context, uri: Uri): Pair<String, String> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    receiverUid: String,
    receiverUsername: String,
    roomId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val db = remember { MuwanChatDb.get(context) }

    val messageEntities by db.messageDao().observeMessages(roomId).collectAsState(initial = emptyList())
    val conversationEntity by db.conversationDao().observeByRoomId(roomId).collectAsState(initial = null)

    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }
    var isReceiverOnline by remember { mutableStateOf(false) }
    var isReceiverTyping by remember { mutableStateOf(false) }
    var typingJob by remember { mutableStateOf<Job?>(null) }

    val messages = remember(messageEntities, myUid) {
        messageEntities.map { it.toChatMessage(myUid) }
    }

    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    var comingSoonFeature by remember { mutableStateOf<String?>(null) }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var fullscreenVideo by remember { mutableStateOf<String?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var uploadingMedia by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadVideoMessage(context, it, myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "document", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    fun sendMessageWithId(
        id: String, content: String, createdAt: String, isRetry: Boolean,
        type: String = "text", fileName: String? = null, mimeType: String? = null
    ) {
        if (!isRetry) {
            typingJob?.cancel()
            if (AppSocketManager.isConnected) {
                AppSocketManager.sendStopTyping(receiverUid)
            }
            scope.launch {
                ChatRepository.recordMessage(
                    db = db,
                    id = id,
                    roomId = roomId,
                    senderUid = myUid,
                    receiverUid = receiverUid,
                    content = content,
                    type = type,
                    createdAt = createdAt,
                    myUid = myUid,
                    otherUsername = receiverUsername,
                    status = "PENDING",
                    fileName = fileName,
                    mimeType = mimeType
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
            AppSocketManager.sendMessage(id, receiverUid, content, type, fileName, mimeType) { success ->
                timeoutJob.cancel()
                scope.launch {
                    db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
                }
            }
        } else {
            scope.launch {
                try {
                    val res = RetrofitClient.chatApi.sendMessage(
                        "Bearer $myToken",
                        SendMessageRequest(receiverUid, content, type, fileName, mimeType)
                    )
                    db.messageDao().updateStatus(id, if (res.isSuccessful) "SENT" else "FAILED")
                } catch (_: Exception) {
                    db.messageDao().updateStatus(id, "FAILED")
                }
            }
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank()) return
        val id = UUID.randomUUID().toString()
        val createdAt = nowIso()
        input = ""
        replyTo = null
        sendMessageWithId(id, text, createdAt, isRetry = false)
    }

    fun retryMessage(msg: ChatMessage) {
        sendMessageWithId(
            msg.id, msg.mediaUrl ?: msg.text, nowIso(), isRetry = true,
            type = msg.type, fileName = msg.fileName, mimeType = msg.mimeType
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token

        try {
            val me = RetrofitClient.authApi.me("Bearer $token")
            myUid = me.body()?.user?.uid ?: ""
        } catch (_: Exception) {}

        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(roomId)
        AppSocketManager.checkPresence(receiverUid)

        try {
            val res = RetrofitClient.chatApi.getMessages("Bearer $token", roomId)
            if (res.isSuccessful) {
                ChatRepository.syncMessages(db, res.body()?.messages ?: emptyList())
            }
        } catch (_: Exception) {}

        try {
            RetrofitClient.chatApi.markSeen("Bearer $token", roomId)
            ChatRepository.clearUnread(db, roomId)
        } catch (_: Exception) {}
    }

    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        AppSocketManager.events.collect { event ->
            when (event) {
                is SocketEvent.NewMessage -> {
                    if (event.roomId == roomId) {
                        ChatRepository.recordMessage(
                            db = db,
                            id = event.id,
                            roomId = roomId,
                            senderUid = event.senderUid,
                            receiverUid = if (event.senderUid == myUid) receiverUid else myUid,
                            content = event.content,
                            type = event.type,
                            createdAt = event.createdAt.ifBlank { nowIso() },
                            myUid = myUid,
                            otherUsername = receiverUsername,
                            fileName = event.fileName,
                            mimeType = event.mimeType
                        )
                        if (event.senderUid != myUid) {
                            isReceiverTyping = false
                            try {
                                RetrofitClient.chatApi.markSeen("Bearer $myToken", roomId)
                            } catch (_: Exception) {}
                            ChatRepository.clearUnread(db, roomId)
                        }
                    }
                }
                is SocketEvent.MessagesSeen -> {
                    if (event.roomId == roomId) {
                        db.messageDao().markMySentAsSeen(roomId, myUid)
                    }
                }
                is SocketEvent.UserOnline -> {
                    if (event.uid == receiverUid) isReceiverOnline = true
                }
                is SocketEvent.UserOffline -> {
                    if (event.uid == receiverUid) {
                        isReceiverOnline = false
                        isReceiverTyping = false
                    }
                }
                is SocketEvent.PresenceStatus -> {
                    if (event.uid == receiverUid) isReceiverOnline = event.online
                }
                is SocketEvent.Typing -> {
                    if (event.uid == receiverUid && event.roomId == roomId) isReceiverTyping = true
                }
                is SocketEvent.StopTyping -> {
                    if (event.uid == receiverUid) isReceiverTyping = false
                }
                else -> {}
            }
        }
    }

    DisposableEffect(roomId) {
        onDispose {
            AppSocketManager.leaveRoom(roomId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {
        ChatHeader(
            receiverUsername = receiverUsername,
            isOnline = isReceiverOnline,
            isTyping = isReceiverTyping,
            avatarBase64 = conversationEntity?.avatar,
            onBack = { navController.popBackStack() },
            onVideoCall = { comingSoonFeature = "📹 Video Call" },
            onVoiceCall = { comingSoonFeature = "📞 Voice Call" }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                ) {
                    MessageBubble(
                        message = msg,
                        onSwipeReply = { replyTo = it },
                        onImageTap = { url -> fullscreenImage = url },
                        onVideoTap = { url -> fullscreenVideo = url },
                        onDocumentTap = { url, _ ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) })
                            } catch (_: Exception) {}
                        },
                        onRetry = { retryMessage(it) }
                    )
                }
            }
        }

        AnimatedVisibility(visible = uploadingMedia) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DarkAccent)
        }

        AnimatedVisibility(visible = replyTo != null) {
            replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkInputBg)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("↩ Reply", color = com.muwan.muwanchat.DarkAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

        AnimatedVisibility(visible = showEmojiPicker) {
            EmojiPickerRow { emoji -> input += emoji }
        }

        ChatInputBar(
            input = input,
            onInputChange = {
                input = it
                if (showEmojiPicker) showEmojiPicker = false

                if (AppSocketManager.isConnected) {
                    if (it.isNotBlank()) {
                        AppSocketManager.sendTyping(roomId, receiverUid)
                        typingJob?.cancel()
                        typingJob = scope.launch {
                            delay(2500)
                            AppSocketManager.sendStopTyping(receiverUid)
                        }
                    } else {
                        typingJob?.cancel()
                        AppSocketManager.sendStopTyping(receiverUid)
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
            onVoiceMessage = { comingSoonFeature = "🎤 Voice Message" }
        )
    }

    if (showMediaSheet) {
        MediaPickerSheet(
            onDismiss = { showMediaSheet = false },
            onSelectPhoto = { photoPicker.launch("image/*") },
            onSelectVideo = { videoPicker.launch("video/*") },
            onSelectDocument = { docPicker.launch(arrayOf("*/*")) }
        )
    }

    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    fullscreenImage?.let { url ->
        FullscreenImageViewer(model = url, onDismiss = { fullscreenImage = null })
    }

    fullscreenVideo?.let { url ->
        FullscreenVideoPlayer(url = url, onDismiss = { fullscreenVideo = null })
    }
}

// ─── Upload + send: photo/document (base64 → MuwanBox) ────────────────────
private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
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
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image") compressImageToBase64(context, uri)
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
}

// ─── Upload + send: video (multipart → Cloudinary) ─────────────────────────
private suspend fun uploadVideoMessage(
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
}
