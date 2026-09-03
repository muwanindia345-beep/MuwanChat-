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
