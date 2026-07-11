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
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AudioRecorder
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.DeletedMessageEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import com.muwan.muwanchat.network.EditMessageRequest
import com.muwan.muwanchat.network.ReactRequest
import com.google.gson.Gson
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
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val currentWallpaper by db.chatWallpaperDao().observeByRoomId(roomId).collectAsState(initial = null)

    val messageEntities by db.messageDao().observeMessages(roomId).collectAsState(initial = emptyList())
    val conversationEntity by db.conversationDao().observeByRoomId(roomId).collectAsState(initial = null)

    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }
    var isReceiverOnline by remember { mutableStateOf(false) }
    var isReceiverTyping by remember { mutableStateOf(false) }
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
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var fullscreenVideo by remember { mutableStateOf<String?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var uploadingMedia by remember { mutableStateOf(false) }
    // ── Voice message recording state ──
    var showVoiceRecorder by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder(context) }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showVoiceRecorder = true
        else Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }
    // ── Multi-select "delete message" state (ConversationListScreen jaisa hi system) ──
    var isSelectionMode by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }
    var showCustomEmojiField by remember { mutableStateOf(false) }
    val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    // Reusable — selection-mode picker aur bubble ke reaction-chip long-press dono isi ko call karte hain
    fun sendReaction(messageId: String, emoji: String) {
        scope.launch {
            try {
                val res = RetrofitClient.chatApi.reactToMessage(
                    "Bearer $myToken", roomId, messageId, ReactRequest(emoji)
                )
                res.body()?.reactions?.let { reactions ->
                    db.messageDao().updateReactions(messageId, Gson().toJson(reactions))
                }
            } catch (_: Exception) {
                // Offline ho toh koi baat nahi, agli baar socket/sync se sahi ho jayega
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
            // Record karo taaki agla sync backend se inhe wapas na le aaye
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
        }
        exitSelectionMode()
    }

    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)
                } catch (_: Exception) {
                    // Backend call fail ho jaaye (jaise no internet) to bhi apni screen se hata dete hain;
                    // dusre user tak socket event backend se hi jaayega jab connection wapas aayega.
                }
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
                RetrofitClient.chatApi.editMessage("Bearer $myToken", roomId, msg.id, EditMessageRequest(newText))
            } catch (_: Exception) {
            }
        }
    }

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
        type: String = "text", fileName: String? = null, mimeType: String? = null,
        replyToId: String? = null
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
            AppSocketManager.sendMessage(id, receiverUid, content, type, fileName, mimeType, replyToId) { success ->
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
                        SendMessageRequest(receiverUid, content, type, fileName, mimeType, replyToId)
                    )
                    db.messageDao().updateStatus(id, if (res.isSuccessful) "SENT" else "FAILED")
                } catch (_: Exception) {
                    db.messageDao().updateStatus(id, "FAILED")
                }
            }
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
        AppSocketManager.joinRoom(roomId)
        AppSocketManager.checkPresence(receiverUid)

        try {
            val res = RetrofitClient.chatApi.getMessages("Bearer $token", roomId)
            if (res.isSuccessful) {
                ChatRepository.syncMessages(db, res.body()?.messages ?: emptyList())
            }
        } catch (_: Exception) {}

        try {
            val delRes = RetrofitClient.chatApi.getDeletedMessages("Bearer $token", roomId)
            if (delRes.isSuccessful) {
                ChatRepository.reconcileDeleted(db, delRes.body()?.ids ?: emptyList())
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
                            mimeType = event.mimeType,
                            replyToId = event.replyToId
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
                is SocketEvent.MessageDeleted -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().markDeleted(event.id) }
                    }
                }
                is SocketEvent.MessageEdited -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().editMessage(event.id, event.content) }
                    }
                }
                is SocketEvent.ReactionUpdate -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().updateReactions(event.id, event.reactionsJson) }
                    }
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
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
            ChatHeader(
                receiverUsername = receiverUsername,
                isOnline = isReceiverOnline,
                isTyping = isReceiverTyping,
                avatarBase64 = conversationEntity?.avatar,
                onBack = { navController.popBackStack() },
                onVideoCall = { comingSoonFeature = "📹 Video Call" },
                onVoiceCall = { comingSoonFeature = "📞 Voice Call" },
                onMenuClick = { showMenuSheet = true }
            )
        }

        if (showMenuSheet) {
            ChatWallpaperSheet(
                onDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(roomId))
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
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedMessageIds.contains(msg.id),
                        onTap = { toggleMessageSelection(msg.id) },
                        onSwipeReply = { replyTo = it },
                        onImageTap = { url -> fullscreenImage = url },
                        onVideoTap = { url -> fullscreenVideo = url },
                        onDocumentTap = { url, _, mimeType ->
                            val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: run {
                                val ext = MimeTypeMap.getFileExtensionFromUrl(url)
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                            }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    if (resolvedMime != null) setDataAndType(Uri.parse(url), resolvedMime)
                                    else data = Uri.parse(url)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) {}
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
                            }
                        }
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

        AnimatedVisibility(visible = editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("✏ Editing message", color = com.muwan.muwanchat.DarkAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { cancelEditing() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel edit",
                        tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
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
            onGifReceived = { uri, _, release ->
                scope.launch {
                    uploadMediaMessage(context, uri, "gif", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it }
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

    if (showMediaSheet) {
        MediaPickerSheet(
            onDismiss = { showMediaSheet = false },
            onSelectPhoto = { photoPicker.launch("image/*") },
            onSelectVideo = { videoPicker.launch("video/*") },
            onSelectDocument = { docPicker.launch(arrayOf("*/*")) }
        )
    }

    if (showVoiceRecorder) {
        VoiceRecorderSheet(
            recorder = audioRecorder,
            onCancel = { showVoiceRecorder = false },
            onSend = { file ->
                showVoiceRecorder = false
                scope.launch {
                    uploadAudioMessage(context, file, myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it }
                }
            }
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            modifier = Modifier.weight(1f).height(52.dp),
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
        // Sirf tabhi "Delete for Everyone" dikhega jab SAARE selected messages khud ke bheje hue hain
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

// ─── Upload + send: voice message (base64 → MuwanBox, category=audio) ─────
private suspend fun uploadAudioMessage(
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
}
