import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

orig = content
patches = []

# 1. Add isSticker flag
old1 = '''    val isMedia = message.type == "image" || message.type == "gif" || message.type == "video" || message.type == "audio"
'''
new1 = '''    val isMedia = message.type == "image" || message.type == "gif" || message.type == "video" || message.type == "audio"
    // Sticker/GIF ka apna pattern hai: koi chat-bubble background/padding nahi (WhatsApp jaisa),
    // bada size, aur timestamp seedha image ke corner pe overlay hota hai — niche alag row nahi.
    val isSticker = message.type == "gif"
'''
patches.append((old1, new1, "isSticker flag"))

# 2. Transparent background/padding for sticker
old2 = '''                .background(if (message.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(
                    horizontal = if (isMedia && !message.isDeleted) 4.dp else 14.dp,
                    vertical = if (isMedia && !message.isDeleted) 4.dp else 10.dp
                )'''
new2 = '''                .background(if (isSticker) Color.Transparent else if (message.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(
                    horizontal = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else 14.dp,
                    vertical = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else 10.dp
                )'''
patches.append((old2, new2, "transparent bubble for sticker"))

# 3. Replace gif rendering with bigger sticker + overlay timestamp
old3 = '''                    "gif" -> message.mediaUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "GIF",
                            modifier = Modifier
                                .sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 200.dp, maxHeight = 200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(4.dp))
                    }
'''
new3 = '''                    "gif" -> message.mediaUrl?.let { url ->
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Sticker",
                                modifier = Modifier
                                    .sizeIn(minWidth = 140.dp, minHeight = 140.dp, maxWidth = 180.dp, maxHeight = 180.dp)
                                    .clickable { if (isSelectionMode) onTap() },
                                contentScale = ContentScale.Fit
                            )
                            Row(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(message.time, color = Color.White, fontSize = 10.sp)
                                if (message.sent) {
                                    Spacer(Modifier.width(3.dp))
                                    val (icon, tint) = when (message.status) {
                                        "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                                        "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                                        "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                                        else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                                    }
                                    Icon(icon, contentDescription = message.status, tint = tint, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
'''
patches.append((old3, new3, "sticker box with overlay timestamp"))

# 4. Skip shared timestamp row for stickers
old4 = '''                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            "edited",
                            color = Color(0x88FFFFFF),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        message.time,
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp
                    )
                    if (message.sent) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, tint) = when (message.status) {
                            "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                            "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                            "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                            else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status,
                            tint = tint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }'''
new4 = '''                if (!isSticker) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            "edited",
                            color = Color(0x88FFFFFF),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        message.time,
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp
                    )
                    if (message.sent) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, tint) = when (message.status) {
                            "PENDING" -> Icons.Filled.AccessTime to Color(0xAAFFFFFF)
                            "SEEN" -> Icons.Filled.DoneAll to Color(0xFF4CAF50)
                            "FAILED" -> Icons.Filled.ErrorOutline to Color(0xFFE53935)
                            else -> Icons.Filled.Check to Color(0xAAFFFFFF)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status,
                            tint = tint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                }'''
patches.append((old4, new4, "skip shared timestamp row for stickers"))

for old, new, label in patches:
    if old in content:
        content = content.replace(old, new)
        print(f"[OK] {label}")
    else:
        print(f"[SKIP] {label} — pattern not found (already patched or file differs)")

if content == orig:
    print("\\n[WARN] Nothing changed. Check file manually.")
    sys.exit(1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("\\nDone.")
