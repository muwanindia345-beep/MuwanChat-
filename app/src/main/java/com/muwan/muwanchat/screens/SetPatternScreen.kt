package com.muwan.muwanchat.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pattern
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

private val ErrorRed = Color(0xFFFF3B30)

/**
 * Pattern setup: step 1 draw -> step 2 confirm -> step 3 success.
 * The pattern is only saved once the confirmation matches.
 */
@Composable
fun SetPatternScreen(navController: NavController) {
    val context = LocalContext.current

    var step by remember { mutableIntStateOf(1) }
    var firstPattern by remember { mutableStateOf<List<Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun startOver() {
        firstPattern = null
        error = null
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
                if (step == 2) "Confirm Pattern" else "Set Pattern",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        if (step == 3) {
            PatternSuccess(onDone = { navController.popBackStack() })
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
                    Icon(Icons.Filled.Pattern, contentDescription = null, tint = DarkAccent)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (step == 1) "Draw your pattern" else "Draw it again to confirm",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: if (step == 1)
                        "Connect at least ${AppLockStore.MIN_DOTS} dots"
                    else
                        "Repeat the same pattern to save it",
                    color = if (error != null) ErrorRed else Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                PatternLockView(
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                    isError = error != null,
                    onPatternStart = { error = null },
                    onPatternComplete = { pattern ->
                        if (step == 1) {
                            if (pattern.size < AppLockStore.MIN_DOTS) {
                                error = "Connect at least ${AppLockStore.MIN_DOTS} dots. Try again."
                            } else {
                                firstPattern = pattern
                                step = 2
                            }
                        } else {
                            if (pattern == firstPattern) {
                                AppLockStore.savePattern(context, pattern)
                                step = 3
                            } else {
                                error = "Patterns don't match. Try again."
                            }
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Step $step of 2",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                if (step == 2) {
                    TextButton(onClick = { startOver() }) {
                        Text("Start over", color = DarkAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PatternSuccess(onDone: () -> Unit) {
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
        Text(
            "Pattern lock applied",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "TalkWave will ask for your pattern when you open the app.",
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
