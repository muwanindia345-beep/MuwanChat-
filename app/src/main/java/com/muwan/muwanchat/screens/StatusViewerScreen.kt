package com.muwan.muwanchat.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.StatusItem
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.data.StatusTime
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendMessageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// STATUS_V3 -- full-screen viewer. uid == "me" -> apne status, warna friend ka uid.
private const val STATUS_DURATION_MS = 5000f

@Composable
fun StatusViewerScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val repo = remember { StatusRepository(context) }
    val isMine = uid == "me"

    var loaded by remember { mutableStateOf(false) }
    var statuses by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var idx by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf(if (isMine) "My status" else "") }
    var avatar by remember { mutableStateOf<String?>(null) }

    var progress by remember { mutableStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var replyFocused by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val marked = remember { mutableSetOf<String>() }

    val close: () -> Unit = { navController.popBackStack() }
    BackHandler { close() }

    // Cache se status load. Friend ke case mein pehle na-dekhe status se shuru.
    LaunchedEffect(uid) {
        val feed = repo.cachedFeed()
        if (isMine) {
            statuses = feed?.mine ?: emptyList()
            avatar = StatusMemory.myAvatar
            // alag coroutine: avatar ke network call se viewer start na ruke
            scope.launch {
                try {
                    loadMyAvatar(context) { avatar = it }
                } catch (_: Exception) {
                }
            }
        } else {
            val u = feed?.users?.firstOrNull { it.uid == uid }
            statuses = u?.items ?: emptyList()
            name = u?.username ?: ""
            avatar = u?.avatar
            val firstUnseen = statuses.indexOfFirst { !it.seen }
            idx = if (firstUnseen < 0) 0 else firstUnseen
        }
        loaded = true
        if (statuses.isEmpty()) close()
    }

    // 5 second ka timer. Pause: ungli dabi ho / reply box focus mein ho / dialog khula ho.
    LaunchedEffect(idx, statuses.size, loaded) {
        if (!loaded || statuses.isEmpty()) return@LaunchedEffect
        progress = 0f
        while (progress < 1f) {
            delay(50)
            if (!(paused || replyFocused || confirmDelete)) {
                progress += 50f / STATUS_DURATION_MS
            }
        }
        if (idx + 1 < statuses.size) { idx += 1 } else { close() }
    }

    // Dekha mark: turant local (ring grey), server ko repository baad mein bata deti hai
    LaunchedEffect(idx, statuses.size) {
        val cur = statuses.getOrNull(idx) ?: return@LaunchedEffect
        if (!isMine && cur.id !in marked) {
            marked.add(cur.id)
            if (!cur.seen) repo.markSeen(cur.id)
        }
    }

    val current: StatusItem? = statuses.getOrNull(idx)
    if (!loaded || current == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    } else {
        val sendReply: () -> Unit = {
            val text = replyText.trim()
            if (text.isEmpty()) {
                Toast.makeText(context, "Write a reply first", Toast.LENGTH_SHORT).show()
            } else {
                val preview = current.text.take(40) + if (current.text.length > 40) "..." else ""
                val content = if (preview.isNotBlank()) "Status: \"$preview\"\n$text" else text
                val msgId = java.util.UUID.randomUUID().toString()
                replyText = ""
                focusManager.clearFocus()
                val onResult: (Boolean) -> Unit = { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Reply sent" else "Couldn't send reply. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (AppSocketManager.isConnected) {
                    AppSocketManager.sendMessage(msgId, uid, content) { ok ->
                        scope.launch { onResult(ok) }
                    }
                } else {
                    scope.launch {
                        var ok = false
                        try {
                            val token = AuthDataStore.getToken(context).first()
                            if (token != null) {
                                val res = RetrofitClient.chatApi.sendMessage(
                                    "Bearer $token",
                                    SendMessageRequest(uid, content)
                                )
                                ok = res.isSuccessful
                            }
                        } catch (_: Exception) {
                        }
                        onResult(ok)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parseStatusColor(current.bgColor))
        ) {
            // Beech ka hissa: tap (left = pichla, right = agla), daba ke rakho = pause, neeche swipe = band
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(statuses.size) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width * 0.35f) {
                                    if (idx > 0) { idx -= 1 } else { progress = 0f }
                                } else {
                                    if (idx + 1 < statuses.size) { idx += 1 } else { close() }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 150f) close() },
                            onDragCancel = { total = 0f },
                            onVerticalDrag = { _, dy -> total += dy }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                StatusContent(current)
            }

            // Upar: progress bars + header
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    statuses.forEachIndexed { i, _ ->
                        val frac = when {
                            i < idx -> 1f
                            i == idx -> progress.coerceIn(0f, 1f)
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(frac)
                                    .background(Color.White)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(avatarBase64 = avatar, fallbackText = name, size = 38.dp, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            StatusTime.label(current.createdAtMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    if (isMine) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete status", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { close() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Neeche: friend ke status par reply bar, apne status par "Viewed by N"
            if (isMine) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Viewed by ${current.viewCount}", color = Color.White, fontSize = 14.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendReply() }),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { replyFocused = it.isFocused },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (replyText.isEmpty()) {
                                    Text(
                                        "Reply to status",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 15.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    IconButton(onClick = { sendReply() }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send reply", tint = Color.White)
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = DarkSheet,
                title = { Text("Delete status?", color = Color.White) },
                text = { Text("This status will be removed for everyone.", color = Color(0xFFAAAAAA)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        val target = current
                        scope.launch {
                            if (repo.delete(target.id)) {
                                statuses = statuses.filter { it.id != target.id }
                                if (idx >= statuses.size) idx = (statuses.size - 1).coerceAtLeast(0)
                                if (statuses.isEmpty()) close()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Couldn't delete status. Try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) { Text("Delete", color = Color(0xFFFF5252)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun StatusContent(s: StatusItem) {
    val body = when (s.type) {
        "link" -> listOf(s.text, s.linkUrl ?: "").filter { it.isNotBlank() }.joinToString("\n\n")
        else -> s.text
    }
    val fontSize = when {
        s.type != "text" -> 18.sp
        body.length > 220 -> 18.sp
        body.length > 100 -> 22.sp
        else -> 28.sp
    }
    Text(
        text = body.ifBlank { "This status type isn't supported in your app version yet." },
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}
