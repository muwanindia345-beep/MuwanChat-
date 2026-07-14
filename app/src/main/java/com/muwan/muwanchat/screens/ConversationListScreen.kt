package com.muwan.muwanchat.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }

    // Room hi single source of truth — instant render, offline bhi
    val conversationEntities by db.conversationDao().observeConversations().collectAsState(initial = emptyList())

    // Global singleton state — screen dispose/recompose hone par reset nahi hota,
    // isliye ChatScreen se wapas aane par turant sahi online/typing status dikhta hai
    val onlineUids by AppSocketManager.onlineUids.collectAsState()
    val typingUsers by AppSocketManager.typingUsers.collectAsState()

    val conversations = remember(conversationEntities, onlineUids) {
        conversationEntities.map { e ->
            ConversationItem(
                room_id = e.roomId,
                uid = e.uid,
                username = e.username,
                avatar = e.avatar,
                lastMessage = e.lastMessage,
                lastTime = e.lastTime,
                isOnline = if (e.isGroup) e.onlineCount > 0 else onlineUids.contains(e.uid),
                unreadCount = e.unreadCount,
                lastSenderUid = e.lastSenderUid,
                isGroup = e.isGroup,
                memberCount = e.memberCount,
                onlineCount = e.onlineCount,
                isRemoved = e.isRemoved,
                removedByUsername = e.removedByUsername
            )
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var incomingCount by remember { mutableStateOf(0) }
    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }
    var showFabSheet by remember { mutableStateOf(false) }
    var comingSoonFeature by remember { mutableStateOf<String?>(null) }

    // ── Multi-select "delete chat" state ──────────────────────────────────
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedRoomIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedRoomIds = emptySet()
    }

    fun toggleSelection(roomId: String) {
        selectedRoomIds = if (selectedRoomIds.contains(roomId)) {
            selectedRoomIds - roomId
        } else {
            selectedRoomIds + roomId
        }
        if (selectedRoomIds.isEmpty()) isSelectionMode = false
    }

    suspend fun reloadConversations(token: String) {
        try {
            val res = RetrofitClient.chatApi.getConversations("Bearer $token")
            if (res.isSuccessful) {
                val serverItems = res.body()?.conversations ?: emptyList()
                ChatRepository.syncConversations(db, serverItems)

                // Offline hote waqt jo "removed" socket event miss ho gaya tha,
                // usko yahan REST se catch-up karte hain -- server response me
                // jo local group conversation missing hai (aur already
                // isRemoved mark nahi hai), uska removal-status check karo.
                val serverGroupIds = serverItems.filter { it.isGroup }.map { it.room_id }.toSet()
                val localGroups = db.conversationDao().getAll().filter { it.isGroup }
                for (local in localGroups) {
                    if (local.roomId in serverGroupIds || local.isRemoved) continue
                    try {
                        val statusRes = RetrofitClient.chatApi.getRemovalStatus("Bearer $token", local.roomId)
                        val status = statusRes.body()
                        if (statusRes.isSuccessful && status?.removed == true) {
                            db.conversationDao().markRemoved(local.roomId, status.removedByUsername ?: "Admin")
                        } else {
                            // Koi removal record nahi mila -- ya to khud-leave tha
                            // (jo already delete ho chuka hoga) ya group hi delete
                            // ho gaya, dono case me local stale entry hata do.
                            db.conversationDao().deleteByRoom(local.roomId)
                        }
                    } catch (_: Exception) {}
                }
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

        // Global socket — app-wide single connection, disconnect sirf logout par
        AppSocketManager.connect(token)

        // Teeno API calls parallel — pehle ye sequential the (me -> conversations -> incoming),
        // jisse fresh install/login par isLoading total teeno ke sum jitni der dikhta tha.
        // Ab total wait sirf sabse slow ek call jitna hoga.
        coroutineScope {
            val meJob = async {
                try {
                    val me = RetrofitClient.authApi.me("Bearer $token")
                    myUid = me.body()?.user?.uid ?: ""
                } catch (_: Exception) {}
            }
            val convJob = async { reloadConversations(token) }
            val reqJob = async {
                try {
                    val req = RetrofitClient.requestsApi.getIncoming("Bearer $token")
                    if (req.isSuccessful) incomingCount = req.body()?.requests?.size ?: 0
                } catch (_: Exception) {}
            }
            meJob.await()
            convJob.await()
            reqJob.await()
        }
        isLoading = false

        // Sabhi known contacts ka presence turant maango — list accurate dikhe
        // bina chat kholay, isi single socket se (koi alag connection nahi)
        db.conversationDao().getAll().forEach { conv ->
            AppSocketManager.checkPresence(conv.uid)
        }
    }

    // Global socket ke events sunte raho — jab tak screen composed hai
    // (online/typing ab AppSocketManager StateFlow se aata hai, yahan sirf
    // message/request events handle karne hain)
    LaunchedEffect(myUid) {
        AppSocketManager.events.collect { event ->
            when (event) {
                is SocketEvent.NewMessage -> {
                    val existing = db.conversationDao().getByRoomId(event.roomId)
                    if (existing == null) {
                        // Bilkul naya conversation — ya phir yeh room hidden tha (delete for me),
                        // dono case me poori list refresh karo (syncConversations khud unhide handle karega)
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
                is SocketEvent.NewRequest -> {
                    // Koi nayi request aayi — badge turant badhao, koi refetch nahi chahiye
                    incomingCount += 1
                }
                is SocketEvent.GroupRemoved -> {
                    // selfLeave case yahan kabhi nahi aata -- khud-leave turant
                    // deleteChatsLocally se list se hi hat jaata hai (GroupInfoScreen).
                    // Yahan sirf admin/owner-triggered removal handle hota hai.
                    if (!event.selfLeave) {
                        db.conversationDao().markRemoved(
                            event.roomId,
                            event.removedByUsername ?: "Admin"
                        )
                    }
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkHeader,
            title = { Text("Delete ${selectedRoomIds.size} chat${if (selectedRoomIds.size > 1) "s" else ""}?", color = Color.White) },
            text = {
                Text(
                    "Yeh sirf tumhare liye delete honge — doosre user ki chat waisi hi rahegi.",
                    color = Color(0xFFAAAAAA)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val idsToDelete = selectedRoomIds
                    scope.launch {
                        ChatRepository.deleteChatsLocally(db, idsToDelete)
                    }
                    showDeleteConfirm = false
                    exitSelectionMode()
                }) {
                    Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
    }

    if (showFabSheet) {
        ModalBottomSheet(onDismissRequest = { showFabSheet = false }, containerColor = DarkHeader) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                FabSheetOption(Icons.Filled.Search, "Search") {
                    showFabSheet = false
                    navController.navigate(Screen.UserSearch.route)
                }
                FabSheetOption(Icons.Filled.Add, "Create Group") {
                    showFabSheet = false
                    navController.navigate(Screen.CreateGroup.route)
                }
            }
        }
    }

    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showFabSheet = true },
                    containerColor = DarkAccent,
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "New Chat", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkHeader)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exitSelectionMode() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    Text(
                        "${selectedRoomIds.size} selected",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (selectedRoomIds.isNotEmpty()) showDeleteConfirm = true },
                        enabled = selectedRoomIds.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = if (selectedRoomIds.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF555577)
                        )
                    }
                }
            } else {
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
                        IconButton(onClick = {
                            navController.navigate(Screen.Profile.createRoute("edit"))
                        }) {
                            Icon(Icons.Filled.Person, contentDescription = "Profile", tint = DarkAccent)
                        }
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
                            navController.navigate(Screen.Settings.route)
                        }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings", tint = DarkAccent)
                        }
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
                        val isTyping = typingUsers[conv.uid] == conv.room_id
                        val isSelected = selectedRoomIds.contains(conv.room_id)
                        ConversationRow(
                            conv = conv,
                            isTyping = isTyping,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelection(conv.room_id)
                                } else {
                                    scope.launch { ChatRepository.clearUnread(db, conv.room_id) }
                                    if (conv.isGroup) {
                                        navController.navigate(
                                            Screen.GroupChat.createRoute(conv.room_id, conv.username)
                                        )
                                    } else {
                                        navController.navigate(
                                            Screen.Chat.createRoute(conv.uid, conv.username, conv.room_id)
                                        )
                                    }
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedRoomIds = setOf(conv.room_id)
                                }
                            }
                        )
                        HorizontalDivider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    conv: ConversationItem,
    isTyping: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val hasUnread = conv.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) DarkAccent.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AvatarView(
                avatarBase64 = conv.avatar,
                fallbackText = conv.username,
                size = 50.dp,
                fontSize = 20.sp
            )
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(if (isSelected) DarkAccent.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conv.username, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                when {
                    conv.isRemoved -> "You were removed from this group by @${conv.removedByUsername ?: "Admin"}"
                    isTyping -> "typing..."
                    else -> conv.lastMessage.ifBlank { "Say hi! 👋" }
                },
                color = when {
                    conv.isRemoved -> Color(0xFFFF6B6B)
                    isTyping -> DarkAccent
                    hasUnread -> Color.White
                    else -> Color(0xFF888888)
                },
                fontWeight = if (isTyping || hasUnread) FontWeight.Bold else FontWeight.Normal,
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
            } else if (conv.isGroup) {
                val othersCount = (conv.memberCount - 1).coerceAtLeast(0)
                val offlineCount = (othersCount - conv.onlineCount).coerceAtLeast(0)
                val statusText = if (conv.onlineCount > 0)
                    "Online (${if (conv.onlineCount > 9) "9+" else "${conv.onlineCount}"})"
                else
                    "Offline (${if (offlineCount > 9) "9+" else "${offlineCount}"})"
                Text(
                    statusText,
                    color = if (conv.onlineCount > 0) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
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

@Composable
private fun FabSheetOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = DarkAccent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}
