package com.muwan.muwanchat.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MessageEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.network.MessageItem
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

const val BACKEND_URL = "https://muwanchat-production.up.railway.app"

data class ChatMessage(
    val id: String,
    val text: String,
    val sent: Boolean,
    val time: String,
    val seen: Int = 0,
    val imageUri: Uri? = null,
    val replyTo: ChatMessage? = null
)

private fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = content,
    sent = sender_uid == myUid,
    seen = seen,
    time = formatTime(created_at),
    imageUri = null
)

private fun MessageEntity.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = content,
    sent = senderUid == myUid,
    seen = seen,
    time = formatTime(createdAt),
    imageUri = null
)

private fun formatTime(iso: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdf.parse(iso.take(16)) ?: return iso.take(16)
        val now = Date()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val msgDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        when {
            msgDay == today -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        iso.take(16)
    }
}

private fun nowTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

// ─── Coming Soon Dialog ───────────────────────────────────────────────────────
@Composable
fun ComingSoonDialog(feature: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213e),
        title = { Text("Coming Soon 🚀", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text("$feature will be available soon!", color = Color(0xFF888888)) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8612C)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("OK", color = Color.White) }
        }
    )
}

// ─── Fullscreen Image ─────────────────────────────────────────────────────────
@Composable
fun FullscreenImageViewer(uri: Uri, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Fullscreen",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ─── Message Bubble ───────────────────────────────────────────────────────────
@Composable
fun MessageBubble(
    message: ChatMessage,
    onSwipeReply: (ChatMessage) -> Unit,
    onImageTap: (Uri) -> Unit,
    onLongPress: (ChatMessage) -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress(message) })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 80f) onSwipeReply(message)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0) offsetX = (offsetX + dragAmount).coerceIn(0f, 100f)
                        }
                    )
                }
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomEnd = if (message.sent) 4.dp else 18.dp,
                        bottomStart = if (message.sent) 18.dp else 4.dp
                    )
                )
                .background(if (message.sent) Color(0xFFE8612C) else Color(0xFF1E2A45))
                .padding(
                    horizontal = if (message.imageUri != null) 4.dp else 14.dp,
                    vertical = if (message.imageUri != null) 4.dp else 10.dp
                )
        ) {
            Column {
                message.replyTo?.let { reply ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0D1B2A))
                            .padding(8.dp)
                    ) {
                        Text("↩ ${reply.text.take(40)}", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Image",
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageTap(uri) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(message.text, color = Color.White, fontSize = 15.sp)
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(message.time, color = Color(0xAAFFFFFF), fontSize = 11.sp)
                    if (message.sent) {
                        Text(
                            text = if (message.seen == 1) "✓✓" else "✓",
                            fontSize = 11.sp,
                            color = if (message.seen == 1) Color(0xFF34B7F1) else Color(0xAAFFFFFF)
                        )
                    }
                }
            }
        }
    }
}

