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
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val db = remember { MuwanChatDb.get(context) }

    // Room hi single source of truth — instant render, offline bhi
    val conversationEntities by db.conversationDao().observeConversations().collectAsState(initial = emptyList())
    var onlineStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val conversations = remember(conversationEntities, onlineStatus) {
        conversationEntities.map { e ->
            ConversationItem(
                room_id = e.roomId,
                uid = e.uid,
                username = e.username,
                avatar = e.avatar,
                lastMessage = e.lastMessage,
                lastTime = e.lastTime,
                isOnline = onlineStatus[e.uid] ?: false,
                unreadCount = e.unreadCount,
                lastSenderUid = e.lastSenderUid
            )
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var incomingCount by remember { mutableStateOf(0) }
    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }

    suspend fun reloadConversations(token: String) {
        try {
            val res = RetrofitClient.chatApi.getConversations("Bearer $token")
            if (res.isSuccessful) {
                ChatRepository.syncConversations(db, res.body()?.conversations ?: emptyList())
            }
        } catch (_: Exception) {}
    }

    // Room mein data aate hi loading hata do — spinner sirf tab jab cache bhi khaali ho
    LaunchedEffect(conversationEntities) {
        if (conversationEntities.isNotEmpty()) isLoading = false
    }

    // Ek baar setup: token/uid nikaalo, global socket connect karo, background sync karo
    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token

        try {
            val me = RetrofitClient.authApi.me("Bearer $token")
            myUid = me.body()?.user?.uid ?: ""
        } catch (_: Exception) {}

        // Global socket — app-wide single connection, disconnect sirf logout par
        AppSocketManager.connect(token)

        // Chup-chaap background sync — Room already list dikha chuka hai isse pehle
        reloadConversations(token)
        try {
            val req = RetrofitClient.requestsApi.getIncoming("Bearer $token")
            if (req.isSuccessful) incomingCount = req.body()?.requests?.size ?: 0
        } catch (_: Exception) {}
        isLoading = false

        // Sabhi known contacts ka presence turant maango — list accurate dikhe
        // bina chat kholay, isi single socket se (koi alag connection nahi)
        db.conversationDao().getAll().forEach { conv ->
            AppSocketManager.checkPresence(conv.uid)
        }
    }

    // Global socket ke events sunte raho — jab tak screen composed hai
    LaunchedEffect(myUid) {
        AppSocketManager.events.collect { event ->
            when (event) {
                is SocketEvent.NewMessage -> {
                    val existing = db.conversationDao().getByRoomId(event.roomId)
                    if (existing == null) {
                        // Bilkul naya conversation — poori list refresh karo taaki username mil jaye
                        if (myToken.isNotBlank()) reloadConversations(myToken)
                    } else {
                        ChatRepository.recordMessage(
                            db = db,
                            id = event.id,
                            roomId = event.roomId,
                            senderUid = event.senderUid,
                            receiverUid = myUid,
                            content = event.content,
                            type = "text",
                            createdAt = event.createdAt.ifBlank { nowIso() },
                            myUid = myUid
                        )
                    }
                }
                is SocketEvent.UserOnline -> {
                    onlineStatus = onlineStatus + (event.uid to true)
                }
                is SocketEvent.UserOffline -> {
                    onlineStatus = onlineStatus + (event.uid to false)
                }
                is SocketEvent.PresenceStatus -> {
                    onlineStatus = onlineStatus + (event.uid to event.online)
                }
                is SocketEvent.NewRequest -> {
                    // Koi nayi request aayi — badge turant badhao, koi refetch nahi chahiye
                    incomingCount += 1
                }
                is SocketEvent.RequestAccepted -> {
                    // Chahe humne accept kiya ho ya doosre ne — dono taraf superfast
                    // naya conversation list mein jud jaata hai, bina full refetch ke
                    ChatRepository.addConversationPlaceholder(
                        db = db,
                        roomId = event.roomId,
                        uid = event.uid,
                        username = event.username,
                        avatar = event.avatar
                    )
                }
                else -> {}
            }
        }
    }

    // Screen par wapas aane par bhi ek halka background refresh — safety net
    LaunchedEffect(navController.currentBackStackEntry) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        reloadConversations(token)
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
                        IconButton(onClick = {
                            incomingCount = 0
                            navController.navigate(Screen.Requests.route)
                        }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Requests", tint = DarkAccent)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            AppSocketManager.disconnect()
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
                    items(filtered, key = { it.room_id }) { conv ->
                        ConversationRow(conv = conv, onClick = {
                            scope.launch { ChatRepository.clearUnread(db, conv.room_id) }
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
    val hasUnread = conv.unreadCount > 0

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
                color = if (hasUnread) Color.White else Color(0xFF888888),
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatConvTime(conv.lastTime), color = Color(0xFF666688), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkAccent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (conv.unreadCount > 9) "9+" else "${conv.unreadCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    if (conv.isOnline) "Online" else "Offline",
                    color = if (conv.isOnline) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
