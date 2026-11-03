package com.muwan.muwanchat.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.navigation.Screen

// Registration ke turant baad dikhne wala mandatory screen -- koi back button
// nahi, dono checkbox tick karne ke baad hi "Confirm" enable hota hai.
// Header/background app ke baaki screens jaisa hi hai.
@Composable
fun TermsPolicyScreen(navController: NavController) {
    val context = LocalContext.current

    var acceptedTerms by remember { mutableStateOf(false) }
    var acceptedPrivacy by remember { mutableStateOf(false) }
    val canConfirm = acceptedTerms && acceptedPrivacy

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Terms & Policy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "Before you get started",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Please read and accept our Terms of Service and Privacy Policy to continue using TalkWave.",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))

            Text(
                "• You're responsible for the content you share.\n" +
                "• Nudity, harassment, spam, and malicious links are not allowed.\n" +
                "• Group admins can remove members who break the rules.\n" +
                "• Your data is transmitted securely, and encrypted at rest on our servers.",
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))

            Text(
                "Read Terms of Service",
                color = DarkAccent,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    openUrl("https://muwanindia345-beep.github.io/talkwave-site/terms.html")
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Read Privacy Policy",
                color = DarkAccent,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    openUrl("https://muwanindia345-beep.github.io/talkwave-site/privacy.html")
                }
            )

            Spacer(Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { acceptedTerms = !acceptedTerms }
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = DarkAccent, uncheckedColor = Color(0xFF888888))
                )
                Text("I accept the Terms of Service", color = Color.White, fontSize = 14.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { acceptedPrivacy = !acceptedPrivacy }
            ) {
                Checkbox(
                    checked = acceptedPrivacy,
                    onCheckedChange = { acceptedPrivacy = it },
                    colors = CheckboxDefaults.colors(checkedColor = DarkAccent, uncheckedColor = Color(0xFF888888))
                )
                Text("I accept the Privacy Policy", color = Color.White, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    navController.navigate(Screen.Profile.createRoute("onboarding")) {
                        popUpTo(Screen.TermsPolicy.route) { inclusive = true }
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkAccent,
                    disabledContainerColor = Color(0xFF444466)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
