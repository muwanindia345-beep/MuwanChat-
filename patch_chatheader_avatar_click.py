import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_sig = '''    avatarBase64: String? = null,
    onBack: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {}
) {'''
new_sig = '''    avatarBase64: String? = null,
    onBack: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {},
    onAvatarClick: (() -> Unit)? = null
) {'''
if old_sig not in content:
    print("Signature anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)

old_avatar = '''            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = receiverUsername,
                size = 38.dp,
                fontSize = 16.sp
            )'''
new_avatar = '''            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = receiverUsername,
                size = 38.dp,
                fontSize = 16.sp,
                onClick = onAvatarClick
            )'''
if old_avatar not in content:
    print("AvatarView call anchor not found!"); sys.exit(1)
content = content.replace(old_avatar, new_avatar, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatHeader.kt patched: onAvatarClick wired to AvatarView")
