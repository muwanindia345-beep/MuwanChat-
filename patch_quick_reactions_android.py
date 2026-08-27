import os
import shutil
import sys

BASE = "app/src/main/java/com/muwan/muwanchat"

def fail(msg):
    print(f"FAILED: {msg}")
    sys.exit(1)

def read(path):
    if not os.path.exists(path):
        fail(f"{path} not found -- run this from the repo root")
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def backup(path):
    shutil.copy(path, path + ".bak")
    print(f"Backed up {path} -> {path}.bak")

def replace_once(content, old, new, label):
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected 1 match, found {count}")
    return content.replace(old, new, 1)

users_api_path = f"{BASE}/network/UsersApi.kt"
backup(users_api_path)
content = read(users_api_path)

old_data_classes_anchor = "data class StatusesResponse(\n    val statuses: Map<String, String> = emptyMap()\n)"
new_data_classes = old_data_classes_anchor + """

data class QuickReactionsResponse(
    val reactions: List<String>
)

data class QuickReactionsBody(
    val reactions: List<String>
)"""
content = replace_once(content, old_data_classes_anchor, new_data_classes, "UsersApi data classes")

old_interface_end = """    @POST("users/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}"""
new_interface_end = """    @POST("users/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @GET("users/quick-reactions")
    suspend fun getQuickReactions(
        @Header("Authorization") token: String
    ): Response<QuickReactionsResponse>

    @PUT("users/quick-reactions")
    suspend fun updateQuickReactions(
        @Header("Authorization") token: String,
        @Body body: QuickReactionsBody
    ): Response<QuickReactionsResponse>
}"""
content = replace_once(content, old_interface_end, new_interface_end, "UsersApi interface end")
write(users_api_path, content)
print(f"Patched {users_api_path}")

chat_screen_path = f"{BASE}/screens/ChatScreen.kt"
backup(chat_screen_path)
content = read(chat_screen_path)

old_import = "import androidx.compose.foundation.clickable"
new_import = """import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.muwan.muwanchat.data.QuickReactionsStore
import com.muwan.muwanchat.network.QuickReactionsBody"""
content = replace_once(content, old_import, new_import, "ChatScreen imports")

old_state = '    val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")'
new_state = """    var quickReactions by remember { mutableStateOf(QuickReactionsStore.get(context, AuthDataStore.getUidBlocking(context))) }
    var showFullEmojiSheet by remember { mutableStateOf(false) }
    var customizingSlotIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(myUid) {
        if (myUid.isBlank() || myToken.isBlank()) return@LaunchedEffect
        try {
            val res = RetrofitClient.usersApi.getQuickReactions("Bearer $myToken")
            if (res.isSuccessful) {
                res.body()?.reactions?.let { fetched ->
                    if (fetched.size == 6) {
                        quickReactions = fetched
                        QuickReactionsStore.save(context, myUid, fetched)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun updateQuickReactionSlot(index: Int, emoji: String) {
        val updated = quickReactions.toMutableList().also { it[index] = emoji }
        quickReactions = updated
        QuickReactionsStore.save(context, myUid, updated)
        scope.launch {
            try {
                RetrofitClient.usersApi.updateQuickReactions("Bearer $myToken", QuickReactionsBody(updated))
            } catch (_: Exception) {
            }
        }
    }"""
content = replace_once(content, old_state, new_state, "quickReactions state block")

old_toggle = """            onToggleEmojiPicker = {
                showEmojiPicker = !showEmojiPicker
                if (showEmojiPicker) keyboardController?.hide() else keyboardController?.show()
            },"""
new_toggle = """            onToggleEmojiPicker = {
                if (isSelectionMode && selectedMessageIds.size == 1) {
                    showReactionPicker = true
                } else {
                    showEmojiPicker = !showEmojiPicker
                    if (showEmojiPicker) keyboardController?.hide() else keyboardController?.show()
                }
            },"""
content = replace_once(content, old_toggle, new_toggle, "ChatInputBar onToggleEmojiPicker")

old_row = """                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    quickReactions.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { reactToSelected(emoji) }
                                .padding(6.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A55))
                            .clickable { showCustomEmojiField = !showCustomEmojiField },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Custom emoji", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }"""
new_row = """                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    quickReactions.forEachIndexed { index, emoji ->
                        Text(
                            emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { reactToSelected(emoji) },
                                    onLongClick = {
                                        customizingSlotIndex = index
                                        showFullEmojiSheet = true
                                    }
                                )
                                .padding(6.dp)
                        )
                    }
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "More emojis",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable {
                                customizingSlotIndex = null
                                showFullEmojiSheet = true
                            }
                            .padding(4.dp)
                    )
                }"""
content = replace_once(content, old_row, new_row, "quick reaction row")

old_dialog_close = """    if (showReactionPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
        }) {"""
new_dialog_close = """    if (showFullEmojiSheet) {
        EmojiBottomSheet(
            onEmojiSelected = { emoji ->
                val slot = customizingSlotIndex
                showFullEmojiSheet = false
                customizingSlotIndex = null
                if (slot != null) {
                    updateQuickReactionSlot(slot, emoji)
                } else {
                    reactToSelected(emoji)
                }
            },
            onDismiss = {
                showFullEmojiSheet = false
                customizingSlotIndex = null
            }
        )
    }

    if (showReactionPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
        }) {"""
content = replace_once(content, old_dialog_close, new_dialog_close, "EmojiBottomSheet wiring")

write(chat_screen_path, content)
print(f"Patched {chat_screen_path}")
print("Done.")
