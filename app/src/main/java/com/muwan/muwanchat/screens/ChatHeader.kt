package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader

@Composable
fun ChatHeader(
    receiverUsername: String,
    isOnline: Boolean,
    isTyping: Boolean = false,
    avatarBase64: String? = null,
    onBack: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkHeader)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = receiverUsername,
                size = 38.dp,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    receiverUsername,
                    color = DarkAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = when {
                    isTyping -> "typing..."
                    isOnline -> "Online"
                    else -> "Offline"
                }
                val statusColor = when {
                    isTyping -> DarkAccent
                    isOnline -> Color(0xFF4CD964)
                    else -> Color(0xFF888888)
                }
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row {
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
