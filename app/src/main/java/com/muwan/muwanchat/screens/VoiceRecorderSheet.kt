package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.data.AudioRecorder
import java.io.File

// Voice message recording bottom sheet — WhatsApp jaisi UX: live waveform,
// timer, delete (cancel), pause/resume, aur send. App ke dark+orange theme
// se match karta hai, koi outer UI (header/list) isse touch nahi hota.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderSheet(
    recorder: AudioRecorder,
    onCancel: () -> Unit,
    onSend: (File) -> Unit
) {
    var isPaused by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }
    val elapsedMs by recorder.elapsedMillis.collectAsState()
    val amplitude by recorder.amplitude.collectAsState()

    val bars = remember { mutableStateListOf<Float>() }
    LaunchedEffect(amplitude) {
        if (hasStarted && !isPaused) {
            val normalized = (amplitude / 15000f).coerceIn(0.08f, 1f)
            bars.add(normalized)
            if (bars.size > 40) bars.removeAt(0)
        }
    }

    LaunchedEffect(Unit) {
        recorder.start()
        hasStarted = true
    }

    ModalBottomSheet(
        onDismissRequest = { recorder.cancel(); onCancel() },
        containerColor = DarkHeader
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isPaused) Color(0xFF888888) else Color(0xFFE53935),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatVoiceTimer(elapsedMs),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(14.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkInputBg)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    bars.forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(h)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isPaused) Color(0xFF666688) else DarkAccent)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { recorder.cancel(); onCancel() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkInputBg)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Cancel",
                        tint = Color(0xFFE05555),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isPaused) { recorder.resume(); isPaused = false }
                        else { recorder.pause(); isPaused = true }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkInputBg)
                ) {
                    Icon(
                        if (isPaused) Icons.Filled.Mic else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val file = recorder.stopAndGetFile()
                        if (file != null && elapsedMs > 500) onSend(file) else onCancel()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkAccent)
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun formatVoiceTimer(ms: Int): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
