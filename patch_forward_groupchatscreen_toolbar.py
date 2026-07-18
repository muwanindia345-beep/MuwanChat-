import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.material.icons.filled.Check"
new_import = "import androidx.compose.material.icons.filled.Check\nimport androidx.compose.material.icons.filled.Send"
if old_import not in content:
    print("Icon import anchor not found!"); sys.exit(1)
content = content.replace(old_import, new_import, 1)

old = '''                if (canReact) {
                    IconButton(onClick = { showReactionPicker = true }) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "React", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = { if (selectedMessageIds.isNotEmpty()) showBulkDeleteConfirm = true },
                    enabled = selectedMessageIds.isNotEmpty()
                ) {'''
new = '''                if (canReact) {
                    IconButton(onClick = { showReactionPicker = true }) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "React", tint = Color.White)
                    }
                }

                val canForward = selectedMessageIds.isNotEmpty() &&
                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.isDeleted == false }

                if (canForward) {
                    IconButton(onClick = {
                        val toForward = messages.filter { selectedMessageIds.contains(it.id) }
                        ForwardMessageSelection.set(toForward)
                        exitSelectionMode()
                        navController.navigate(com.muwan.muwanchat.navigation.Screen.Forward.route)
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Forward", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = { if (selectedMessageIds.isNotEmpty()) showBulkDeleteConfirm = true },
                    enabled = selectedMessageIds.isNotEmpty()
                ) {'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("GroupChatScreen.kt patched: selection toolbar mein Forward icon add hua")
