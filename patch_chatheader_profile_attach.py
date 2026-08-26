import sys

# ── File 1: ChatHeader.kt ────────────────────────────────────────────
path1 = "app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt"
with open(path1, "r", encoding="utf-8") as f:
    content1 = f.read()

# clickable import add karo
old_import = "import androidx.compose.foundation.background"
new_import = "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
if old_import not in content1:
    print("ChatHeader.kt: import anchor not found!"); sys.exit(1)
content1 = content1.replace(old_import, new_import, 1)

# left-side Row (back + avatar + name) ko clickable banao — onAvatarClick hi
# reuse hoga poore header-tap ke liye, ChatScreen se ab profile screen khulega
old_row = '''        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {'''
new_row = '''        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(enabled = onAvatarClick != null) { onAvatarClick?.invoke() }
        ) {'''
if old_row not in content1:
    print("ChatHeader.kt: Row anchor not found!"); sys.exit(1)
content1 = content1.replace(old_row, new_row, 1)

with open(path1, "w", encoding="utf-8") as f:
    f.write(content1)
print("ChatHeader.kt patched: pura header-area ab clickable (onAvatarClick reuse)")

# ── File 2: ChatScreen.kt ─────────────────────────────────────────────
path2 = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path2, "r", encoding="utf-8") as f:
    content2 = f.read()

old_call = '''                onAvatarClick = {
                    AvatarViewerSelection.set(conversationEntity?.avatar, receiverUsername)
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.ViewAvatar.route)
                }
            )'''
new_call = '''                onAvatarClick = {
                    navController.navigate(
                        com.muwan.muwanchat.navigation.Screen.UserProfile.createRoute(receiverUid)
                    )
                }
            )'''
if old_call not in content2:
    print("ChatScreen.kt: ChatHeader call anchor not found!"); sys.exit(1)
content2 = content2.replace(old_call, new_call, 1)

with open(path2, "w", encoding="utf-8") as f:
    f.write(content2)
print("ChatScreen.kt patched: header/avatar tap ab UserProfileScreen kholega")
