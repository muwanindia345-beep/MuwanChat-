package com.muwan.muwanchat.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.BuildConfig
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.UpdateManager
import com.muwan.muwanchat.network.AppVersionInfo
import kotlinx.coroutines.launch

private enum class UpdateState { CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, ERROR }

@Composable
fun CheckUpdatesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(UpdateState.CHECKING) }
    var versionInfo by remember { mutableStateOf<AppVersionInfo?>(null) }
    var progress by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val info = UpdateManager.checkForUpdate(context)
        if (info != null) {
            versionInfo = info
            state = UpdateState.AVAILABLE
            UpdateManager.markVersionSeen(context, info.versionCode) // red dot clear
        } else {
            state = UpdateState.UP_TO_DATE
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg).systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Check Updates", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("Current version: ${BuildConfig.VERSION_NAME}", color = Color(0xFF888888), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                UpdateState.CHECKING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = DarkAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Checking for updates...", color = Color.White, fontSize = 15.sp)
                    }
                }

                UpdateState.UP_TO_DATE -> {
                    UpdateStatusButton(text = "Up to date \uD83D\uDC9A", color = Color(0xFF2ECC71), enabled = false)
                }

                UpdateState.AVAILABLE, UpdateState.ERROR -> {
                    versionInfo?.let { info ->
                        Text("New version available: ${info.versionName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("What's new", color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(info.changelog, color = Color(0xFFCCCCCC), fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(24.dp))

                        if (state == UpdateState.ERROR) {
                            Text("Update failed. Check your connection and try again.", color = Color(0xFFFF5555), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        UpdateStatusButton(
                            text = "Update",
                            color = DarkAccent,
                            enabled = true,
                            onClick = {
                                val apkUrl = info.apkUrl ?: return@UpdateStatusButton
                                state = UpdateState.DOWNLOADING
                                progress = 0
                                scope.launch {
                                    try {
                                        UpdateManager.downloadAndInstall(context, apkUrl) { p -> progress = p }
                                    } catch (_: Exception) {
                                        state = UpdateState.ERROR
                                    }
                                }
                            }
                        )
                    }
                }

                UpdateState.DOWNLOADING -> {
                    Text("Downloading update...", color = Color.White, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    VerticalDownloadProgress(progress = progress)
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit = {}) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(
            text,
            color = if (color == Color(0xFF2ECC71)) Color(0xFF0A2A1A) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

// Real-time animated vertical bar + %, jaise-jaise bytes aate hai height badhti hai
@Composable
private fun VerticalDownloadProgress(progress: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 250),
        label = "download_progress"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(14.dp).height(160.dp).background(Color(0xFF2a2a4e))) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .fillMaxHeight(animatedProgress).background(DarkAccent)
            )
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column {
            Text("$progress%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(if (progress < 100) "Downloading..." else "Installing...", color = Color(0xFF888888), fontSize = 13.sp)
        }
    }
}
