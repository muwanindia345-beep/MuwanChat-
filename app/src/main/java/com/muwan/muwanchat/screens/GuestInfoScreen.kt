package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.navigation.Screen

// GUEST_UI -- Guest mode ke bare mein info screen: data kaha save hota hai, risk aur warning.
// Sirf English. Confirm tick ke baad hi Continue enable hota hai.
@Composable
fun GuestInfoScreen(navController: NavController) {
    var understood by remember { mutableStateOf(false) }

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
            Text("About Guest Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                "Please read before you continue",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            GuestInfoSection(
                title = "Where is my data stored?",
                body = "Everything lives only on this device: your name, photo, contacts, messages, media and " +
                    "encryption keys. Nothing is linked to an email, phone number or username, and there is " +
                    "no account on our servers."
            )
            GuestInfoSection(
                title = "What do our servers do?",
                body = "The server only relays end-to-end encrypted messages and call signals. It cannot read " +
                    "them. If the person you message is offline, the encrypted message is held temporarily " +
                    "(for a few days, up to 7) and deleted as soon as it is delivered or expires. " +
                    "No chat history is kept on the server."
            )
            GuestInfoSection(
                title = "How do I connect with people?",
                body = "Guests are not listed in search. You connect only by sharing your QR code or an " +
                    "invite link, or by scanning or opening someone else's. Anyone who has your QR code or " +
                    "link can send you a connection request, so share them carefully."
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip4()
                    .background(Color(0x1AFF5555))
                    .border(1.dp, Color(0xFFFF5555), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("Risks and warnings", color = Color(0xFFFF5555), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "• If you clear app data or uninstall the app, your guest ID and all chats are deleted " +
                        "permanently. This cannot be undone.\n" +
                        "• There is no backup, restore or password reset for guests. Nobody, including us, " +
                        "can recover a lost guest ID.\n" +
                        "• If you lose or change your phone, you lose everything.\n" +
                        "• Messages that stay undelivered for more than 7 days expire and are lost.\n" +
                        "• The other person may keep copies of what you send, such as screenshots.\n" +
                        "• Need a backup or a database of your chats? Use a registered account instead.",
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { understood = !understood }
            ) {
                Checkbox(
                    checked = understood,
                    onCheckedChange = { understood = it },
                    colors = CheckboxDefaults.colors(checkedColor = DarkAccent, uncheckedColor = Color(0xFF888888))
                )
                Text(
                    "I understand that my guest data exists only on this device and can be lost permanently.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { navController.navigate(Screen.Profile.createRoute("guest")) },
                enabled = understood,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkAccent,
                    disabledContainerColor = Color(0xFF444466)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GuestInfoSection(title: String, body: String) {
    Text(title, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    Text(body, color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(18.dp))
}

private fun Modifier.clip4(): Modifier =
    this.then(Modifier.clip(RoundedCornerShape(12.dp)))
