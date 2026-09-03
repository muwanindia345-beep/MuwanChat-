package com.muwan.muwanchat.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader

@Composable
fun TermsPrivacyScreen(navController: NavController) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
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
            Text("Terms & Privacy Policy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "TalkWave takes your privacy and safety seriously. Below you'll find our Terms of Service " +
                "(rules for using the app) and our Privacy Policy (how we handle your data).",
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl("https://muwanindia345-beep.github.io/talkwave-site/terms.html") }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Terms of Service", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(18.dp))
            }
            Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl("https://muwanindia345-beep.github.io/talkwave-site/privacy.html") }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Privacy Policy", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(18.dp))
            }
            Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

            Spacer(Modifier.height(20.dp))

            Text(
                "Looking for community guidelines and prohibited content rules? Check Application Laws & Rules in Settings.",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        }
    }
}
