package com.muwan.muwanchat.screens

import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.DarkBubbleSent
import com.muwan.muwanchat.DarkBubbleReceived
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.network.MessageItem
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import java.text.SimpleDateFormat
import java.util.*

// ─── Local message wrapper ────────────────────────────────────────────────────
data class ChatMessage(
    val id: String,
    val text: String,
    val sent: Boolean,         // true = aapka message
    val time: String,
    val imageUri: Uri? = null,
    val replyTo: ChatMessage? = null
)

private fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = content,
    sent = sender_uid == myUid,
    time = created_at.take(16).replace("T", " "),
    imageUri = null
)

private fun nowTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}

// ─── Coming Soon Dialog ───────────────────────────────────────────────────────
@Composable
fun ComingSoonDialog(feature: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213e),
        title = {
            Text("🔜 Coming Soon", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    feature,
                    color = DarkAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Yeh feature abhi development mein hai.\nJald aayega! 🚀",
                    color = Color(0xFF888888),
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("OK", color = Color.White) }
        }
    )
}

// ─── Fullscreen Image Viewer ──────────────────────────────────────────────────
@Composable
fun FullscreenImageViewer(uri: Uri, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Full image",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color(0x88000000), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            // Double tap to reset zoom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offsetX = 0f
                            offsetY = 0f
                        })
                    }
            )
        }
    }
}

// ─── Emoji Picker Row ─────────────────────────────────────────────────────────
val QUICK_EMOJIS = listOf(
    "😀","😂","🥹","😍","🤩","😎","🥳","😭","😤","🤯",
    "❤️","🔥","💯","👍","👏","🙏","💀","😈","🫡","✅",
    "😴","🤔","🫂","🥺","😅","😬","🤣","😇","🤗","😏"
)

@Composable
fun EmojiPickerRow(onEmojiSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0f1428))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QUICK_EMOJIS.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .clickable { onEmojiSelected(emoji) }
                        .padding(4.dp)
                )
            }
        }
    }
}

