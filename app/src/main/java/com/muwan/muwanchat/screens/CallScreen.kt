package com.muwan.muwanchat.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.muwan.muwanchat.calling.CallManager
import com.muwan.muwanchat.calling.PendingIncomingCall
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.SocketEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private enum class CallState { RINGING_OUTGOING, RINGING_INCOMING, CONNECTING, ONGOING, ENDED }

// Voice call screen -- WebRTC signaling (CallManager) + backend socket
// events (AppSocketManager) dono se real-wired. isIncoming=true tab CallId
// aur SDP PendingIncomingCall se aate hain (NavGraph ke global listener ne
// wahan rakhe the), Accept/Decline UI dikhta hai. isIncoming=false tab yeh
// screen khud offer banake bhejti hai aur "Ringing..." dikhata hai.
@Composable
fun CallScreen(
    navController: NavController,
    otherUid: String,
    otherUsername: String,
    callType: String,
    isIncoming: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }

    val incomingData = remember { if (isIncoming) PendingIncomingCall.data else null }
    LaunchedEffect(Unit) { PendingIncomingCall.data = null } // consume kar liya, dobara use na ho

    val callId = remember { incomingData?.callId ?: UUID.randomUUID().toString() }

    var avatarBase64 by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var callState by remember {
        mutableStateOf(if (isIncoming) CallState.RINGING_INCOMING else CallState.RINGING_OUTGOING)
    }
    var durationSeconds by remember { mutableStateOf(0) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission is needed to make calls", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // CallManager ek hi baar banta hai, screen ke poore lifecycle mein reuse hota hai
    val callManager = remember {
        CallManager(
            context = context,
            onLocalIceCandidate = { candidate ->
                AppSocketManager.sendIceCandidate(callId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            },
            onRemoteAudioTrackAdded = { /* audio apne aap play hota hai WebRTC se, UI change ki zaroorat nahi */ },
            onConnectionFailed = {
                if (callState != CallState.ENDED) {
                    callState = CallState.ENDED
                    navController.popBackStack()
                }
            }
        )
    }
    val ringtoneManager = remember { com.muwan.muwanchat.calling.CallRingtoneManager(context) }

    // Incoming call pe ringtone+vibration, outgoing pe ringback tone -- jaise
    // hi state ringing se aage badhe (connecting/ongoing/ended), band ho jaata hai
    LaunchedEffect(callState) {
        when (callState) {
            CallState.RINGING_INCOMING -> ringtoneManager.startIncomingRing()
            CallState.RINGING_OUTGOING -> ringtoneManager.startOutgoingRingback()
            else -> ringtoneManager.stop()
        }
    }

    // Outgoing call: offer khud banao aur bhejo
    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && !isIncoming && callState == CallState.RINGING_OUTGOING) {
            callManager.init()
            callManager.createOffer { sdp ->
                AppSocketManager.sendCallOffer(callId, otherUid, callType, sdp) { success, error ->
                    if (!success) {
                        Toast.makeText(
                            context,
                            if (error == "busy") "User is on another call" else "Could not start call",
                            Toast.LENGTH_SHORT
                        ).show()
                        navController.popBackStack()
                    }
                }
            }
        } else if (hasMicPermission && isIncoming) {
            callManager.init()
        }
    }

    // Backend se aane wale call events suno
    LaunchedEffect(Unit) {
        AppSocketManager.events.collect { event ->
            when (event) {
                is SocketEvent.CallAnswerReceived -> {
                    if (event.callId == callId && !isIncoming) {
                        callManager.setRemoteAnswer(event.sdp)
                        callState = CallState.ONGOING
                    }
                }
                is SocketEvent.IceCandidateReceived -> {
                    if (event.callId == callId) {
                        callManager.addRemoteIceCandidate(event.sdpMid, event.sdpMLineIndex, event.candidate)
                    }
                }
                is SocketEvent.CallEndReceived -> {
                    if (event.callId == callId) {
                        callState = CallState.ENDED
                        callManager.release()
                        navController.popBackStack()
                    }
                }
                is SocketEvent.CallBusyReceived -> {
                    if (event.callId == callId) {
                        Toast.makeText(context, "User is on another call", Toast.LENGTH_SHORT).show()
                        callState = CallState.ENDED
                        navController.popBackStack()
                    }
                }
                else -> {}
            }
        }
    }

    // Ongoing call ka duration timer
    LaunchedEffect(callState) {
        if (callState == CallState.ONGOING) {
            durationSeconds = 0
            while (true) {
                delay(1000)
                durationSeconds++
            }
        }
    }

    // Screen band ho (back button, ya kisi aur wajah se) toh cleanup zaroor ho
    DisposableEffect(Unit) {
        onDispose {
            if (callState != CallState.ENDED) {
                AppSocketManager.sendCallEnd(callId)
            }
            callManager.release()
            ringtoneManager.stop()
        }
    }

    LaunchedEffect(otherUid) {
        scope.launch {
            val cached = db.cachedUserProfileDao().get(otherUid)
            avatarBase64 = cached?.avatar
        }
    }

    fun endCall() {
        AppSocketManager.sendCallEnd(callId)
        callState = CallState.ENDED
        callManager.release()
        navController.popBackStack()
    }

    fun acceptCall() {
        val data = incomingData ?: return
        callState = CallState.CONNECTING
        callManager.createAnswer(data.sdp) { answerSdp ->
            AppSocketManager.sendCallAnswer(callId, answerSdp)
            callState = CallState.ONGOING
        }
    }

    fun declineCall() {
        AppSocketManager.sendCallReject(callId)
        callState = CallState.ENDED
        callManager.release()
        navController.popBackStack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background: chat/group ka default space wallpaper, consistency ke liye
        WallpaperPreviewBackground(entity = null)
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = otherUsername,
                size = 120.dp,
                fontSize = 42.sp
            )
            Spacer(Modifier.height(20.dp))
            Text(otherUsername, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (callState) {
                    CallState.RINGING_OUTGOING -> "Ringing..."
                    CallState.RINGING_INCOMING -> "Incoming voice call"
                    CallState.CONNECTING -> "Connecting..."
                    CallState.ONGOING -> formatDuration(durationSeconds)
                    CallState.ENDED -> "Call ended"
                },
                color = Color(0xFFAAAAAA),
                fontSize = 15.sp
            )
        }

        // Bottom controls -- incoming call ke liye Accept/Decline, baaki sab ke liye Mute/End/Speaker
        if (callState == CallState.RINGING_INCOMING) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = Icons.Filled.CallEnd,
                    background = Color(0xFFFF3B30),
                    iconTint = Color.White,
                    onClick = { declineCall() }
                )
                CallControlButton(
                    icon = Icons.Filled.Call,
                    background = Color(0xFF34C759),
                    iconTint = Color.White,
                    onClick = { acceptCall() }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    background = Color(0xFF2A2A2A),
                    iconTint = Color.White,
                    onClick = {
                        isMuted = !isMuted
                        callManager.setMuted(isMuted)
                    }
                )
                CallControlButton(
                    icon = Icons.Filled.CallEnd,
                    background = Color(0xFFFF3B30),
                    iconTint = Color.White,
                    size = 64.dp,
                    iconSize = 30.dp,
                    onClick = { endCall() }
                )
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    background = Color(0xFF2A2A2A),
                    iconTint = Color.White,
                    onClick = {
                        isSpeakerOn = !isSpeakerOn
                        callManager.setSpeakerOn(isSpeakerOn)
                    }
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    background: Color,
    iconTint: Color,
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
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
    }
}
