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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import com.muwan.muwanchat.data.UploadProgressTracker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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

// Bug fix: DarkAccent (link color) == DarkBubbleSent (sent bubble background),
// isliye sent bubble pe link orange-on-orange ho ke invisible ho jata tha.
// Sent bubble pe white-ish + underline, received bubble pe DarkAccent — dono jagah contrast guaranteed.
private val LinkColorOnSent = Color(0xFFFFE0B2)

// muwanchat://join/{code} jaisa custom-scheme invite link Patterns.WEB_URL se
// match nahi hota (wo sirf http/https/www samajhta hai), isliye alag se
// regex chahiye. Overlap avoid karne ke liye already-covered ranges track
// karte hain taaki WEB_URL match par dobara annotation na lage.
private val CUSTOM_SCHEME_LINK = Regex("""muwanchat://\S+""")

private fun linkifyText(text: String, sent: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    val linkColor = if (sent) LinkColorOnSent else DarkAccent
    val coveredRanges = mutableListOf<IntRange>()

    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        var url = text.substring(start, end)
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        builder.addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            start, end
        )
        builder.addStringAnnotation(tag = LINK_TAG, annotation = url, start = start, end = end)
        coveredRanges.add(start until end)
    }

    CUSTOM_SCHEME_LINK.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (coveredRanges.none { it.first < end && start < it.last + 1 }) {
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start, end
            )
            builder.addStringAnnotation(tag = LINK_TAG, annotation = match.value, start = start, end = end)
        }
    }

    return builder.toAnnotatedString()
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    myUid: String = "",
    onReactionLongPress: (String, String) -> Unit = { _, _ -> },
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
    onLongPress: (ChatMessage) -> Unit = {},
    senderAvatar: String? = null,
    senderName: String? = null
) {
    if (message.type == "system") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    message.text,
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var offsetX by remember { mutableFloatStateOf(0f) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotatedText = remember(message.text, message.sent) { linkifyText(message.text, message.sent) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe"
    )
    val isMedia = message.type == "image" || message.type == "gif" || message.type == "video" || message.type == "audio" || message.type == "music"
    // Sticker/GIF ka apna pattern hai: koi chat-bubble background/padding nahi (WhatsApp jaisa),
    // bada size, aur timestamp seedha image ke corner pe overlay hota hai — niche alag row nahi.
    val isSticker = message.type == "gif"

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
            horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
        if (!message.sent && senderName != null) {
            AvatarView(
                avatarBase64 = senderAvatar,
                fallbackText = senderName,
                size = 26.dp,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (message.sent) Alignment.End else Alignment.Start) {
        if (!message.sent && senderName != null) {
            Text(
                senderName,
                color = DarkAccent,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        Box(
            contentAlignment = if (message.sent) Alignment.BottomEnd else Alignment.BottomStart
        ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!isSelectionMode && !message.isDeleted && offsetX > 80f) onSwipeReply(message)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isSelectionMode && !message.isDeleted && dragAmount > 0) offsetX = (offsetX + dragAmount).coerceIn(0f, 100f)
                        }
                    )
                }
                // Copy Message feature: kahin bhi bubble pe double-tap = clipboard copy, koi UI change nahi
                .pointerInput(message.id, isSelectionMode) {
                    detectTapGestures(
                        onLongPress = { if (!message.isDeleted) onLongPress(message) },
                        onTap = { tapOffset ->
                            if (isSelectionMode) {
                                onTap()
                            } else if (!message.isDeleted && message.text.isNotBlank()) {
                                val layout = textLayoutResult
                                if (layout != null) {
                                    val charOffset = layout.getOffsetForPosition(tapOffset)
                                    val link = annotatedText.getStringAnnotations(LINK_TAG, charOffset, charOffset).firstOrNull()
                                    if (link != null) onLinkTap(link.item)
                                }
                            }
                        },
                        onDoubleTap = {
                            if (!isSelectionMode && !message.isDeleted) {
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
                .background(if (isSticker) Color.Transparent else if (message.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(
                    horizontal = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else 14.dp,
                    vertical = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else 10.dp
                )
                .let {
                    if (message.sent && message.status == "FAILED" && !message.isDeleted)
                        it.clickable { if (isSelectionMode) onTap() else onRetry(message) }
                    else it
                }
        ) {
            if (message.isDeleted) {
                // Tombstone bubble — "delete for everyone" ka result, WhatsApp jaisa
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = "Deleted",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "This message was deleted",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message.time,
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
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
                    Spacer(Modifier.height(2.dp))
                }

                when (message.type) {
                    "image" -> message.mediaUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Image",
                            modifier = Modifier
                                .widthIn(min = 120.dp, max = 200.dp)
                                .heightIn(min = 120.dp, max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { if (isSelectionMode) onTap() else onImageTap(url) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    "gif" -> message.mediaUrl?.let { url ->
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Sticker",
                                modifier = Modifier
                                    .sizeIn(minWidth = 140.dp, minHeight = 140.dp, maxWidth = 180.dp, maxHeight = 180.dp)
                                    .clickable { if (isSelectionMode) onTap() },
                                contentScale = ContentScale.Fit
                            )
                            Row(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(message.time, color = Color.White, fontSize = 10.sp)
                                if (message.sent) {
                                    Spacer(Modifier.width(3.dp))
                                    val (icon, tint) = when (message.status) {
                                        "UPLOADING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                                        "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                                        else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                                    }
                                    Icon(icon, contentDescription = message.status, tint = tint, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
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
                            AsyncImage(
                                model = url,
                                contentDescription = "Video thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Play video",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    "audio", "music" -> message.mediaUrl?.let { url ->
                        AudioMessagePlayer(url = url, sent = message.sent)
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
                        Text(
                            text = annotatedText,
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                            onTextLayout = { textLayoutResult = it }
                        )
                    }
                }

                if (!message.previewUrl.isNullOrBlank() &&
                    (!message.previewTitle.isNullOrBlank() || !message.previewImage.isNullOrBlank())
                ) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkInputBg)
                            .clickable(enabled = !isSelectionMode) { onLinkTap(message.previewUrl) }
                    ) {
                        if (!message.previewImage.isNullOrBlank()) {
                            AsyncImage(
                                model = message.previewImage,
                                contentDescription = "Link preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (!message.previewTitle.isNullOrBlank()) {
                                Text(
                                    message.previewTitle,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!message.previewDescription.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    message.previewDescription,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (!isSticker) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            "edited",
                            color = Color(0x88FFFFFF),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        message.time,
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp
                    )
                    if (message.sent) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, tint) = when (message.status) {
                            "UPLOADING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
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

                // Real-time upload % layer — jab tak upload chal raha hai tabhi dikhta hai,
                // complete hote hi (status UPLOADING se hat jaate hi) automatically gayab
                if (message.status == "UPLOADING") {
                    val pct = UploadProgressTracker.progress[message.id]
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF7A1A))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (pct ?: 0) / 100f },
                            modifier = Modifier
                                .width(70.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color(0x55FFFFFF)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${pct ?: 0}%",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                }
            }
            }
            }

            }

            if (message.reactions.isNotEmpty()) {
                val totalCount = message.reactions.sumOf { it.userIds.size }
                Row(
                    modifier = Modifier
                        .align(if (message.sent) Alignment.End else Alignment.Start)
                        .offset(
                            x = if (message.sent) (-6).dp else 6.dp,
                            y = (-10).dp
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A2A45))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    message.reactions.take(3).forEach { r ->
                        Text(
                            r.emoji,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onReactionLongPress(message.id, r.emoji) }
                                .padding(horizontal = 1.dp)
                        )
                    }
                    if (totalCount > 1) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "$totalCount",
                            color = Color(0xAAFFFFFF),
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            }
        }
        }
    }
