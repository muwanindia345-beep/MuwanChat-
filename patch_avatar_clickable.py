import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/AvatarView.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.foundation.background"
new_import = "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable"
if old_import not in content:
    print("Import anchor not found!"); sys.exit(1)
content = content.replace(old_import, new_import, 1)

old_sig = '''fun AvatarView(
    avatarBase64: String?,
    fallbackText: String,
    size: Dp = 46.dp,
    fontSize: TextUnit = 18.sp
) {'''
new_sig = '''fun AvatarView(
    avatarBase64: String?,
    fallbackText: String,
    size: Dp = 46.dp,
    fontSize: TextUnit = 18.sp,
    onClick: (() -> Unit)? = null
) {'''
if old_sig not in content:
    print("Signature anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)

old_box = '''    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(DarkAccent),
        contentAlignment = Alignment.Center
    ) {'''
new_box = '''    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(DarkAccent)
            .let { m -> if (onClick != null) m.clickable { onClick() } else m },
        contentAlignment = Alignment.Center
    ) {'''
if old_box not in content:
    print("Box anchor not found!"); sys.exit(1)
content = content.replace(old_box, new_box, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("AvatarView.kt patched: onClick param added, avatar ab clickable hai")
