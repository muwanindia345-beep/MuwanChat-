package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.RetrofitClient
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private fun formatConvTime(raw: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdf.parse(raw.take(16)) ?: return raw.take(10)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val msgDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        if (msgDay == today)
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
    } catch (_: Exception) { raw.take(10) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var conversations by remember { mutableStateOf<List<ConversationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var incomingCount by remember { mutableStateOf(0) }
    var socket by remember { mutableStateOf<Socket?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        try {
            val res = RetrofitClient.chatApi.getConversations("Bearer $token")
            if (res.isSuccessful) conversations = res.body()?.conversations ?: emptyList()
            val req = RetrofitClient.requestsApi.getIncoming("Bearer $token")
            if (req.isSuccessful) incomingCount = req.body()?.requests?.size ?: 0
        } catch (_: Exception) {}
        isLoading = false

        // Socket — new message pe conversation preview update karo
        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to token)
                transports = arrayOf("websocket")
            }
            val s = IO.socket(BACKEND_URL, opts)
            s.on("new_message") { args ->
                val json = args[0] as? JSONObject ?: return@on
                val senderUid = json.optString("sender_uid")
                val content = json.optString("content")
                val roomId = json.optString("room_id")
                scope.launch {
                    conversations = conversations.map { conv ->
                        if (conv.room_id == roomId)
                            conv.copy(lastMessage = content, lastTime = "just now")
                        else conv
                    }
                }
            }
            s.connect()
            socket = s
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose { socket?.disconnect() }
    }

    val filtered = if (searchQuery.isBlank()) conversations
    else conversations.filter { it.username.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.UserSearch.route) },
                containerColor = DarkAccent,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "New Chat", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MuwanChat", color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Row {
                    BadgedBox(badge = {
                        if (incomingCount > 0) Badge { Text("$incomingCount") }
                    }) {
                        IconButton(onClick = { navController.navigate(Screen.Requests.route) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Requests", tint = DarkAccent)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            AuthDataStore.clearAuth(context)
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.ConversationList.route) { inclusive = true }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout", tint = Color(0xFF888888))
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search chats...", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF888888)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF333355),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkAccent)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null,
                            tint = Color(0xFF444466), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Koi chat nahi abhi", color = Color(0xFF666688), fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Neeche + se naya chat shuru karo", color = Color(0xFF444466), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn {
                    items(filtered) { conv ->
                        ConversationRow(conv = conv, onClick = {
                            navController.navigate(
                                Screen.Chat.createRoute(conv.uid, conv.username, conv.room_id)
                            )
                        })
                        HorizontalDivider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationRow(conv: ConversationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(DarkAccent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                conv.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conv.username, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                conv.lastMessage.ifBlank { "Say hi! 👋" },
                color = Color(0xFF888888), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(formatConvTime(conv.lastTime), color = Color(0xFF666688), fontSize = 11.sp)
    }
}
