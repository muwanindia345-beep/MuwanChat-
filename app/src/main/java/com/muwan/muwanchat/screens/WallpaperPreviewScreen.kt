package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkBubbleReceived
import com.muwan.muwanchat.DarkBubbleSent
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb

// ─── Dummy message model — sirf display ke liye, koi real data/network nahi ───
private data class DummyMessage(val text: String, val time: String, val sent: Boolean)

private val dummyMessages = listOf(
    DummyMessage("Hey! kaisa hai sab kuch?", "9:12 AM", sent = false),
    DummyMessage("Bas theek hu bhai, tu bata", "9:12 AM", sent = true),
    DummyMessage("Wallpaper kaisa laga ye?", "9:13 AM", sent = false),
    DummyMessage("Ekdum sahi lag raha hai 🔥", "9:13 AM", sent = true),
    DummyMessage("Set kar deta hu phir isse", "9:14 AM", sent = false),
    DummyMessage("Haan bindaas kar de", "9:14 AM", sent = true)
)

@Composable
fun WallpaperPreviewScreen(navController: NavController, roomId: String) {
    val context = LocalContext.current
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val current by db.chatWallpaperDao().observeByRoomId(roomId).collectAsState(initial = null)

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // ── Header, solid/opaque — ChatScreen jaisa, wallpaper ke peeche nahi ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Preview Wallpaper", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // ── Wallpaper + dummy chat bubbles, sirf isi box tak limited ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WallpaperPreviewBackground(current)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(dummyMessages) { msg ->
                    DummyBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun DummyBubble(msg: DummyMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.sent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (msg.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(msg.text, color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                msg.time,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
