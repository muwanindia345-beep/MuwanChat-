import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_sig = '''fun ConversationRow(
    conv: ConversationItem,
    isTyping: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {'''
new_sig = '''fun ConversationRow(
    conv: ConversationItem,
    isTyping: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAvatarClick: (() -> Unit)? = null
) {'''
if old_sig not in content:
    print("Signature anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)

old_avatar = '''            AvatarView(
                avatarBase64 = conv.avatar,
                fallbackText = conv.username,
                size = 50.dp,
                fontSize = 20.sp
            )'''
new_avatar = '''            AvatarView(
                avatarBase64 = conv.avatar,
                fallbackText = conv.username,
                size = 50.dp,
                fontSize = 20.sp,
                onClick = if (!isSelectionMode) onAvatarClick else null
            )'''
if old_avatar not in content:
    print("AvatarView call anchor not found!"); sys.exit(1)
content = content.replace(old_avatar, new_avatar, 1)

old_call = '''                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedRoomIds = setOf(conv.room_id)
                                }
                            }
                        )'''
new_call = '''                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedRoomIds = setOf(conv.room_id)
                                }
                            },
                            onAvatarClick = {
                                AvatarViewerSelection.set(conv.avatar, conv.username)
                                navController.navigate(Screen.ViewAvatar.route)
                            }
                        )'''
if old_call not in content:
    print("ConversationRow call anchor not found!"); sys.exit(1)
content = content.replace(old_call, new_call, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ConversationListScreen.kt patched: avatar tap opens ViewAvatarScreen")
