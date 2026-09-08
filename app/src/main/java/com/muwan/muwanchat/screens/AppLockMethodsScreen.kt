package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet

// Ek lock method ka static info — abhi sirf UI ke liye, koi actual
// setup logic nahi. Jab real screen banegi, yahi list us screen ke
// route se wire ho jayegi.
private data class LockMethodInfo(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val steps: String
)

private val lockMethods = listOf(
    LockMethodInfo(
        title = "Pattern",
        icon = Icons.Filled.Pattern,
        description = "Draw a custom pattern on a grid to unlock TalkWave.",
        steps = "Steps: Connect at least 4 dots to create your pattern, then confirm it once more to save."
    ),
    LockMethodInfo(
        title = "PIN",
        icon = Icons.Filled.Dialpad,
        description = "Use a numeric code (4–6 digits) to unlock the app.",
        steps = "Steps: Enter a PIN, then re-enter it to confirm before it's saved."
    ),
    LockMethodInfo(
        title = "Fingerprint",
        icon = Icons.Filled.Fingerprint,
        description = "Unlock instantly using your device's fingerprint sensor.",
        steps = "Steps: Uses your phone's existing fingerprint setup — no new fingerprint data is stored by TalkWave."
    ),
    LockMethodInfo(
        title = "Face Unlock",
        icon = Icons.Filled.Face,
        description = "Unlock instantly using your device's face recognition.",
        steps = "Steps: Uses your phone's existing face unlock setup — nothing is stored by TalkWave."
    ),
    LockMethodInfo(
        title = "Custom Password",
        icon = Icons.Filled.Password,
        description = "Set a custom word or password of your choice to unlock the app.",
        steps = "Steps: Type a password, then re-enter it to confirm before it's saved."
    ),
)

@Composable
fun AppLockMethodsScreen(navController: NavController) {
    var comingSoonFeature by remember { mutableStateOf<String?>(null) }

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
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Lock App", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Choose how you'd like to lock TalkWave",
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp
            )

            lockMethods.forEach { method ->
                LockMethodCard(
                    method = method,
                    onClick = { comingSoonFeature = method.title }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }
}

@Composable
private fun LockMethodCard(method: LockMethodInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSheet)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(method.icon, contentDescription = method.title, tint = DarkAccent)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                method.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            method.description,
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            method.steps,
            color = Color(0xFF888888),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
