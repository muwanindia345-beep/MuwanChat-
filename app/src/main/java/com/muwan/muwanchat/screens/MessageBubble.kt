package com.muwan.muwanchat.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBubbleReceived
import com.muwan.muwanchat.DarkBubbleSent
import com.muwan.muwanchat.DarkInputBg

// Message text mein URLs dhoondh ke unhe clickable-blue dikhane ke liye
private const val LINK_TAG = "URL"

private fun linkifyText(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        var url = text.substring(start, end)
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        builder.addStyle(
            SpanStyle(color = DarkAccent, textDecoration = TextDecoration.Underline),
            start, end
        )
        builder.addStringAnnotation(tag = LINK_TAG, annotation = url, start = start, end = end)
    }
    return builder.toAnnotatedString()
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onTap: () -> Unit = {},
    onSwipeReply: (ChatMessage) -> Unit,
    onImageTap: (String) -> Unit,
    onVideoTap: (String) -> Unit,
    onDocumentTap: (String, String, String?) -> Unit,
    onLinkTap: (String) -> Unit = {},
    onRetry: (ChatMessage) -> Unit = {},
    onReplyTap: (String) -> Unit = {},
    onLongPress: (ChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe"
    )
    val isMedia = message.type == "image" || message.type == "video"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) DarkAccent else Color(0xFF333355)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start
        ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!isSelectionMode && offsetX > 80f) onSwipeReply(message)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isSelectionMode && dragAmount > 0) offsetX = (offsetX + dragAmount).coerceIn(0f, 100f)
                        }
                    )
                }
                // Copy Message feature: kahin bhi bubble pe double-tap = clipboard copy, koi UI change nahi
                .pointerInput(message.id, isSelectionMode) {
                    detectTapGestures(
                        onLongPress = { onLongPress(message) },
                        onTap = { if (isSelectionMode) onTap() },
                        onDoubleTap = {
                            if (!isSelectionMode) {
                                val textToCopy = message.text.ifBlank { message.fileName ?: "" }
                                if (textToCopy.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            }
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
                    horizontal = if (isMedia) 4.dp else 14.dp,
                    vertical = if (isMedia) 4.dp else 10.dp
                )
                .let {
                    if (message.sent && message.status == "FAILED")
                        it.clickable { if (isSelectionMode) onTap() else onRetry(message) }
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
                            .clickable { if (isSelectionMode) onTap() else onReplyTap(reply.id) }
                            .padding(8.dp)
                    ) {
                        Text("↩ ${reply.text.take(40)}", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                when (message.type) {
                    "image" -> message.mediaUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Image",
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { if (isSelectionMode) onTap() else onImageTap(url) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    "video" -> message.mediaUrl?.let { url ->
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1A1A))
                                .clickable { if (isSelectionMode) onTap() else onVideoTap(url) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Play video",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    "document" -> message.mediaUrl?.let { url ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkInputBg)
                                .clickable {
                                    if (isSelectionMode) onTap()
                                    else onDocumentTap(url, message.fileName ?: "Document", message.mimeType)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = "Document", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                message.fileName ?: "Document",
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (message.text.isNotBlank()) {
                    if (isSelectionMode) {
                        // Selection mode me poore bubble ka tap select/deselect ke liye reserved hai
                        Text(message.text, color = Color.White, fontSize = 15.sp)
                    } else {
                        val annotated = remember(message.text) { linkifyText(message.text) }
                        ClickableText(
                            text = annotated,
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                            onClick = { offset ->
                                val link = annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()
                                if (link != null) onLinkTap(link.item)
                            }
                        )
                    }
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
                            else -> Icons.Filled.Check to Color(0xAAFFFFFF)
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
}
