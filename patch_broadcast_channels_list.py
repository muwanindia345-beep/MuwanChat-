# -*- coding: utf-8 -*-
# BroadcastChannelsScreen ab asli channel list dikhata hai (jaise
# ConversationListScreen -- same ConversationRow composable reuse,
# showOnlineStatus=false taaki online/offline na dikhe, sirf timestamp).
# "Coming Soon" empty-state hata ke "No broadcast channel yet" laga diya.
# Row tap karne se seedha GroupChatScreen khulti hai (channel bhi ek group
# hi hai backend me).

path = "app/src/main/java/com/muwan/muwanchat/screens/BroadcastChannelsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet'''

new_imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first'''

n = src.count(old_imports)
if n != 1:
    raise SystemExit(f"[FAIL] imports block: found {n} matches (expected 1)")
src = src.replace(old_imports, new_imports, 1)

old_state = '''fun BroadcastChannelsScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(containerColor = DarkBg) { padding ->'''

new_state = '''fun BroadcastChannelsScreen(navController: NavController) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<ConversationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val res = RetrofitClient.chatApi.getChannels("Bearer $token")
                if (res.isSuccessful) {
                    channels = res.body()?.conversations ?: emptyList()
                }
            }
        } catch (_: Exception) {
            // List khaali reh jaayegi, empty-state dikh jaayega -- pull-to-
            // refresh jaisi cheez abhi is screen pe nahi hai.
        }
        isLoading = false
    }

    Scaffold(containerColor = DarkBg) { padding ->'''

n2 = src.count(old_state)
if n2 != 1:
    raise SystemExit(f"[FAIL] fun signature anchor: found {n2} matches (expected 1)")
src = src.replace(old_state, new_state, 1)

old_body = '''            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = Color(0xFF444466),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Coming Soon",
                        color = DarkAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This feature is currently in development.\\n🤝 Best of luck!",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }'''

new_body = '''            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkAccent)
                }
            } else if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = Color(0xFF444466),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No broadcast channel yet",
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(channels, key = { it.room_id }) { channel ->
                        ConversationRow(
                            conv = channel,
                            showOnlineStatus = false,
                            onClick = {
                                navController.navigate(
                                    Screen.GroupChat.createRoute(channel.room_id, channel.username)
                                )
                            }
                        )
                    }
                }
            }'''

n3 = src.count(old_body)
if n3 != 1:
    raise SystemExit(f"[FAIL] empty-state body block: found {n3} matches (expected 1)")
src = src.replace(old_body, new_body, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Broadcast Channels list wired: fetch, render rows (no online/offline), 'No broadcast channel yet' empty state")
