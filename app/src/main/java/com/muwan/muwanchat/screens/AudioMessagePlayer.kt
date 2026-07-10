package com.muwan.muwanchat.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import kotlinx.coroutines.delay

// Message bubble ke andar voice note player — play/pause button + animated
// progress bar. Bubble ka hi color theme use karta hai (sent = white on
// orange, received = accent on navy) taaki UI bilkul consistent lage.
@Composable
fun AudioMessagePlayer(url: String, sent: Boolean) {
    var isPlaying by remember(url) { mutableStateOf(false) }
    var isPrepared by remember(url) { mutableStateOf(false) }
    var durationMs by remember(url) { mutableStateOf(0) }
    var positionMs by remember(url) { mutableStateOf(0) }

    val player = remember(url) { MediaPlayer() }

    DisposableEffect(url) {
        try {
            player.setDataSource(url)
            player.setOnPreparedListener {
                isPrepared = true
                durationMs = it.duration
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
            }
            player.prepareAsync()
        } catch (_: Exception) {}
        onDispose {
            try { player.release() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(150)
            try { positionMs = player.currentPosition } catch (_: Exception) { break }
        }
    }

    val accent = if (sent) Color.White else DarkAccent
    val trackBg = if (sent) Color(0x33FFFFFF) else Color(0xFF0f3460)

    Row(
        modifier = Modifier
            .padding(6.dp)
            .widthIn(min = 180.dp, max = 220.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .clickable(enabled = isPrepared) {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.start()
                        isPlaying = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(trackBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
            Spacer(Modifier.height(4.dp))
            val shownMs = if (isPlaying || positionMs > 0) positionMs else durationMs
            Text(
                formatAudioTime(shownMs),
                color = accent.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatAudioTime(ms: Int): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
