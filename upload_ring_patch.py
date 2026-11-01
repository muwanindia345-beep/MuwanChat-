import sys, os

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens")
MB = os.path.join(BASE, "MessageBubble.kt")
s = open(MB, encoding="utf-8").read()

if "UPLOAD_RING_FIX" in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: matched %d times" % (name, n))
        sys.exit(1)
    return s.replace(old, new)

s = rep(s,
'''                    "image" -> message.mediaUrl?.let { url ->
                        AsyncImage(''',
'''                    "image" -> message.mediaUrl?.let { url ->
                        // UPLOAD_RING_FIX: upload ke waqt progress ring image ke center mein
                        Box(contentAlignment = Alignment.Center) {
                        AsyncImage(''', "image open")
s = rep(s,
'''                            contentScale = if (isChannel) ContentScale.FillWidth else ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))''',
'''                            contentScale = if (isChannel) ContentScale.FillWidth else ContentScale.Crop
                        )
                        if (message.status == "UPLOADING") {
                            UploadProgressRing(UploadProgressTracker.progress[message.id])
                        }
                        }
                        Spacer(Modifier.height(4.dp))''', "image close")

s = rep(s,
'''                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Play video",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )''',
'''                            if (message.status == "UPLOADING") {
                                UploadProgressRing(UploadProgressTracker.progress[message.id])
                            } else {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }''', "video play icon")

s = rep(s,
'''                if (message.status == "UPLOADING") {
                    val pct = UploadProgressTracker.progress[message.id]''',
'''                if (message.status == "UPLOADING" &&
                    !((message.type == "image" || message.type == "video") && message.mediaUrl != null)
                ) {
                    val pct = UploadProgressTracker.progress[message.id]''', "bottom bar")

open(MB, "w", encoding="utf-8").write(s)
print("OK: MessageBubble.kt")

ring = '''package com.muwan.muwanchat.screens

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
'''
open(os.path.join(BASE, "UploadProgressRing.kt"), "w", encoding="utf-8").write(ring)
print("OK: UploadProgressRing.kt (new)")
print("DONE")
