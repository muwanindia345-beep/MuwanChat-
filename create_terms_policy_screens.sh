#!/data/data/com.termux/files/usr/bin/bash
set -e

DIR="app/src/main/java/com/muwan/muwanchat/screens"
if [ ! -d "$DIR" ]; then
  echo "ERROR: run this from project root (where app/ folder is)"
  exit 1
fi

echo "Creating TermsPolicyScreen.kt..."
cat > "$DIR/TermsPolicyScreen.kt" << 'F1_EOF'
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
F1_EOF

echo "Creating TermsPrivacyScreen.kt..."
cat > "$DIR/TermsPrivacyScreen.kt" << 'F2_EOF'
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
F2_EOF

echo "Creating ApplicationRulesScreen.kt..."
cat > "$DIR/ApplicationRulesScreen.kt" << 'F3_EOF'
package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader

@Composable
fun ApplicationRulesScreen(navController: NavController) {

    @Composable
    fun SectionTitle(text: String) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
    }

    @Composable
    fun SectionBody(text: String) {
        Text(text, color = Color(0xFFCCCCCC), fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(20.dp))
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
            Text("Application Laws & Rules", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "TalkWave — Application Rules",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(16.dp))

            SectionBody(
                "These rules exist to keep TalkWave a safe and respectful place for everyone. " +
                "By using the app, you agree to follow them."
            )

            SectionTitle("1. Respect Other Users")
            SectionBody(
                "Treat everyone you interact with on TalkWave with respect. Harassment, threats, bullying, " +
                "hate speech, or discrimination based on race, religion, gender, sexuality, or any other " +
                "characteristic is strictly against these rules."
            )

            SectionTitle("2. No Explicit or Harmful Content")
            SectionBody(
                "Do not send, share, or request:\n" +
                "• Nudity or sexually explicit material\n" +
                "• Content involving minors in any inappropriate context\n" +
                "• Graphic violence intended to shock or disturb\n" +
                "• Content promoting self-harm"
            )

            SectionTitle("3. No Spam or Malicious Links")
            SectionBody(
                "Do not send unsolicited promotional messages, chain messages, or links to phishing, scam, " +
                "or malware websites. Suspicious links may be flagged automatically; sharing known malicious " +
                "domains may result in removal from groups."
            )

            SectionTitle("4. No Impersonation or Fake Accounts")
            SectionBody(
                "Do not create an account pretending to be someone else, or misrepresent your identity to " +
                "deceive other users."
            )

            SectionTitle("5. Respect Group Spaces")
            SectionBody(
                "Group admins set the tone and purpose of their groups. Follow group-specific rules set by " +
                "admins. Repeated disruptive behavior in a group may lead to removal by the group admin."
            )

            SectionTitle("6. Encrypted & Private by Design")
            SectionBody(
                "TalkWave transmits your messages and calls securely and does not proactively read or " +
                "monitor your private conversations. This means we rely on the community — if you experience " +
                "or witness a violation of these rules, please use the report option so appropriate action " +
                "can be taken."
            )

            SectionTitle("7. What Happens If You Break These Rules")
            SectionBody(
                "• A group admin can remove you from a group at any time if your behavior violates these " +
                "rules or the group's own guidelines.\n" +
                "• If removed, you may send a new request to rejoin — approval is at the admin's discretion.\n" +
                "• Repeated or severe violations reported by multiple users may result in further " +
                "account-level action."
            )

            SectionTitle("8. Reporting a Problem")
            SectionBody(
                "If you see something that violates these rules, use the in-app report option, or contact " +
                "us directly at binarycota890@gmail.com with relevant details (screenshots help)."
            )

            SectionTitle("9. Changes to These Rules")
            SectionBody(
                "These rules may be updated as TalkWave grows. Continued use of the app after an update " +
                "means you accept the current version of these rules."
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Last updated: September 2026",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
F3_EOF

echo "Done: 3 new screen files created."
