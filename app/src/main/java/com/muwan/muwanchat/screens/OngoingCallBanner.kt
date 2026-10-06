package com.muwan.muwanchat.screens

// STEP3_CALL_BANNER
import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.calling.ActiveCall
import com.muwan.muwanchat.calling.CallPhase
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.delay

// Chhota "call chal rahi hai" strip: green dot + naam + live timer + tap to return.
// Sirf tab dikhta hai jab outgoing ringing / connecting / ongoing call ho.
// (Incoming ringing mein CallScreen khud khul jaati hai, isliye wahan nahi.)
@Composable
fun OngoingCallBanner(navController: NavController) {
    val phase by ActiveCall.phase.collectAsState()
    val info by ActiveCall.info.collectAsState()
    val connectedAt by ActiveCall.connectedAt.collectAsState()

    val cur = info
    val visible = cur != null && (
        phase == CallPhase.RINGING_OUTGOING ||
            phase == CallPhase.CONNECTING ||
            phase == CallPhase.ONGOING
        )
    if (!visible || cur == null) return

    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(phase, connectedAt) {
        if (phase == CallPhase.ONGOING && connectedAt > 0L) {
            while (true) {
                seconds = ((SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt()
                delay(500)
            }
        }
    }

    // Green dot halka halka blink karta hai (call "live" hai)
    val transition = rememberInfiniteTransition(label = "callDot")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "callDotAlpha"
    )

    val statusText = when (phase) {
        CallPhase.ONGOING -> formatBannerTime(seconds)
        CallPhase.CONNECTING -> "Connecting..."
        else -> "Calling..."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F3D2B))
            .clickable {
                navController.navigate(
                    Screen.Call.createRoute(
                        uid = cur.otherUid,
                        username = cur.otherUsername,
                        callType = cur.callType,
                        isIncoming = cur.isIncoming
                    )
                )
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .background(Color(0xFF34C759), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            cur.otherUsername,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(statusText, color = Color(0xFF9BE3B5), fontSize = 13.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text("Tap to return", color = Color(0xFF9BE3B5), fontSize = 12.sp)
    }
}

private fun formatBannerTime(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
