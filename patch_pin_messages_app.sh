#!/data/data/com.termux/files/usr/bin/bash
# patch_pin_messages_app.sh
# Wires up the "Pin Message" feature on the Android side:
#  - New SocketEvent.MessagePinned/MessageUnpinned (live sync)
#  - New Retrofit endpoints: pinMessage, unpinMessage, getPinnedMessages
#  - Selection toolbar: "React" icon replaced with "Pin" (multi-select pin)
#  - New pinned-message banner below the header in both 1-on-1 and group
#    chat, with tap-to-jump (single pinned) or dropdown (multiple pinned)
# Requires: patch_backend_pin_messages.sh must already be deployed on
# the backend, otherwise these calls will just fail silently.
# Run from project root (MuwanChat--main folder):
#   bash patch_pin_messages_app.sh

set -e

SOCKET_FILE="app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
API_FILE="app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
CHAT_FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
GROUP_FILE="app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

for f in "$SOCKET_FILE" "$API_FILE" "$CHAT_FILE" "$GROUP_FILE"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

python3 - << 'PYEOF'
def patch(path, replacements, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for old, new in replacements:
        if new in content:
            print(f"SKIP ({label}): already patched")
            continue
        if old in content:
            content = content.replace(old, new, 1)
            changed = True
        else:
            print(f"WARN ({label}): anchor not found — already patched or changed manually?")
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")

# ───────────────────────── AppSocketManager.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt",
    [
        (
            '''    // Edit Message ka result — sender ke alawa dusre device/user ki screen bhi isi se update hoti hai
    data class MessageEdited(val id: String, val roomId: String, val content: String) : SocketEvent()''',
            '''    // Edit Message ka result — sender ke alawa dusre device/user ki screen bhi isi se update hoti hai
    data class MessageEdited(val id: String, val roomId: String, val content: String) : SocketEvent()

    // Pin/Unpin shared hote hai — dono/sabhi participants ki screen isi se live update hoti hai
    data class MessagePinned(val id: String, val roomId: String, val pinnedAt: String) : SocketEvent()
    data class MessageUnpinned(val id: String, val roomId: String) : SocketEvent()'''
        ),
        (
            '''            s.on("message_deleted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageDeleted(json.optString("id"), json.optString("room_id"))
                )
            }''',
            '''            s.on("message_deleted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageDeleted(json.optString("id"), json.optString("room_id"))
                )
            }

            s.on("message_pinned") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessagePinned(
                        json.optString("id"),
                        json.optString("room_id"),
                        json.optString("pinned_at")
                    )
                )
            }

            s.on("message_unpinned") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessageUnpinned(json.optString("id"), json.optString("room_id"))
                )
            }'''
        ),
    ],
    "AppSocketManager.kt"
)

# ───────────────────────── ChatApi.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt",
    [
        (
            '''    // "Delete for me" (single message) ko server pe bhi persist karta hai,
    // taaki app reinstall / local DB reset ke baad bhi message wapas na aaye.
    // Dusre user ko is se koi farak nahi padta, sirf khud ke liye hide hota hai.
    @POST("chat/message/{roomId}/{id}/hide")
    suspend fun hideMessageForMe(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>''',
            '''    // "Delete for me" (single message) ko server pe bhi persist karta hai,
    // taaki app reinstall / local DB reset ke baad bhi message wapas na aaye.
    // Dusre user ko is se koi farak nahi padta, sirf khud ke liye hide hota hai.
    @POST("chat/message/{roomId}/{id}/hide")
    suspend fun hideMessageForMe(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    // Pinned messages — SHARED, dono/sabhi participants ko dikhta hai
    @POST("chat/message/{roomId}/{id}/pin")
    suspend fun pinMessage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    @POST("chat/message/{roomId}/{id}/unpin")
    suspend fun unpinMessage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Path("id") id: String
    ): Response<Map<String, Boolean>>

    @GET("chat/pinned/{roomId}")
    suspend fun getPinnedMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<PinnedMessagesResponse>'''
        ),
        (
            '''data class DeletedMessagesResponse(
    val ids: List<String>
)''',
            '''data class DeletedMessagesResponse(
    val ids: List<String>
)

data class PinnedMessageInfo(
    val id: String,
    val pinned_at: String
)

data class PinnedMessagesResponse(
    val pinned: List<PinnedMessageInfo>
)'''
        ),
    ],
    "ChatApi.kt"
)

