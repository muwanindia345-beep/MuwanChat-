package com.muwan.muwanchat.screens

// GROUP_CALL_V1_ANDROID (step B)
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.gson.Gson
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.calling.ActiveGroupCall
import com.muwan.muwanchat.calling.GroupCallPhase
import com.muwan.muwanchat.calling.GroupParticipant
import com.muwan.muwanchat.calling.PeerLink
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Group voice call screen. Pure UI: all call state and logic live in ActiveGroupCall,
 * so leaving this screen (back) never ends the call.
 *
 * Layout follows the 1:1 video call screen: solid header bar (name + timer), solid bottom
 * bar (controls), and the content in between. In the middle every participant, including
 * the user, gets one window (profile photo + name); the grid scrolls when there are many.
 */
@Composable
fun GroupCallScreen(
    navController: NavController,
    roomId: String,
    groupName: String,
    isIncomingRoute: Boolean
) {
    val context = LocalContext.current
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }

    val phase by ActiveGroupCall.phase.collectAsState()
    val session by ActiveGroupCall.session.collectAsState()
    val participants by ActiveGroupCall.participants.collectAsState()
    val isMuted by ActiveGroupCall.muted.collectAsState()
    val isSpeakerOn by ActiveGroupCall.speakerOn.collectAsState()
    val connectedAt by ActiveGroupCall.connectedAt.collectAsState()

    // Show what the real call is (not the route args), so coming back from a banner shows the right group.
    val shownName = session?.groupName ?: groupName
    val shownRoomId = session?.roomId ?: roomId

    var group by remember { mutableStateOf<GroupData?>(null) }
    var memberAvatars by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var seconds by remember { mutableStateOf(0) }
    var sawActive by remember { mutableStateOf(false) }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMic = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission is needed to join calls", Toast.LENGTH_SHORT).show()
            // Outgoing: nothing to do without the mic. Incoming: stay on the ring, the user can still decline.
            if (!isIncomingRoute && ActiveGroupCall.phase.value == GroupCallPhase.IDLE) {
                navController.popBackStack()
            }
        } else if (ActiveGroupCall.phase.value == GroupCallPhase.RINGING_INCOMING) {
            ActiveGroupCall.join()
        }
    }

    // Outgoing: ask for the mic first, then start the call. If a call of this group already
    // runs (user came back to the screen) just attach to it instead of starting a new one.
    LaunchedEffect(Unit) {
        if (!isIncomingRoute && !hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(hasMic) {
        if (!isIncomingRoute && hasMic) {
            val cur = ActiveGroupCall.session.value
            if (cur == null) {
                if (!ActiveGroupCall.startCall(roomId, groupName)) {
                    Toast.makeText(context, "You are already on a call", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            } else if (cur.roomId != roomId) {
                Toast.makeText(context, "You are already on a call", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
    }

    // Call finished (IDLE) -> close the screen. Not before it has been active once,
    // otherwise the initial IDLE would close it immediately.
    LaunchedEffect(phase) {
        if (phase != GroupCallPhase.IDLE) {
            sawActive = true
        } else if (sawActive) {
            navController.popBackStack()
        }
    }

    // Stale incoming screen (the ring is already gone): do not stay on an empty screen
    LaunchedEffect(Unit) {
        if (isIncomingRoute) {
            delay(1500)
            if (ActiveGroupCall.phase.value == GroupCallPhase.IDLE && !sawActive) navController.popBackStack()
        }
    }

    // Back on the incoming ring = decline (local only)
    BackHandler(enabled = phase == GroupCallPhase.RINGING_INCOMING) {
        ActiveGroupCall.decline()
    }

    LaunchedEffect(phase, connectedAt) {
        if (phase == GroupCallPhase.ONGOING && connectedAt > 0L) {
            while (true) {
                seconds = ((SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt()
                delay(500)
            }
        }
    }

    // Member photos/names: local cache first (instant), then a fresh copy from the server.
    LaunchedEffect(shownRoomId) {
        if (shownRoomId.isBlank()) return@LaunchedEffect
        try {
            val cached = db.groupInfoCacheDao().get(shownRoomId)
            if (cached != null) {
                val g = Gson().fromJson(cached.json, GroupData::class.java)
                group = g
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberNames = g.memberProfiles.associate { it.uid to it.username }
            }
        } catch (_: Exception) {
        }
        try {
            val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
            val fresh = RetrofitClient.chatApi.getGroup("Bearer $token", shownRoomId).body()?.group
            if (fresh != null) {
                group = fresh
                memberAvatars = fresh.memberProfiles.associate { it.uid to it.avatar }
                memberNames = fresh.memberProfiles.associate { it.uid to it.username }
            }
        } catch (_: Exception) {
        }
    }

    fun joinNow() {
        if (!hasMic) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            ActiveGroupCall.join()
        }
    }

    val topBarH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomBarH = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp

    val subtitle = when (phase) {
        GroupCallPhase.IDLE -> if (sawActive) "Call ended" else "Connecting..."
        GroupCallPhase.RINGING_INCOMING -> "Group voice call"
        GroupCallPhase.CONNECTING -> "Connecting..."
        GroupCallPhase.ONGOING ->
            if (participants.size <= 1) "Waiting for others..."
            else formatGroupDuration(seconds) + "  ·  " + participants.size + " in call"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Same background as the 1:1 call screen
        WallpaperPreviewBackground(entity = null)
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))

        if (phase == GroupCallPhase.RINGING_INCOMING) {
            val caller = session?.startedByUsername.orEmpty()
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AvatarView(
                    avatarBase64 = group?.avatar,
                    fallbackText = shownName,
                    size = 120.dp,
                    fontSize = 42.sp
                )
                Spacer(Modifier.height(20.dp))
                Text(shownName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (caller.isBlank()) "Incoming group voice call"
                    else "$caller started a group voice call",
                    color = Color(0xFFAAAAAA),
                    fontSize = 15.sp
                )
            }
        } else {
            val single = participants.size <= 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (single) 1 else 2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarH, bottom = bottomBarH),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(participants, key = { it.uid }) { p ->
                    ParticipantTile(
                        p = p,
                        displayName = p.username.ifBlank { memberNames[p.uid] ?: "..." },
                        avatar = memberAvatars[p.uid],
                        single = single
                    )
                }
            }
        }

        // Solid header bar (same as the video call screen): group name + timer
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBarH)
                .background(DarkHeader)
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                shownName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 64.dp)
            )
            Text(text = subtitle, color = Color(0xFFDDDDDD), fontSize = 13.sp)
        }
        IconButton(
            onClick = {
                // Incoming ring: back = decline. Otherwise just leave the screen, the call keeps running.
                if (phase == GroupCallPhase.RINGING_INCOMING) {
                    ActiveGroupCall.decline()
                } else {
                    navController.popBackStack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Solid bottom bar with the controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomBarH)
                .background(DarkSheet)
        )
        if (phase == GroupCallPhase.RINGING_INCOMING) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupCallControlButton(
                    icon = Icons.Filled.CallEnd,
                    background = Color(0xFFFF3B30),
                    onClick = { ActiveGroupCall.decline() }
                )
                GroupCallControlButton(
                    icon = Icons.Filled.Call,
                    background = Color(0xFF34C759),
                    onClick = { joinNow() }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupCallControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    background = Color(0xFF2A2A2A),
                    onClick = { ActiveGroupCall.toggleMute() }
                )
                GroupCallControlButton(
                    icon = Icons.Filled.CallEnd,
                    background = Color(0xFFFF3B30),
                    size = 64.dp,
                    iconSize = 30.dp,
                    onClick = { ActiveGroupCall.leave() }
                )
                GroupCallControlButton(
                    icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    background = Color(0xFF2A2A2A),
                    onClick = { ActiveGroupCall.toggleSpeaker() }
                )
            }
        }
    }
}

