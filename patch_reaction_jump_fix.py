import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
content = open(path, encoding="utf-8").read()

# --- Edit 1: onLongPress -- quick-react ab full selection mode enable nahi karta ---
old1 = '''                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        },'''

new1 = '''                        onLongPress = {
                            if (!isSelectionMode) {
                                if (it.isDeleted) {
                                    // Deleted message pe react nahi ho sakta -- yahan asli
                                    // selection mode chahiye (bulk actions ke liye)
                                    isSelectionMode = true
                                    selectedMessageIds = setOf(it.id)
                                } else {
                                    // Sirf quick-react ke liye poora selection-mode (jo header
                                    // badal deta hai aur list reflow/jump karti hai) enable nahi
                                    // karte -- sirf reaction dialog kholte hain.
                                    selectedMessageIds = setOf(it.id)
                                    showReactionPicker = true
                                }
                            }
                        },'''

if old1 not in content:
    sys.exit("Anchor 1 not found -- mujhe 'sed -n \"1005,1020p\" GroupChatScreen.kt' ka output bhejo.")
content = content.replace(old1, new1, 1)

# --- Edit 2: dialog dismiss par selection saaf karo (agar asli selection mode nahi hai) ---
old2 = '''    if (showReactionPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
        }) {'''

new2 = '''    if (showReactionPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            showReactionPicker = false
            showCustomEmojiField = false
            customEmojiInput = ""
            if (!isSelectionMode) selectedMessageIds = emptySet()
        }) {'''

if old2 not in content:
    sys.exit("Anchor 2 not found -- mujhe 'grep -n \"showReactionPicker) {\" GroupChatScreen.kt' ka output bhejo.")
content = content.replace(old2, new2, 1)

open(path, "w", encoding="utf-8").write(content)
print("GroupChatScreen.kt patched: reaction ab list ko jump nahi karayegi.")
