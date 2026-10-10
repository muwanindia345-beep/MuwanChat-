package com.muwan.muwanchat.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.*
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
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AppLockStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PinSetErrorRed = Color(0xFFFF3B30)

/**
 * PIN setup: step 1 enter (4-6 digits, tap Continue) -> step 2 confirm
 * (auto-submits at the same length) -> step 3 success.
 * The PIN is only saved once the confirmation matches.
 */
@Composable
fun SetPinScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) }
    var firstPin by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var wrongFlash by remember { mutableStateOf(false) }

    fun startOver() {
        firstPin = ""
        entered = ""
        error = null
        wrongFlash = false
        step = 1
    }

    BackHandler(enabled = step == 2) { startOver() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        // Header — same fixed style/color used across every other settings screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (step == 2) startOver() else navController.popBackStack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                if (step == 2) "Confirm PIN" else "Set PIN",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        if (step == 3) {
            PinSuccess(onDone = { navController.popBackStack() })
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Dialpad, contentDescription = null, tint = DarkAccent)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (step == 1) "Enter a new PIN" else "Enter it again to confirm",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: if (step == 1)
                        "Use ${AppLockStore.MIN_PIN}–${AppLockStore.MAX_PIN} digits"
                    else
                        "Repeat the same PIN to save it",
                    color = if (error != null) PinSetErrorRed else Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))

                val totalDots = if (step == 1)
                    maxOf(entered.length, AppLockStore.MIN_PIN)
                else firstPin.length
                PinDots(filled = entered.length, total = totalDots, isError = wrongFlash)

                Spacer(Modifier.height(28.dp))

                PinPad(
                    enabled = !wrongFlash,
                    onDigit = { d ->
                        if (step == 1) {
                            if (entered.length < AppLockStore.MAX_PIN) {
                                error = null
                                entered += d
                            }
                        } else if (entered.length < firstPin.length) {
                            if (entered.isEmpty()) error = null
                            entered += d
                            if (entered.length == firstPin.length) {
                                if (entered == firstPin) {
                                    AppLockStore.savePin(context, entered)
                                    entered = ""
                                    step = 3
                                } else {
                                    error = "PINs don't match. Try again."
                                    wrongFlash = true
                                    scope.launch {
                                        delay(450)
                                        entered = ""
                                        wrongFlash = false
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (entered.isNotEmpty()) entered = entered.dropLast(1)
                    }
                )

                Spacer(Modifier.height(16.dp))
                if (step == 1) {
                    Button(
                        onClick = {
                            firstPin = entered
                            entered = ""
                            error = null
                            step = 2
                        },
                        enabled = entered.length >= AppLockStore.MIN_PIN,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkAccent,
                            disabledContainerColor = DarkAccent.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth()
                    ) { Text("Continue", color = Color.White) }
                } else {
                    TextButton(onClick = { startOver() }) {
                        Text("Start over", color = DarkAccent)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Step $step of 2", color = Color(0xFF888888), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ColumnScope.PinSuccess(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(DarkAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = DarkAccent,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("PIN lock applied", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "TalkWave will ask for your PIN when you open the app.",
            color = Color(0xFF888888),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Done", color = Color.White) }
    }
}
