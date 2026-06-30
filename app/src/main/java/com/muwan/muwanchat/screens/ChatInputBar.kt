package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onVoiceMessage: () -> Unit
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

            TextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text("Message...", color = Color(0xFF888888),
                        fontSize = androidx.compose.ui.unit.TextUnit.Unspecified.let { 14.sp },
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
