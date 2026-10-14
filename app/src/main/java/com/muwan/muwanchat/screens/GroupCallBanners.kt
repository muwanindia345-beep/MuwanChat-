package com.muwan.muwanchat.screens

// GROUP_CALL_V1_ANDROID_C
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.calling.ActiveGroupCall
import com.muwan.muwanchat.calling.GroupCallPhase
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.GroupCallLive
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.delay

// Same strip as OngoingCallBanner (1:1) but for group calls: green dot + group name +
// live timer + tap to return. Shown on the chat list while the user is inside a group call.
@Composable
fun OngoingGroupCallBanner(navController: NavController) {
    val phase by ActiveGroupCall.phase.collectAsState()
    val session by ActiveGroupCall.session.collectAsState()
    val connectedAt by ActiveGroupCall.connectedAt.collectAsState()
    val participants by ActiveGroupCall.participants.collectAsState()

    val cur = session
    val visible = cur != null &&
        (phase == GroupCallPhase.CONNECTING || phase == GroupCallPhase.ONGOING)
    if (!visible || cur == null) return

    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(phase, connectedAt) {
        if (phase == GroupCallPhase.ONGOING && connectedAt > 0L) {
            while (true) {
                seconds = ((SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt()
                delay(500)
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "groupCallDot")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "groupCallDotAlpha"
    )

    val statusText = if (phase == GroupCallPhase.ONGOING) {
        formatBannerDuration(seconds) + "  ·  " + participants.size + " in call"
    } else {
        "Connecting..."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F3D2B))
            .clickable {
                navController.navigate(Screen.GroupCall.createRoute(cur.roomId, cur.groupName, false))
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
            cur.groupName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(statusText, color = Color(0xFF9BE3B5), fontSize = 13.sp, maxLines = 1)
        Spacer(modifier = Modifier.weight(1f))
        Text("Tap to return", color = Color(0xFF9BE3B5), fontSize = 12.sp)
    }
}

// Strip under the group chat header. Shows "Group call in progress - Join" while a call runs
// in this group and the user is not in it, or "Tap to return" when the user is in it.
// Joining = open the group call screen; if a call is already running the server tells us and
// ActiveGroupCall joins it automatically (no second call is created).
@Composable
fun GroupCallStrip(roomId: String, groupName: String, navController: NavController) {
    val phase by ActiveGroupCall.phase.collectAsState()
    val session by ActiveGroupCall.session.collectAsState()
    val myParticipants by ActiveGroupCall.participants.collectAsState()
    var live by remember(roomId) { mutableStateOf<GroupCallLive?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun refresh() {
        AppSocketManager.getGroupCall(roomId) { res -> mainHandler.post { live = res } }
    }

    // The socket may still be connecting right after the chat opens: retry a couple of times.
    LaunchedEffect(roomId) {
        refresh()
        delay(1500)
        if (live == null) refresh()
        delay(2500)
        if (live == null) refresh()
    }
    fun onEvent(e: SocketEvent) {
        when (e) {
            is SocketEvent.GroupCallStarted -> {
                if (e.roomId == roomId) refresh()
            }
            is SocketEvent.GroupCallPeerJoined -> refresh()
            is SocketEvent.GroupCallPeerLeft -> refresh()
            is SocketEvent.GroupCallEnded -> {
                if (e.roomId == roomId) live = null
            }
            else -> {}
        }
    }
    LaunchedEffect(roomId) {
        AppSocketManager.events.collect { e -> onEvent(e) }
    }

    val inThisCall = session?.roomId == roomId &&
        (phase == GroupCallPhase.CONNECTING || phase == GroupCallPhase.ONGOING)
    val ringingHere = session?.roomId == roomId && phase == GroupCallPhase.RINGING_INCOMING
    val l = live
    if (ringingHere) return // the incoming screen is open on top
    if (!inThisCall && l == null) return

    val count = if (inThisCall) myParticipants.size else (l?.participants?.size ?: 0)
    val full = !inThisCall && l != null && l.max > 0 && l.participants.size >= l.max

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F3D2B))
            .clickable(enabled = !full) {
                navController.navigate(Screen.GroupCall.createRoute(roomId, groupName, false))
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFF34C759), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (inThisCall) "You are in this call" else "Group call in progress",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "$count in call",
                color = Color(0xFF9BE3B5),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        if (inThisCall) {
            Text("Tap to return", color = Color(0xFF9BE3B5), fontSize = 12.sp)
        } else {
            Text(
                if (full) "Full" else "Join",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (full) Color(0xFF555555) else DarkAccent)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

private fun formatBannerDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