# ───────────────────────── ChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt",
    [
        (
            "import androidx.compose.material.icons.filled.EmojiEmotions",
            "import androidx.compose.material.icons.filled.EmojiEmotions\nimport androidx.compose.material.icons.filled.PushPin"
        ),
        (
            "import androidx.compose.material3.ExperimentalMaterial3Api",
            "import androidx.compose.material3.ExperimentalMaterial3Api\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem"
        ),
        (
            "    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }",
            '''    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var pinnedMessages by remember { mutableStateOf(listOf<com.muwan.muwanchat.network.PinnedMessageInfo>()) }
    var showPinnedDropdown by remember { mutableStateOf(false) }

    fun jumpToMessage(targetId: String) {
        val index = messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            scope.launch { listState.animateScrollToItem(index) }
        }
    }

    LaunchedEffect(roomId, myToken) {
        if (myToken.isBlank()) return@LaunchedEffect
        try {
            val res = RetrofitClient.chatApi.getPinnedMessages("Bearer $myToken", roomId)
            if (res.isSuccessful) {
                pinnedMessages = res.body()?.pinned ?: emptyList()
            }
        } catch (_: Exception) {}
    }'''
        ),
        (
            '''                val canReact = selectedMessageIds.size == 1 &&
                    messages.firstOrNull { it.id == selectedMessageIds.first() }?.isDeleted == false

                if (canReact) {
                    IconButton(onClick = { showReactionPicker = true }) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "React", tint = Color.White)
                    }
                }''',
            '''                val canPin = selectedMessageIds.isNotEmpty() &&
                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.isDeleted == false }

                if (canPin) {
                    IconButton(onClick = {
                        val idsToPin = selectedMessageIds.toList()
                        scope.launch {
                            idsToPin.forEach { id ->
                                try {
                                    RetrofitClient.chatApi.pinMessage("Bearer $myToken", roomId, id)
                                } catch (_: Exception) {}
                            }
                        }
                        exitSelectionMode()
                    }) {
                        Icon(Icons.Filled.PushPin, contentDescription = "Pin", tint = Color.White)
                    }
                }'''
        ),
        (
            '''                is SocketEvent.MessageEdited -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().editMessage(event.id, event.content) }
                    }
                }''',
            '''                is SocketEvent.MessageEdited -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().editMessage(event.id, event.content) }
                    }
                }
                is SocketEvent.MessagePinned -> {
                    if (event.roomId == roomId && pinnedMessages.none { it.id == event.id }) {
                        pinnedMessages = pinnedMessages + com.muwan.muwanchat.network.PinnedMessageInfo(event.id, event.pinnedAt)
                    }
                }
                is SocketEvent.MessageUnpinned -> {
                    if (event.roomId == roomId) {
                        pinnedMessages = pinnedMessages.filter { it.id != event.id }
                    }
                }'''
        ),
        (
            '''                        onReplyTap = { targetId ->
                            val index = messages.indexOfFirst { it.id == targetId }
                            if (index >= 0) {
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },''',
            '                        onReplyTap = { targetId -> jumpToMessage(targetId) },'
        ),
        (
            '''                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {''',
            '''                }
            )
        }

        if (pinnedMessages.isNotEmpty()) {
            val latestPinned = pinnedMessages.last()
            fun pinnedPreviewText(id: String): String {
                val m = messages.firstOrNull { it.id == id } ?: return "Message"
                return when {
                    m.isDeleted -> "This message was deleted"
                    m.type == "image" -> "📷 Photo"
                    m.type == "video" -> "🎥 Video"
                    m.type == "audio" -> "🎤 Voice message"
                    m.type == "music" -> "🎵 ${m.fileName ?: "Music"}"
                    m.type == "document" -> "📄 ${m.fileName ?: "Document"}"
                    m.type == "gif" -> "GIF"
                    else -> m.text
                }
            }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSheet)
                        .clickable {
                            if (pinnedMessages.size > 1) {
                                showPinnedDropdown = true
                            } else {
                                jumpToMessage(latestPinned.id)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = DarkAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        pinnedPreviewText(latestPinned.id),
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    if (pinnedMessages.size > 1) {
                        Text(
                            "${pinnedMessages.size}",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    RetrofitClient.chatApi.unpinMessage("Bearer $myToken", roomId, latestPinned.id)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Unpin", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(
                    expanded = showPinnedDropdown,
                    onDismissRequest = { showPinnedDropdown = false },
                    modifier = Modifier.background(DarkSheet)
                ) {
                    pinnedMessages.reversed().forEach { pinned ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    pinnedPreviewText(pinned.id),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.PushPin, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            RetrofitClient.chatApi.unpinMessage("Bearer $myToken", roomId, pinned.id)
                                        } catch (_: Exception) {}
                                    }
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Unpin", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                jumpToMessage(pinned.id)
                                showPinnedDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {'''
        ),
    ],
    "ChatScreen.kt"
)

