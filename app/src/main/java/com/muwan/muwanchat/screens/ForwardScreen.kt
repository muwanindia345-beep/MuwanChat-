package com.muwan.muwanchat.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.ConversationEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

private suspend fun sendViaSocketAwait(send: (onAck: (Boolean) -> Unit) -> Unit): Boolean {
    return withTimeoutOrNull(8000) {
        suspendCancellableCoroutine<Boolean> { cont ->
            send { success -> if (cont.isActive) cont.resume(success) }
        }
    } ?: false
}

private suspend fun forwardOneMessage(
    db: MuwanChatDb,
    myUid: String,
    myToken: String,
    target: ConversationEntity,
    msg: ChatMessage
) {
    val id = UUID.randomUUID().toString()
    val createdAt = nowIso()
    val content = if (msg.type == "text" || msg.type == "system") msg.text else (msg.mediaUrl ?: "")

    ChatRepository.recordMessage(
        db = db,
        id = id,
        roomId = target.roomId,
        senderUid = myUid,
        receiverUid = if (target.isGroup) target.roomId else target.uid,
        content = content,
        type = msg.type,
        createdAt = createdAt,
        myUid = myUid,
        otherUsername = target.username,
        status = "PENDING",
        fileName = msg.fileName,
        mimeType = msg.mimeType,
        isForwarded = true
    )

    if (target.isGroup) {
        val success = if (AppSocketManager.isConnected) {
            sendViaSocketAwait { cb ->
                AppSocketManager.sendGroupMessage(id, target.roomId, content, msg.type, msg.fileName, msg.mimeType, null, true, cb)
            }
        } else false
        db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
    } else {
        if (AppSocketManager.isConnected) {
            val success = sendViaSocketAwait { cb ->
                AppSocketManager.sendMessage(id, target.uid, content, msg.type, msg.fileName, msg.mimeType, null, true, cb)
            }
            db.messageDao().updateStatus(id, if (success) "SENT" else "FAILED")
        } else {
            try {
                val res = RetrofitClient.chatApi.sendMessage(
                    "Bearer $myToken",
                    SendMessageRequest(target.uid, content, msg.type, msg.fileName, msg.mimeType, null, true)
                )
                db.messageDao().updateStatus(id, if (res.isSuccessful) "SENT" else "FAILED")
            } catch (_: Exception) {
                db.messageDao().updateStatus(id, "FAILED")
            }
        }
    }
}

@Composable
fun ForwardScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myUid = remember { AuthDataStore.getUidBlocking(context) }
    val myToken = remember { AuthDataStore.getTokenBlocking(context) }
    val db = remember { MuwanChatDb.get(context, myUid) }

    val conversations by db.conversationDao().observeConversations().collectAsState(initial = emptyList())
    var selectedRoomIds by remember { mutableStateOf(setOf<String>()) }
    var sending by remember { mutableStateOf(false) }

    fun toggle(roomId: String) {
        selectedRoomIds = if (selectedRoomIds.contains(roomId)) selectedRoomIds - roomId else selectedRoomIds + roomId
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
            IconButton(onClick = {
                ForwardMessageSelection.clear()
                navController.popBackStack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Forward to", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        val activeConversations = conversations.filterNot { it.isRemoved }

        if (activeConversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Koi chat nahi mili", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(activeConversations, key = { it.roomId }) { conv ->
                    val isChecked = selectedRoomIds.contains(conv.roomId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(conv.roomId) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            avatarBase64 = conv.avatar,
                            fallbackText = conv.username,
                            size = 46.dp,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conv.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (conv.isGroup) {
                                Text("Group", color = Color(0xFF888888), fontSize = 12.sp)
                            }
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { toggle(conv.roomId) },
                            colors = CheckboxDefaults.colors(checkedColor = DarkAccent)
                        )
                    }
                    Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                }
            }
        }

        if (selectedRoomIds.isNotEmpty()) {
            Button(
                onClick = {
                    if (sending) return@Button
                    sending = true
                    val targets = activeConversations.filter { selectedRoomIds.contains(it.roomId) }
                    val toForward = ForwardMessageSelection.messages
                    scope.launch {
                        targets.forEach { target ->
                            toForward.forEach { msg ->
                                forwardOneMessage(db, myUid, myToken, target, msg)
                            }
                        }
                        ForwardMessageSelection.clear()
                        Toast.makeText(context, "Forwarded", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                enabled = !sending
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (sending) "Sending..." else "Send to ${selectedRoomIds.size}")
            }
        }
    }
}
