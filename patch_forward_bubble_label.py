import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.material.icons.filled.PlayCircle"
new_import = "import androidx.compose.material.icons.filled.PlayCircle\nimport androidx.compose.material.icons.filled.Send"
if old_import not in content:
    print("Icon import anchor not found!"); sys.exit(1)
content = content.replace(old_import, new_import, 1)

old = '''            } else {
            Column {
                message.replyTo?.let { reply ->'''
new = '''            } else {
            Column {
                if (message.isForwarded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Forwarded",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Forwarded",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                message.replyTo?.let { reply ->'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("MessageBubble.kt patched: Forwarded label bubble ke andar upar dikhega")
