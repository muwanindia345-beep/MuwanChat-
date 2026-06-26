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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var conversations by remember { mutableStateOf<List<ConversationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var myUid by remember { mutableStateOf("") }
    var incomingCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myUid = AuthDataStore.getUsername(context).first() ?: ""
        try {
            val res = RetrofitClient.chatApi.getConversations("Bearer $token")
            if (res.isSuccessful) conversations = res.body()?.conversations ?: emptyList()
            val req = RetrofitClient.requestsApi.getIncoming("Bearer $token")
            if (req.isSuccessful) incomingCount = req.body()?.requests?.size ?: 0
        } catch (_: Exception) {}
        isLoading = false
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "MuwanChat",
                    color = DarkAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Row {
                    // Requests bell with badge
                    BadgedBox(
                        badge = {
                            if (incomingCount > 0) Badge { Text("$incomingCount") }
                        }
                    ) {
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

            // Search bar
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
                        val otherUid = if (conv.sender_uid == myUid) conv.receiver_uid else conv.sender_uid
                        ConversationRow(
                            conv = conv,
                            myUid = myUid,
                            onClick = {
                                navController.navigate(Screen.Chat.createRoute(otherUid, conv.username, conv.room_id))
                            }
                        )
                        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationRow(conv: ConversationItem, myUid: String, onClick: () -> Unit) {
    val isUnread = conv.seen == 0 && conv.receiver_uid == myUid

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
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                conv.username,
                color = Color.White,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                conv.content.ifBlank { "📷 Image" },
                color = if (isUnread) Color.White else Color(0xFF888888),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                conv.created_at.take(10),
                color = if (isUnread) DarkAccent else Color(0xFF666688),
                fontSize = 11.sp
            )
            if (isUnread) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DarkAccent)
                )
            }
        }
    }
}