# ───────────────────────── GroupChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
    [
        (
            "import androidx.compose.material.icons.filled.EmojiEmotions",
            "import androidx.compose.material.icons.filled.EmojiEmotions\nimport androidx.compose.material.icons.filled.PushPin"
        ),
        (
            '''    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }''',
            '''    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var pinnedMessages by remember { mutableStateOf(listOf<com.muwan.muwanchat.network.PinnedMessageInfo>()) }
    var showPinnedDropdown by remember { mutableStateOf(false) }

    fun jumpToMessage(targetId: String) {
        val index = messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            scope.launch { listState.animateScrollToItem(index) }
        }
    }

    LaunchedEffect(groupId, myToken) {
        if (myToken.isBlank()) return@LaunchedEffect
        try {
            val res = RetrofitClient.chatApi.getPinnedMessages("Bearer $myToken", groupId)
            if (res.isSuccessful) {
                pinnedMessages = res.body()?.pinned ?: emptyList()
            }
        } catch (_: Exception) {}
    }'''
        ),
        (
            '''                val canReact = selectedMessageIds.size == 1 &&
                    messages.firstOrNull { it.id == selectedMessageIds.first() }?.isDeleted == false

                if (canReact) {
                    IconButton(onClick = { showReactionPicker = true }) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "React", tint = Color.White)
                    }
                }''',
            '''                val canPin = selectedMessageIds.isNotEmpty() &&
                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.isDeleted == false }

                if (canPin) {
                    IconButton(onClick = {
                        val idsToPin = selectedMessageIds.toList()
                        scope.launch {
                            idsToPin.forEach { id ->
                                try {
                                    RetrofitClient.chatApi.pinMessage("Bearer $myToken", groupId, id)
                                } catch (_: Exception) {}
                            }
                        }
                        exitSelectionMode()
                    }) {
                        Icon(Icons.Filled.PushPin, contentDescription = "Pin", tint = Color.White)
                    }
                }'''
        ),
        (
            '''                is SocketEvent.MessageEdited -> {
                    if (event.roomId == groupId) {
                        scope.launch { db.messageDao().editMessage(event.id, event.content) }
                    }
                }''',
            '''                is SocketEvent.MessageEdited -> {
                    if (event.roomId == groupId) {
                        scope.launch { db.messageDao().editMessage(event.id, event.content) }
                    }
                }
                is SocketEvent.MessagePinned -> {
                    if (event.roomId == groupId && pinnedMessages.none { it.id == event.id }) {
                        pinnedMessages = pinnedMessages + com.muwan.muwanchat.network.PinnedMessageInfo(event.id, event.pinnedAt)
                    }
                }
                is SocketEvent.MessageUnpinned -> {
                    if (event.roomId == groupId) {
                        pinnedMessages = pinnedMessages.filter { it.id != event.id }
                    }
                }'''
        ),
        (
            '''                        onReplyTap = { targetId ->
                            val index = messages.indexOfFirst { it.id == targetId }
                            if (index >= 0) {
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },''',
            '                        onReplyTap = { targetId -> jumpToMessage(targetId) },'
        ),
        (
            '''                onMessageTheme = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.MessageTheme.createRoute(groupId))
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {''',
            '''                onMessageTheme = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.MessageTheme.createRoute(groupId))
                }
            )
        }

        if (pinnedMessages.isNotEmpty()) {
            val latestPinned = pinnedMessages.last()
            fun pinnedPreviewText(id: String): String {
                val m = messages.firstOrNull { it.id == id } ?: return "Message"
                return when {
                    m.isDeleted -> "This message was deleted"
                    m.type == "image" -> "📷 Photo"
                    m.type == "video" -> "🎥 Video"
                    m.type == "audio" -> "🎤 Voice message"
                    m.type == "music" -> "🎵 ${m.fileName ?: "Music"}"
                    m.type == "document" -> "📄 ${m.fileName ?: "Document"}"
                    m.type == "gif" -> "GIF"
                    else -> m.text
                }
            }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSheet)
                        .clickable {
                            if (pinnedMessages.size > 1) {
                                showPinnedDropdown = true
                            } else {
                                jumpToMessage(latestPinned.id)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = DarkAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        pinnedPreviewText(latestPinned.id),
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    if (pinnedMessages.size > 1) {
                        Text(
                            "${pinnedMessages.size}",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    RetrofitClient.chatApi.unpinMessage("Bearer $myToken", groupId, latestPinned.id)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Unpin", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(
                    expanded = showPinnedDropdown,
                    onDismissRequest = { showPinnedDropdown = false },
                    modifier = Modifier.background(DarkSheet)
                ) {
                    pinnedMessages.reversed().forEach { pinned ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    pinnedPreviewText(pinned.id),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.PushPin, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            RetrofitClient.chatApi.unpinMessage("Bearer $myToken", groupId, pinned.id)
                                        } catch (_: Exception) {}
                                    }
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Unpin", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                jumpToMessage(pinned.id)
                                showPinnedDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {'''
        ),
    ],
    "GroupChatScreen.kt"
)
PYEOF

echo ""
echo "Verifying brace/paren balance..."
for f in "$SOCKET_FILE" "$API_FILE" "$CHAT_FILE" "$GROUP_FILE"; do
    python3 -c "
content = open('$f').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$f -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"
done
