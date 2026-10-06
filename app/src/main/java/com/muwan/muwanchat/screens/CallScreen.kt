package com.muwan.muwanchat.screens

// STEP2_ACTIVE_CALL
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.muwan.muwanchat.calling.ActiveCall
import com.muwan.muwanchat.calling.CallControlEvents
import com.muwan.muwanchat.calling.CallPhase
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// Voice call screen -- ab sirf UI hai. Call ki asli state/logic ActiveCall
// (app-level) mein hai, isliye back dabane ya doosre screen pe jaane se call
// nahi katti. Call sirf End / Decline button se (ya doosre side se) khatam hoti hai.
@Composable
fun CallScreen(
    navController: NavController,
    otherUid: String,
    otherUsername: String,
    callType: String,
    isIncoming: Boolean
) {
    val context = LocalContext.current
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }

    val phase by ActiveCall.phase.collectAsState()
    val info by ActiveCall.info.collectAsState()
    val isMuted by ActiveCall.muted.collectAsState()
    val isSpeakerOn by ActiveCall.speakerOn.collectAsState()
    val connectedAt by ActiveCall.connectedAt.collectAsState()

    // Screen pe wahi dikhao jo asli call hai (route args se nahi) -- banner/notification
    // se wapas aane par bhi sahi naam aur photo dikhe.
    val shownUid = info?.otherUid ?: otherUid
    val shownName = info?.otherUsername ?: otherUsername

    var avatarBase64 by remember { mutableStateOf<String?>(null) }
    var isAvatarLoading by remember { mutableStateOf(true) }
    var durationSeconds by remember { mutableStateOf(0) }
    var sawActive by remember { mutableStateOf(false) }
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
            if (isIncoming && ActiveCall.isActive) {
                ActiveCall.decline() // IDLE hote hi neeche wala effect screen band kar dega
            } else {
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Outgoing: permission milte hi call shuru. Pehle se call chal rahi ho (banner/chat se
    // wapas aaye) to nayi call mat banao -- bas screen usi call se jud jaati hai.
    LaunchedEffect(hasMicPermission) {
        if (!isIncoming && hasMicPermission) {
            val cur = ActiveCall.info.value
            if (cur == null) {
                if (!ActiveCall.startOutgoing(otherUid, otherUsername, callType)) {
                    Toast.makeText(context, "Could not start call", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            } else if (cur.otherUid != otherUid) {
                Toast.makeText(context, "You are already on a call", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Call khatam (IDLE) hote hi screen band. sawActive ke bina nahi, warna start hone se
    // pehle ka IDLE galti se screen band kar deta.
    LaunchedEffect(phase) {
        if (phase != CallPhase.IDLE) {
            sawActive = true
        } else if (sawActive) {
            navController.popBackStack()
        }
    }

    // Purani/stale incoming screen (call ab hai hi nahi) -- khali screen pe mat atko
    LaunchedEffect(Unit) {
        if (isIncoming) {
            delay(1500)
            if (ActiveCall.phase.value == CallPhase.IDLE && !sawActive) navController.popBackStack()
        }
    }

    // Incoming ringing mein back = decline (pehle bhi back se call khatam hoti thi)
    BackHandler(enabled = phase == CallPhase.RINGING_INCOMING) {
        ActiveCall.decline()
    }

    // Notification se "Answer" dabaya -- dobara Accept dabane ki zaroorat nahi
    LaunchedEffect(hasMicPermission, phase) {
        val id = info?.callId
        if (isIncoming && hasMicPermission && phase == CallPhase.RINGING_INCOMING &&
            id != null && CallControlEvents.answerRequestedCallId == id
        ) {
            ActiveCall.accept()
        }
    }
    LaunchedEffect(Unit) {
        CallControlEvents.answeredFromNotification.collect { answeredId ->
            if (hasMicPermission &&
                ActiveCall.info.value?.callId == answeredId &&
                ActiveCall.phase.value == CallPhase.RINGING_INCOMING
            ) {
                ActiveCall.accept()
            }
        }
    }

    // Duration: connectedAt (ActiveCall) se nikalta hai, isliye screen dobara khulne par
    // bhi timer sahi chalta hai.
    LaunchedEffect(phase, connectedAt) {
        if (phase == CallPhase.ONGOING && connectedAt > 0L) {
            while (true) {
                durationSeconds = ((SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt()
                delay(500)
            }
        }
    }

    // Cache se turant dikhao (agar hai), phir background me API se fresh photo
    // laao -- pehle sirf cache padhte the isliye pehli-baar-call-karne pe
    // (jab profile pehle kabhi khola na ho) avatar kabhi load hi nahi hota tha.
    LaunchedEffect(shownUid) {
        val cached = db.cachedUserProfileDao().get(shownUid)
        if (cached?.avatar != null) {
            avatarBase64 = cached.avatar
            isAvatarLoading = false
        }
        try {
            val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
            val res = RetrofitClient.usersApi.getUserByUid("Bearer $token", shownUid)
            val fresh = res.body()?.user
            if (fresh != null) {
                avatarBase64 = fresh.avatar
                db.cachedUserProfileDao().upsert(
                    com.muwan.muwanchat.data.CachedUserProfileEntity(
                        uid = fresh.uid,
                        username = fresh.username,
                        name = fresh.name,
                        bio = fresh.bio,
                        city = fresh.city,
                        country = fresh.country,
                        gender = fresh.gender,
                        avatar = fresh.avatar,
                        status = cached?.status ?: "none"
                    )
                )
            }
        } catch (_: Exception) {
            // Network fail ho toh bhi loader hata do -- fallback letter-avatar dikh jaayega
        } finally {
            isAvatarLoading = false
        }
    }

    fun acceptCall() {
        if (hasMicPermission) {
            ActiveCall.accept()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background: chat/group ka default space wallpaper, consistency ke liye
        WallpaperPreviewBackground(entity = null)
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))

        IconButton(
            onClick = {
                // Incoming ringing mein back = decline (screen effect se band hogi);
                // baaki sab mein sirf screen se bahar -- call chalti rehti hai.
                if (phase == CallPhase.RINGING_INCOMING) {
                    ActiveCall.decline()
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

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                AvatarView(
                    avatarBase64 = avatarBase64,
                    fallbackText = shownName,
                    size = 120.dp,
                    fontSize = 42.sp
                )
                // Photo load hone tak fallback-letter ke upar chhota spinner --
                // shared AvatarView component ko touch nahi kiya, sirf yahan
                // ek transparent overlay lagaya hai.
                if (isAvatarLoading && avatarBase64 == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(shownName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (phase) {
                    CallPhase.IDLE -> if (sawActive) "Call ended" else "Connecting..."
                    CallPhase.RINGING_OUTGOING -> "Ringing..."
                    CallPhase.RINGING_INCOMING -> "Incoming voice call"
                    CallPhase.CONNECTING -> "Connecting..."
                    CallPhase.ONGOING -> formatDuration(durationSeconds)
                },
                color = Color(0xFFAAAAAA),
                fontSize = 15.sp
            )
        }

        // Bottom controls -- incoming call ke liye Accept/Decline, baaki sab ke liye Mute/End/Speaker
        if (phase == CallPhase.RINGING_INCOMING) {
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
                    onClick = { ActiveCall.decline() }
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
                    onClick = { ActiveCall.toggleMute() }
                )
                CallControlButton(
                    icon = Icons.Filled.CallEnd,
                    background = Color(0xFFFF3B30),
                    iconTint = Color.White,
                    size = 64.dp,
                    iconSize = 30.dp,
                    onClick = { ActiveCall.end() }
                )
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    background = Color(0xFF2A2A2A),
                    iconTint = Color.White,
                    onClick = { ActiveCall.toggleSpeaker() }
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
