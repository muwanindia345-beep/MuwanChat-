package com.muwan.muwanchat.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// UPLOAD_RING_FIX: photo/video bubble ke center mein upload progress ring + percentage.
@Composable
fun UploadProgressRing(percent: Int?, modifier: Modifier = Modifier) {
    val pct = (percent ?: 0).coerceIn(0, 100)
    val animated by animateFloatAsState(
        targetValue = pct / 100f,
        animationSpec = tween(durationMillis = 150),
        label = "uploadRing"
    )
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxSize().padding(5.dp),
            color = Color(0xFFFF7A1A),
            strokeWidth = 4.dp,
            trackColor = Color(0x40FFFFFF)
        )
        Text(
            "$pct%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