/** One participant window: photo, name, mute badge, and an orange ring while the person speaks. */
@Composable
private fun ParticipantTile(
    p: GroupParticipant,
    displayName: String,
    avatar: String?,
    single: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val ring by animateColorAsState(
        targetValue = if (p.speaking) DarkAccent else Color.Transparent,
        animationSpec = tween(150),
        label = "speakRing"
    )
    val avatarScale by animateFloatAsState(
        targetValue = if (p.speaking) 1.06f else 1f,
        animationSpec = tween(150),
        label = "speakScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (single) 1.3f else 1f)
            .clip(shape)
            .background(DarkSheet)
            .border(3.dp, ring, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(modifier = Modifier.scale(avatarScale)) {
                AvatarView(
                    avatarBase64 = avatar,
                    fallbackText = displayName,
                    size = if (single) 110.dp else 76.dp,
                    fontSize = if (single) 40.sp else 28.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (p.isSelf) "$displayName (You)" else displayName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (p.link) {
                PeerLink.CONNECTING ->
                    if (!p.isSelf) Text("Connecting...", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                PeerLink.FAILED ->
                    Text("Connection lost", color = Color(0xFFFF3B30), fontSize = 12.sp)
                PeerLink.CONNECTED -> {}
            }
        }
        if (p.muted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .background(Color(0xFFFF3B30), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MicOff,
                    contentDescription = "Muted",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupCallControlButton(
    icon: ImageVector,
    background: Color,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

private fun formatGroupDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
