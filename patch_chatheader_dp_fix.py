import re

path = "app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.ui.text.font.FontWeight\n"
new_import = "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow\n"

old_block = """        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = receiverUsername,
                size = 38.dp,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(receiverUsername, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val statusText = when {
                    isTyping -> "typing..."
                    isOnline -> "Online"
                    else -> "Offline"
                }
                val statusColor = when {
                    isTyping -> DarkAccent
                    isOnline -> Color(0xFF4CD964)
                    else -> Color(0xFF888888)
                }
                Text(statusText, color = statusColor, fontSize = 12.sp)
            }
        }"""

new_block = """        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = receiverUsername,
                size = 38.dp,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    receiverUsername,
                    color = DarkAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = when {
                    isTyping -> "typing..."
                    isOnline -> "Online"
                    else -> "Offline"
                }
                val statusColor = when {
                    isTyping -> DarkAccent
                    isOnline -> Color(0xFF4CD964)
                    else -> Color(0xFF888888)
                }
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }"""

if content.count(old_import) == 0:
    print("ERROR: import anchor not found")
else:
    content = content.replace(old_import, new_import, 1)

count = content.count(old_block)
if count == 0:
    print("ERROR: old_block not found, file already patched or changed manually")
else:
    content = content.replace(old_block, new_block)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Patched ChatHeader.kt left-Row weight + ellipsis fix ({count} block)")