// ─── Chat Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    receiverUid: String,
    receiverName: String,
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val db = remember { MuwanChatDb.get(context) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    var socket by remember { mutableStateOf<Socket?>(null) }

    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }

    var comingSoonFeature by remember { mutableStateOf<String?>(null) }
    var fullscreenImage by remember { mutableStateOf<Uri?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(Unit) {
        try {
            val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
            myToken = token

            try {
                val me = RetrofitClient.authApi.me("Bearer $token")
                myUid = me.body()?.user?.uid ?: ""
            } catch (_: Exception) {}

            // Room cache se instant load
            val cached = db.messageDao().getMessages(roomId)
            if (cached.isNotEmpty()) {
                messages.addAll(cached.map { it.toChatMessage(myUid) })
                listState.scrollToItem(messages.size - 1)
            }

            // Server se fresh data
            try {
                val res = RetrofitClient.chatApi.getMessages("Bearer $token", roomId)
                if (res.isSuccessful) {
                    val fresh = res.body()?.messages ?: emptyList()
                    db.messageDao().insertAll(fresh.map {
                        MessageEntity(
                            id = it.id,
                            roomId = it.room_id,
                            senderUid = it.sender_uid,
                            receiverUid = it.receiver_uid,
                            content = it.content,
                            type = it.type,
                            seen = it.seen,
                            createdAt = it.created_at
                        )
                    })
                    messages.clear()
                    messages.addAll(fresh.map { it.toChatMessage(myUid) })
                    if (messages.isNotEmpty())
                        listState.scrollToItem(messages.size - 1)
                }
            } catch (_: Exception) {}

            // Mark seen
            try {
                RetrofitClient.chatApi.markSeen("Bearer $token", roomId)
                db.messageDao().markSeen(roomId, myUid)
            } catch (_: Exception) {}

            // Socket connect
            try {
                val opts = IO.Options().apply {
                    auth = mapOf("token" to token)
                    transports = arrayOf("websocket")
                }
                val s = IO.socket(BACKEND_URL, opts)
                s.on(Socket.EVENT_CONNECT) { s.emit("join_room", roomId) }
                s.on("new_message") { args ->
                    val json = args[0] as? JSONObject ?: return@on
                    val senderId = json.optString("sender_uid")
                    val msgId = json.optString("id")
                    if (messages.any { it.id == msgId }) return@on
                    val createdAt = json.optString("created_at")
                    val content = json.optString("content")
                    val msg = ChatMessage(
                        id = msgId,
                        text = content,
                        sent = senderId == myUid,
                        seen = 0,
                        time = formatTime(createdAt).ifBlank { nowTime() }
                    )
                    scope.launch {
                        messages.add(msg)
                        db.messageDao().insert(
                            MessageEntity(
                                id = msgId,
                                roomId = roomId,
                                senderUid = senderId,
                                receiverUid = if (senderId == myUid) receiverUid else myUid,
                                content = content,
                                type = "text",
                                seen = 0,
                                createdAt = createdAt
                            )
                        )
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
                s.connect()
                socket = s
            } catch (_: Exception) {}

        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose { socket?.disconnect() }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank()) return
        val tempId = UUID.randomUUID().toString()
        val tempMsg = ChatMessage(
            id = tempId, text = text, sent = true,
            seen = 0, time = nowTime(), replyTo = replyTo
        )
        messages.add(tempMsg)
        input = ""
        replyTo = null
        scope.launch { listState.animateScrollToItem(messages.size - 1) }

        socket?.let { s ->
            val json = JSONObject().apply {
                put("receiver_uid", receiverUid)
                put("content", text)
                put("type", "text")
            }
            s.emit("send_message", json)
        } ?: run {
            scope.launch {
                try {
                    RetrofitClient.chatApi.sendMessage(
                        "Bearer $myToken",
                        SendMessageRequest(receiverUid, text)
                    )
                } catch (_: Exception) {}
            }
        }

        scope.launch {
            db.messageDao().insert(
                MessageEntity(
                    id = tempId, roomId = roomId,
                    senderUid = myUid, receiverUid = receiverUid,
                    content = text, type = "text", seen = 0,
                    createdAt = java.time.Instant.now().toString()
                )
            )
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D1B2A),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16213e)),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8612C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                receiverName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(receiverName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Online", color = Color(0xFF4CAF50), fontSize = 12.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { comingSoonFeature = "Video Call" }) {
                        Icon(Icons.Default.VideoCall, contentDescription = "Video", tint = Color.White)
                    }
                    IconButton(onClick = { comingSoonFeature = "Voice Call" }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                    .navigationBarsPadding()
        ) {
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
                            onImageTap = { uri -> fullscreenImage = uri },
                            onLongPress = { deleteTarget = it }
                        )
                    }
                }
            }

            // Reply preview
            replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16213e))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "↩ ${reply.text.take(50)}",
                        color = Color(0xFF888888),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { replyTo = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                }
            }

            // Emoji picker
            if (showEmojiPicker) {
                val emojis = listOf("😀","😂","🥰","😎","🤔","😅","🙏","🔥","💀","👑","✅","❤️","👍","🎉","😭","💯")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16213e))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    emojis.forEach { emoji ->
                        Text(
                            emoji, fontSize = 24.sp,
                            modifier = Modifier.clickable {
                                input += emoji
                                showEmojiPicker = false
                            }
                        )
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16213e))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = Color(0xFF888888))
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...", color = Color(0xFF555555)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0D1B2A),
                        unfocusedContainerColor = Color(0xFF0D1B2A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        sendMessage()
                        keyboardController?.hide()
                    }),
                    maxLines = 4
                )
                IconButton(onClick = { comingSoonFeature = "Image Upload" }) {
                    Icon(Icons.Default.Image, contentDescription = "Image", tint = Color(0xFF888888))
                }
                if (input.isBlank()) {
                    IconButton(onClick = { comingSoonFeature = "Voice Message" }) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8612C))
                                .padding(12.dp))
                    }
                } else {
                    IconButton(onClick = { sendMessage(); keyboardController?.hide() }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8612C))
                                .padding(12.dp))
                    }
                }
            }
        }

        // Dialogs
        comingSoonFeature?.let {
            ComingSoonDialog(feature = it, onDismiss = { comingSoonFeature = null })
        }

        fullscreenImage?.let {
            FullscreenImageViewer(uri = it, onDismiss = { fullscreenImage = null })
        }

        deleteTarget?.let { msg ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor = Color(0xFF16213e),
                title = { Text("Delete Message?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("\"${msg.text.take(40)}\"", color = Color(0xFF888888)) },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", msg.id)
                                    db.messageDao().deleteById(msg.id)
                                    messages.removeIf { it.id == msg.id }
                                } catch (_: Exception) {}
                            }
                            deleteTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Delete", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel", color = Color(0xFF888888))
                    }
                }
            )
        }
    }
}
