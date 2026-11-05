package com.muwan.muwanchat.screens

// GUEST_P2B2
// 1) GuestInviteHost: Chats home par invite ka confirm popup (Add / Cancel) + natija.
// 2) GuestJoinRoute: muwan://guest?p=... deep link ka darwaza -- invite inbox mein daal ke home le jata hai.
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.GuestInviteInbox
import com.muwan.muwanchat.data.GuestStore
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun GuestInviteHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val raw by GuestInviteInbox.pending.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var resolved by remember(raw) { mutableStateOf<GuestInviteInbox.Resolved?>(null) }  // GUEST_P2D
    LaunchedEffect(raw) {
        val r = raw
        resolved = if (r == null) null else GuestInviteInbox.resolve(r)
    }
    val invite = resolved?.invite

    val current = raw
    if (current == null) return

    fun close() {
        outcome = null
        busy = false
        GuestInviteInbox.clear()
    }

    val result = outcome
    if (result != null) {
        AlertDialog(
            onDismissRequest = { close() },
            containerColor = DarkSheet,
            title = {
                Text(
                    if (result.first) "Done" else "Couldn't add contact",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(result.second, color = Color(0xFFCCCCCC), fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { close() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("OK", color = Color.White) }
            }
        )
    } else if (resolved == null) {  // GUEST_P2D: invite server se check ho raha hai
        AlertDialog(
            onDismissRequest = { close() },
            containerColor = DarkSheet,
            title = { Text("Checking invite…", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Please wait a moment.", color = Color(0xFFCCCCCC), fontSize = 14.sp) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { close() }) { Text("Cancel", color = Color(0xFF888888)) } }
        )
    } else if (invite == null) {
        AlertDialog(
            onDismissRequest = { close() },
            containerColor = DarkSheet,
            title = { Text("Invalid invite", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    resolved?.error ?: "This QR code or link is not a valid TalkWave guest invite.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { close() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("OK", color = Color.White) }
            }
        )
    } else {
        val shown = invite.name.ifBlank { "Guest " + invite.guestId.takeLast(4) }
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            containerColor = DarkSheet,
            title = { Text("Add contact?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(shown, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (invite.country.isNotBlank()) {
                        Text(invite.country, color = Color(0xFF888888), fontSize = 13.sp)
                    }
                    Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    Text(
                        "Add this person to your chats? They will be able to message you.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                    Spacer(androidx.compose.ui.Modifier.height(8.dp))
                    Text(invite.guestId.take(10) + "…" + invite.guestId.takeLast(6), color = Color(0xFF666688), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            outcome = GuestInviteInbox.accept(context, invite)
                            busy = false
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (busy) "Adding…" else "Add", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { close() }, enabled = !busy) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
    }
}

@Composable
fun GuestJoinRoute(navController: NavController, payload: String?) {
    val context = LocalContext.current
    LaunchedEffect(payload) {
        val isAuth = AuthDataStore.getTokenBlocking(context).isNotEmpty()
        val isGuest = GuestStore.isGuestActive(context)
        val target: String
        if (isAuth) {
            Toast.makeText(context, "Invite links work in guest mode only.", Toast.LENGTH_LONG).show()
            target = Screen.ConversationList.route
        } else if (isGuest) {
            if (payload != null) GuestInviteInbox.offer(payload)
            target = Screen.ConversationList.route
        } else {
            // Guest profile abhi bana nahi: invite yaad rakho, profile ke baad home par popup aayega
            if (payload != null) GuestInviteInbox.offer(payload)
            Toast.makeText(context, "Create your guest profile to accept this invite.", Toast.LENGTH_LONG).show()
            target = Screen.Login.route
        }
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}
