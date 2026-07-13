import io
path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """    onRetry: (ChatMessage) -> Unit = {},
    onReplyTap: (String) -> Unit = {},
    onLongPress: (ChatMessage) -> Unit = {}
) {"""

new1 = """    onRetry: (ChatMessage) -> Unit = {},
    onReplyTap: (String) -> Unit = {},
    onLongPress: (ChatMessage) -> Unit = {},
    senderAvatar: String? = null,
    senderName: String? = null
) {
    if (message.type == "system") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    message.text,
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }
"""

old2 = """        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start
        ) {
        Column(horizontalAlignment = if (message.sent) Alignment.End else Alignment.Start) {"""

new2 = """        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
        if (!message.sent && senderName != null) {
            AvatarView(
                avatarBase64 = senderAvatar,
                fallbackText = senderName,
                size = 26.dp,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (message.sent) Alignment.End else Alignment.Start) {
        if (!message.sent && senderName != null) {
            Text(
                senderName,
                color = DarkAccent,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }"""

patches = [(old1, new1, "signature + system message render"), (old2, new2, "sender avatar + name")]
for old, new, label in patches:
    count = content.count(old)
    if count != 1:
        print(f"MATCH FAILED ({label}): found {count}, expected 1")
    else:
        content = content.replace(old, new)
        print(f"Patched: {label}")

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Done -> MessageBubble.kt")
