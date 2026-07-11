package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import android.net.Uri
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader

@Composable
fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    showEmojiPicker: Boolean,
    onToggleEmojiPicker: () -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
    onVoiceMessage: () -> Unit,
    onGifReceived: (Uri, String, () -> Unit) -> Unit = { _, _, release -> release() }
) {
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
            IconButton(
                onClick = onToggleEmojiPicker,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (showEmojiPicker) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions,
                    contentDescription = "Emoji",
                    tint = if (showEmojiPicker) DarkAccent else Color(0xFF888888),
                    modifier = Modifier.size(20.dp)
                )
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                factory = { ctx ->
                    GifAwareEditText(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(android.graphics.Color.WHITE)
                        setHintTextColor(android.graphics.Color.parseColor("#888888"))
                        setHighlightColor(android.graphics.Color.parseColor("#55FF7043"))
                        hint = "Message..."
                        textSize = 14f
                        setPadding(0, 0, 0, 0)
                        isSingleLine = false
                        maxLines = 4
                        addTextChangedListener { editable ->
                            val text = editable?.toString() ?: ""
                            if (text != input) onInputChange(text)
                        }
                        onContentReceived = { uri, mime, release ->
                            onGifReceived(uri, mime, release)
                        }
                    }
                },
                update = { view ->
                    if (view.text.toString() != input) {
                        view.setText(input)
                        view.setSelection(input.length.coerceIn(0, view.text.length))
                    }
                }
            )

            IconButton(
                onClick = onPickImage,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.Image, contentDescription = "Photo",
                    tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
            }
        }

        FloatingActionButton(
            onClick = { if (input.isBlank()) onVoiceMessage() else onSend() },
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
