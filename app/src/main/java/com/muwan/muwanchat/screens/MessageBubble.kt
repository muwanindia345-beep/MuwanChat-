package com.muwan.muwanchat.screens

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muwan.muwanchat.DarkBubbleReceived
import com.muwan.muwanchat.DarkBubbleSent
import com.muwan.muwanchat.DarkInputBg

@Composable
fun MessageBubble(
    message: ChatMessage,
    onSwipeReply: (ChatMessage) -> Unit,
    onImageTap: (Uri) -> Unit,
    onRetry: (ChatMessage) -> Unit = {}
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
                .let {
                    if (message.sent && message.status == "FAILED")
                        it.clickable { onRetry(message) }
                    else it
                }
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message.time,
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp
                    )
                    if (message.sent) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, tint) = when (message.status) {
                            "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                            "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                            "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                            else -> Icons.Filled.Check to Color(0xAAFFFFFF) // SENT (default)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status,
                            tint = tint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
