package com.muwan.muwanchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

data class Message(
    val id: Int,
    val text: String,
    val sent: Boolean,
    val time: String,
    val replyTo: Message? = null
)

val DarkBg = Color(0xFF1a1a2e)
val DarkHeader = Color(0xFF16213e)
val DarkBubbleSent = Color(0xFFff6b35)
val DarkBubbleReceived = Color(0xFF16213e)
val DarkAccent = Color(0xFFff6b35)
val DarkInputBg = Color(0xFF0f3460)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuwanChatApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuwanChatApp() {
    val messages = remember {
        mutableStateListOf(
            Message(1, "Hey MuwanChat mein welcome hai! 🔥", false, "12:00"),
            Message(2, "Thanks bhai! UI bahut fire lag raha hai 😄", true, "12:01"),
            Message(3, "Swipe karke reply try karo!", false, "12:02"),
        )
    }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                ) {
                    MessageBubble(
                        message = msg,
                        onSwipeReply = { replyTo = it }
                    )
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
                    Column {
                        Text("↩ Reply", color = DarkAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            reply.text.take(50),
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { replyTo = null }) {
                        Text("✕", color = Color(0xFF888888), fontSize = 16.sp)
                    }
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkInputBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message likho...", color = Color(0xFF888888)) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkHeader,
                    unfocusedContainerColor = DarkHeader,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add(
                            Message(
                                id = messages.size + 1,
                                text = input.trim(),
                                sent = true,
                                time = "now",
                                replyTo = replyTo
                            )
                        )
                        input = ""
                        replyTo = null
                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                },
                containerColor = DarkAccent,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
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
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomEnd = if (message.sent) 4.dp else 18.dp,
                        bottomStart = if (message.sent) 18.dp else 4.dp
                    )
                )
                .background(if (message.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
                        Text(
                            "↩ ${reply.text.take(40)}",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(message.text, color = Color.White, fontSize = 15.sp)
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