// ─── Main ChatScreen ──────────────────────────────────────────────────────────
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

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Dialog states
    var comingSoonFeature by remember { mutableStateOf<String?>(null) }
    var fullscreenImage by remember { mutableStateOf<Uri?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Socket
    var socket by remember { mutableStateOf<Socket?>(null) }
    val BACKEND_URL = "https://muwan-chat-backend-production.up.railway.app"

    // ── Image picker ──────────────────────────────────────────────────────────
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            messages.add(ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "",
                sent = true,
                time = nowTime(),
                imageUri = it
            ))
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    // ── Load history + init socket ────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token

        // Get my uid
        try {
            val me = RetrofitClient.authApi.me("Bearer $token")
            myUid = me.body()?.user?.uid ?: ""
        } catch (_: Exception) {}

        // Load message history
        try {
            val res = RetrofitClient.chatApi.getMessages("Bearer $token", roomId)
            if (res.isSuccessful) {
                val history = res.body()?.messages ?: emptyList()
                messages.addAll(history.map { it.toChatMessage(myUid) })
                if (messages.isNotEmpty())
                    listState.scrollToItem(messages.size - 1)
            }
        } catch (_: Exception) {}

        isLoading = false

        // Socket.IO connect
        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                transports = arrayOf("websocket")
            }
            val s = IO.socket(BACKEND_URL, opts)

            s.on(Socket.EVENT_CONNECT) {
                s.emit("join_room", roomId)
            }

            s.on("new_message") { args ->
                val json = args[0] as? JSONObject ?: return@on
                val senderId = json.optString("sender_uid")
                // avoid duplicate if we sent it
                val msgId = json.optString("id")
                if (messages.any { it.id == msgId }) return@on

                val msg = ChatMessage(
                    id = msgId,
                    text = json.optString("content"),
                    sent = senderId == myUid,
                    time = json.optString("created_at").take(16).replace("T", " ")
                        .ifBlank { nowTime() }
                )
                scope.launch {
                    messages.add(msg)
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            s.connect()
            socket = s
        } catch (_: Exception) {}
    }

    // Disconnect on leave
    DisposableEffect(Unit) {
        onDispose { socket?.disconnect() }
    }

    // ── Send message ──────────────────────────────────────────────────────────
    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank()) return

        val tempId = UUID.randomUUID().toString()
        val tempMsg = ChatMessage(
            id = tempId,
            text = text,
            sent = true,
            time = nowTime(),
            replyTo = replyTo
        )
        messages.add(tempMsg)
        input = ""
        replyTo = null
        scope.launch { listState.animateScrollToItem(messages.size - 1) }

        // Emit via socket
        socket?.let { s ->
            val json = JSONObject().apply {
                put("receiver_uid", receiverUid)
                put("content", text)
                put("type", "text")
            }
            s.emit("send_message", json)
        } ?: run {
            // Fallback: REST
            scope.launch {
                try {
                    RetrofitClient.chatApi.sendMessage(
                        "Bearer $myToken",
                        SendMessageRequest(receiverUid, text)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        receiverUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(receiverUsername, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Online", color = Color(0xFF888888), fontSize = 12.sp)
                }
            }
            Row {
                IconButton(onClick = { comingSoonFeature = "📹 Video Call" }) {
                    Icon(Icons.Filled.VideoCall, contentDescription = "Video",
                        tint = DarkAccent, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { comingSoonFeature = "📞 Voice Call" }) {
                    Icon(Icons.Filled.Call, contentDescription = "Call",
                        tint = DarkAccent, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Messages
        if (isLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else {
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
                            onImageTap = { uri -> fullscreenImage = uri }
                        )
                    }
                }
            }
        }

        // Reply Preview
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

        // Emoji Picker
        AnimatedVisibility(visible = showEmojiPicker) {
            EmojiPickerRow { emoji ->
                input += emoji
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkHeader)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji toggle
                IconButton(
                    onClick = {
                        showEmojiPicker = !showEmojiPicker
                        if (showEmojiPicker) keyboardController?.hide()
                        else keyboardController?.show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (showEmojiPicker) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions,
                        contentDescription = "Emoji",
                        tint = if (showEmojiPicker) DarkAccent else Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }

                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (showEmojiPicker) showEmojiPicker = false
                    },
                    placeholder = {
                        Text("Message...", color = Color(0xFF888888),
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = false,
                    maxLines = 4
                )

                // Image picker — smaller icon
                IconButton(
                    onClick = { photoPicker.launch("image/*") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "Photo",
                        tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                }
            }

            FloatingActionButton(
                onClick = { sendMessage() },
                containerColor = DarkAccent,
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                Icon(
                    if (input.isBlank()) Icons.Filled.Mic else Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    fullscreenImage?.let { uri ->
        FullscreenImageViewer(uri = uri, onDismiss = { fullscreenImage = null })
    }
}

// ─── Message Bubble ───────────────────────────────────────────────────────────
@Composable
fun MessageBubble(
    message: ChatMessage,
    onSwipeReply: (ChatMessage) -> Unit,
    onImageTap: (Uri) -> Unit
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
                .background(if (message.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(
                    horizontal = if (message.imageUri != null) 4.dp else 14.dp,
                    vertical = if (message.imageUri != null) 4.dp else 10.dp
                )
        ) {
            Column {
                // Reply preview
                message.replyTo?.let { reply ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkInputBg)
                            .padding(8.dp)
                    ) {
                        Text("↩ ${reply.text.take(40)}", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Image — smaller, tappable → fullscreen
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Image",
                        modifier = Modifier
                            .widthIn(max = 200.dp)       // chhota size
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageTap(uri) }, // tap → fullscreen
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(message.text, color = Color.White, fontSize = 15.sp)
                }

                Text(
                    message.time,
                    color = Color(0xAAFFFFFF),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
