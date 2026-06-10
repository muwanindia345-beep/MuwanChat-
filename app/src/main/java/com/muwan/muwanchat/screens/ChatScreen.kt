package com.muwan.muwanchat.screens

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

data class Message(
    val id: Int,
    val text: String,
    val sent: Boolean,
    val time: String,
    val replyTo: Message? = null,
    val imageUri: Uri? = null
)

val DarkHeader = Color(0xFF16213e)
val DarkBubbleSent = Color(0xFFff6b35)
val DarkBubbleReceived = Color(0xFF16213e)
val DarkInputBg = Color(0xFF0f3460)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages = remember {
        mutableStateListOf(
            Message(1, "Hey MuwanChat mein welcome hai! 🔥", false, "12:00"),
            Message(2, "Thanks bhai! UI bahut fire lag raha hai 😄", true, "12:01"),
            Message(3, "Swipe to reply try karo!", false, "12:02"),
        )
    }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            messages.add(Message(
                id = messages.size + 1,
                text = "",
                sent = true,
                time = "now",
                imageUri = it
            ))
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("MuwanChat", color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Online", color = Color(0xFF888888), fontSize = 12.sp)
                }
            }
            Row {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.VideoCall, contentDescription = "Video", tint = DarkAccent, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Call, contentDescription = "Call", tint = DarkAccent, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = DarkAccent, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Messages
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
                    MessageBubble(message = msg, onSwipeReply = { replyTo = it })
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
                        Text(reply.text.take(50), color = Color(0xFF888888), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { replyTo = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    }
                }
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
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.EmojiEmotions, contentDescription = "Emoji", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text("Message...", color = Color(0xFF888888), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    maxLines = 4,
                )
                IconButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Image, contentDescription = "Photo", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Camera", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
            }

            FloatingActionButton(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add(Message(
                            id = messages.size + 1,
                            text = input.trim(),
                            sent = true,
                            time = "now",
                            replyTo = replyTo
                        ))
                        input = ""
                        replyTo = null
                        scope.launch { listState.animateScrollToItem(messages.size - 1) }
                    }
                },
                containerColor = DarkAccent,
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                if (input.isBlank()) {
                    Icon(Icons.Filled.Mic, contentDescription = "Mic", tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, onSwipeReply: (Message) -> Unit) {
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
                .clip(RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomEnd = if (message.sent) 4.dp else 18.dp,
                    bottomStart = if (message.sent) 18.dp else 4.dp
                ))
                .background(if (message.sent) DarkBubbleSent else DarkBubbleReceived)
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
                            .background(DarkInputBg)
                            .padding(8.dp)
                    ) {
                        Text("↩ ${reply.text.take(40)}", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Image",
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
