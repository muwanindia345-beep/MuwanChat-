package com.muwan.muwanchat.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    receiverUid: String,
    receiverUsername: String,
    roomId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val db = remember { MuwanChatDb.get(context) }

    // Room hi single source of truth — yahi se list render hoti hai, offline bhi
    val messageEntities by db.messageDao().observeMessages(roomId).collectAsState(initial = emptyList())

    var myUid by remember { mutableStateOf("") }
    var myToken by remember { mutableStateOf("") }
    var isReceiverOnline by remember { mutableStateOf(false) }

    // Sirf image placeholder jaisi cheezein jo abhi Room mein persist nahi hoti (upload flow baad mein aayega)
    val localOnlyMessages = remember { mutableStateListOf<ChatMessage>() }

    val messages = remember(messageEntities, localOnlyMessages.size, myUid) {
        messageEntities.map { it.toChatMessage(myUid) } + localOnlyMessages
    }

    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    var comingSoonFeature by remember { mutableStateOf<String?>(null) }
    var fullscreenImage by remember { mutableStateOf<Uri?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    var socketManager by remember { mutableStateOf<ChatSocketManager?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            localOnlyMessages.add(ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "",
                sent = true,
                time = nowTime(),
                imageUri = it
            ))
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank()) return
        val id = UUID.randomUUID().toString()
        val createdAt = nowIso()
        input = ""
        replyTo = null

        // Room mein turant insert — offline pe bhi apna hi bheja message dikhega, aur
        // conversation list ka lastMessage bhi isi se sync ho jaata hai
        scope.launch {
            ChatRepository.recordMessage(
                db = db,
                id = id,
                roomId = roomId,
                senderUid = myUid,
                receiverUid = receiverUid,
                content = text,
                type = "text",
                createdAt = createdAt,
                myUid = myUid,
                otherUsername = receiverUsername
            )
        }

        val mgr = socketManager
        if (mgr?.socket != null) {
            mgr.sendMessage(id, text)
        } else {
            scope.launch {
                try {
                    RetrofitClient.chatApi.sendMessage(
                        "Bearer $myToken",
                        SendMessageRequest(receiverUid, text)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    // Naya message (Room se ho ya socket se) aate hi list badhegi, tab scroll karo
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token

        try {
            val me = RetrofitClient.authApi.me("Bearer $token")
            myUid = me.body()?.user?.uid ?: ""
        } catch (_: Exception) {}

        val mgr = ChatSocketManager(
            token = token,
            roomId = roomId,
            receiverUid = receiverUid,
            onNewMessage = { id, senderUid, content, createdAt ->
                scope.launch {
                    ChatRepository.recordMessage(
                        db = db,
                        id = id,
                        roomId = roomId,
                        senderUid = senderUid,
                        receiverUid = if (senderUid == myUid) receiverUid else myUid,
                        content = content,
                        type = "text",
                        createdAt = createdAt.ifBlank { nowIso() },
                        myUid = myUid,
                        otherUsername = receiverUsername
                    )
                    if (senderUid != myUid) {
                        try {
                            RetrofitClient.chatApi.markSeen("Bearer $myToken", roomId)
                        } catch (_: Exception) {}
                        ChatRepository.clearUnread(db, roomId)
                    }
                }
            },
            onPresenceChange = { _, online ->
                isReceiverOnline = online
            }
        )
        mgr.connect()
        socketManager = mgr

        // Chup-chaap background sync — Room already screen dikha chuka hai isse pehle
        try {
            val res = RetrofitClient.chatApi.getMessages("Bearer $token", roomId)
            if (res.isSuccessful) {
                ChatRepository.syncMessages(db, res.body()?.messages ?: emptyList())
            }
        } catch (_: Exception) {}

        try {
            RetrofitClient.chatApi.markSeen("Bearer $token", roomId)
            ChatRepository.clearUnread(db, roomId)
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose { socketManager?.disconnect() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {
        ChatHeader(
            receiverUsername = receiverUsername,
            isOnline = isReceiverOnline,
            onBack = { navController.popBackStack() },
            onVideoCall = { comingSoonFeature = "📹 Video Call" },
            onVoiceCall = { comingSoonFeature = "📞 Voice Call" }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                ) {
                    MessageBubble(
                        message = msg,
                        onSwipeReply = { replyTo = it },
                        onImageTap = { uri -> fullscreenImage = uri }
                    )
                }
            }
        }

        AnimatedVisibility(visible = replyTo != null) {
            replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkInputBg)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("↩ Reply", color = com.muwan.muwanchat.DarkAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(reply.text.take(50), color = Color(0xFF888888),
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { replyTo = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close",
                            tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = showEmojiPicker) {
            EmojiPickerRow { emoji -> input += emoji }
        }

        ChatInputBar(
            input = input,
            onInputChange = {
                input = it
                if (showEmojiPicker) showEmojiPicker = false
            },
            showEmojiPicker = showEmojiPicker,
            onToggleEmojiPicker = {
                showEmojiPicker = !showEmojiPicker
                if (showEmojiPicker) keyboardController?.hide() else keyboardController?.show()
            },
            onPickImage = { photoPicker.launch("image/*") },
            onSend = { sendMessage() },
            onVoiceMessage = { comingSoonFeature = "🎤 Voice Message" }
        )
    }

    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    fullscreenImage?.let { uri ->
        FullscreenImageViewer(uri = uri, onDismiss = { fullscreenImage = null })
    }
}
