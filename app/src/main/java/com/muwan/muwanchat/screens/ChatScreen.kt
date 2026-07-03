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
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.SocketEvent
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var isReceiverTyping by remember { mutableStateOf(false) }
    var typingJob by remember { mutableStateOf<Job?>(null) }

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

        // Message bhejte hi typing signal band karo
        typingJob?.cancel()
        if (AppSocketManager.isConnected) {
            AppSocketManager.sendStopTyping(receiverUid)
        }

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

        if (AppSocketManager.isConnected) {
            AppSocketManager.sendMessage(id, receiverUid, text)
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

        // Same app-wide socket jo ConversationListScreen bhi use karti hai —
        // koi alag connection nahi banta, isliye online/unread dono jagah consistent
        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(roomId)
        AppSocketManager.checkPresence(receiverUid)

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

    // Global socket ke events sunte raho, sirf isi room/user se related events pakdo
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        AppSocketManager.events.collect { event ->
            when (event) {
                is SocketEvent.NewMessage -> {
                    if (event.roomId == roomId) {
                        ChatRepository.recordMessage(
                            db = db,
                            id = event.id,
                            roomId = roomId,
                            senderUid = event.senderUid,
                            receiverUid = if (event.senderUid == myUid) receiverUid else myUid,
                            content = event.content,
                            type = "text",
                            createdAt = event.createdAt.ifBlank { nowIso() },
                            myUid = myUid,
                            otherUsername = receiverUsername
                        )
                        if (event.senderUid != myUid) {
                            isReceiverTyping = false
                            try {
                                RetrofitClient.chatApi.markSeen("Bearer $myToken", roomId)
                            } catch (_: Exception) {}
                            ChatRepository.clearUnread(db, roomId)
                        }
                    }
                }
                is SocketEvent.UserOnline -> {
                    if (event.uid == receiverUid) isReceiverOnline = true
                }
                is SocketEvent.UserOffline -> {
                    if (event.uid == receiverUid) {
                        isReceiverOnline = false
                        isReceiverTyping = false
                    }
                }
                is SocketEvent.PresenceStatus -> {
                    if (event.uid == receiverUid) isReceiverOnline = event.online
                }
                is SocketEvent.Typing -> {
                    if (event.uid == receiverUid && event.roomId == roomId) isReceiverTyping = true
                }
                is SocketEvent.StopTyping -> {
                    if (event.uid == receiverUid) isReceiverTyping = false
                }
                else -> {}
            }
        }
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
            isTyping = isReceiverTyping,
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

                if (AppSocketManager.isConnected) {
                    if (it.isNotBlank()) {
                        AppSocketManager.sendTyping(roomId, receiverUid)
                        typingJob?.cancel()
                        typingJob = scope.launch {
                            delay(2500)
                            AppSocketManager.sendStopTyping(receiverUid)
                        }
                    } else {
                        typingJob?.cancel()
                        AppSocketManager.sendStopTyping(receiverUid)
                    }
                }
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
