package com.muwan.muwanchat.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.data.AppLockStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UnlockErrorRed = Color(0xFFFF3B30)

/**
 * Full-screen lock gate drawn above the whole app by MainActivity while locked.
 * Shows the pattern grid or the PIN pad depending on the active lock method.
 * Handles wrong-attempt counting, the lockout countdown and the final logout.
 * (File keeps its original name; it now serves both methods.)
 */
@Composable
fun PatternUnlockScreen(
    onUnlocked: () -> Unit,
    onLogoutRequired: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var lockedMs by remember { mutableLongStateOf(AppLockStore.remainingLockMs(context)) }
    var loggingOut by remember { mutableStateOf(false) }
    var failedRounds by remember { mutableIntStateOf(AppLockStore.failedRounds(context)) }

    val usePin = remember { AppLockStore.isPinEnabled(context) }
    val pinLen = remember { AppLockStore.pinLength(context) }
    var entered by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val lockActive = lockedMs > 0L

    // Shared result handling for both methods.
    fun handleResult(result: AppLockStore.VerifyResult) {
        val what = if (usePin) "PIN" else "pattern"
        when (result) {
            is AppLockStore.VerifyResult.Success -> onUnlocked()
            is AppLockStore.VerifyResult.Wrong -> {
                isError = true
                val n = result.attemptsLeft
                message = "Wrong $what. $n ${if (n == 1) "attempt" else "attempts"} left"
            }
            is AppLockStore.VerifyResult.Locked -> {
                isError = true
                message = null
                failedRounds = AppLockStore.failedRounds(context)
                lockedMs = result.remainingMs
            }
            is AppLockStore.VerifyResult.LogoutRequired -> {
                isError = true
                loggingOut = true
                onLogoutRequired()
            }
        }
        if (usePin && result !is AppLockStore.VerifyResult.Success) {
            // Show the red dots briefly, then clear for the next try.
            scope.launch {
                delay(if (result is AppLockStore.VerifyResult.Wrong) 450 else 0)
                entered = ""
                isError = false
            }
        }
    }

    // Live countdown while blocked.
    LaunchedEffect(lockActive) {
        while (AppLockStore.remainingLockMs(context) > 0L) {
            lockedMs = AppLockStore.remainingLockMs(context)
            delay(500)
        }
        lockedMs = 0L
        if (lockActive) {
            isError = false
            message = null
        }
    }

    // Leaving the lock screen with Back sends the app to the background instead of
    // revealing what's underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            // Swallow every tap so nothing underneath the gate can be touched.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DarkAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = DarkAccent)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "TalkWave is locked",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))

            val status: String
            val statusColor: Color
            when {
                loggingOut -> {
                    status = "Too many failed attempts. Logging you out…"
                    statusColor = UnlockErrorRed
                }
                lockActive -> {
                    val totalSec = (lockedMs + 999) / 1000
                    status = "Too many attempts. Try again in %02d:%02d".format(totalSec / 60, totalSec % 60)
                    statusColor = UnlockErrorRed
                }
                message != null -> {
                    status = message!!
                    statusColor = UnlockErrorRed
                }
                else -> {
                    status = if (usePin) "Enter your PIN to unlock" else "Draw your pattern to unlock"
                    statusColor = Color(0xFFAAAAAA)
                }
            }
            Text(status, color = statusColor, fontSize = 14.sp, textAlign = TextAlign.Center)

            if (failedRounds >= 2 && !loggingOut) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Warning: ${AppLockStore.MAX_ATTEMPTS} more wrong attempts will log you out.",
                    color = DarkAccent,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            if (usePin) {
                PinDots(filled = entered.length, total = pinLen, isError = isError)
                Spacer(Modifier.height(32.dp))
                PinPad(
                    enabled = !lockActive && !loggingOut,
                    onDigit = { d ->
                        if (entered.length < pinLen) {
                            if (entered.isEmpty()) {
                                isError = false
                                message = null
                            }
                            entered += d
                            if (entered.length == pinLen) {
                                handleResult(AppLockStore.verifyPin(context, entered))
                            }
                        }
                    },
                    onBackspace = {
                        if (entered.isNotEmpty()) entered = entered.dropLast(1)
                    }
                )
            } else {
                PatternLockView(
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                    enabled = !lockActive && !loggingOut,
                    isError = isError,
                    onPatternStart = {
                        isError = false
                        message = null
                    },
                    onPatternComplete = { pattern ->
                        if (pattern.size < AppLockStore.MIN_DOTS) {
                            // Too short to be a real attempt — don't count it.
                            isError = true
                            message = "Connect at least ${AppLockStore.MIN_DOTS} dots"
                        } else {
                            handleResult(AppLockStore.verifyPattern(context, pattern))
                        }
                    }
                )
            }
        }
    }
}
