#!/usr/bin/env python3
# Call bubble -- APP patch PART 3/3. Project root se run karo (jahan app/ folder hai).
import sys, os

BASE = "app/src/main/java/com/muwan/muwanchat/"

def patch(rel, pairs):
    path = BASE + rel
    if not os.path.exists(path):
        sys.exit("ERROR: %s nahi mila -- project root se run karo" % path)
    s = open(path, encoding="utf-8").read()
    if "CALL_BUBBLE_PATCH" in s:
        print("SKIP  %s (already patched)" % rel)
        return
    for old, new, expected in pairs:
        n = s.count(old)
        if n != expected:
            sys.exit("ERROR: %s mein anchor %d baar mila (%d chahiye):\n%s" % (rel, n, expected, old[:140]))
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)
    print("OK    %s" % rel)

# ───────────────────────── 7) MessageBubble.kt ─────────────────────────
CALL_BUBBLE_COMPOSABLE = r'''

// CALL_BUBBLE_PATCH
// Call history bubble: sent = caller (apna color = Message Theme ka sentColor),
// received = DarkBubbleReceived. Shape/padding/font bhi theme.compact ke hisaab se,
// taaki custom theme ke saath bilkul match kare. Green dot = ringing/connected/ended,
// red dot = missed/declined. Tap = wapas call.
@Composable
private fun CallBubble(
    message: ChatMessage,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    bubbleTheme: BubbleTheme,
    onTap: () -> Unit,
    onCallTap: (ChatMessage) -> Unit,
    onLongPress: (ChatMessage) -> Unit
) {
    val info = remember(message.callInfo) { parseCallInfo(message.callInfo) }
    val isRed = info.status == "missed" || info.status == "declined"
    val dotColor = if (isRed) Color(0xFFFF3B30) else Color(0xFF00E676)
    val kindLabel = if (info.kind == "video") "video call" else "voice call"
    val title = when (info.status) {
        "ringing" -> if (message.sent) "Calling…" else "Incoming $kindLabel"
        "answered" -> kindLabel.replaceFirstChar { it.uppercase() }
        "declined" -> "Call declined"
        "missed" -> if (message.sent) "No answer" else "Missed $kindLabel"
        else -> kindLabel.replaceFirstChar { it.uppercase() }
    }
    val subtitle = when {
        info.status == "ended" && info.duration > 0 -> "${message.time} · ${formatCallDuration(info.duration)}"
        info.status == "answered" -> "${message.time} · In call"
        else -> message.time
    }

    val bg = if (message.sent) bubbleTheme.sentColor else DarkBubbleReceived
    val cornerBig = if (bubbleTheme.compact) 14.dp else 18.dp
    val cornerTail = 4.dp
    val hPad = if (bubbleTheme.compact) 10.dp else 14.dp
    val vPad = if (bubbleTheme.compact) 7.dp else 10.dp
    val titleSize = if (bubbleTheme.compact) 14.sp else 15.sp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) DarkAccent else Color(0xFF333355)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 190.dp, max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = cornerBig, topEnd = cornerBig,
                            bottomEnd = if (message.sent) cornerTail else cornerBig,
                            bottomStart = if (message.sent) cornerBig else cornerTail
                        )
                    )
                    .background(bg)
                    .pointerInput(message.id, message.callInfo, isSelectionMode) {
                        detectTapGestures(
                            onLongPress = { onLongPress(message) },
                            onTap = { if (isSelectionMode) onTap() else onCallTap(message) }
                        )
                    }
                    .padding(horizontal = hPad, vertical = vPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = titleSize,
                        fontWeight = if (info.status == "missed") FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatCallDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
'''

# MessageBubble.kt ke andar 'return' wala system block ke baad, context line se pehle call block
MB_PATH = BASE + "screens/MessageBubble.kt"
patch("screens/MessageBubble.kt", [
    ("import androidx.compose.material.icons.filled.Block\n",
     "import androidx.compose.material.icons.filled.Block\nimport androidx.compose.material.icons.filled.Call // CALL_BUBBLE_PATCH\n", 1),
    ("import com.muwan.muwanchat.data.BubbleThemePresets\n",
     "import com.muwan.muwanchat.data.BubbleThemePresets\nimport com.muwan.muwanchat.data.parseCallInfo\n", 1),
    ("    bubbleTheme: BubbleTheme = BubbleThemePresets.ORIGINAL,\n    groupMemberUsernames: List<String> = emptyList()\n) {\n",
     "    bubbleTheme: BubbleTheme = BubbleThemePresets.ORIGINAL,\n    groupMemberUsernames: List<String> = emptyList(),\n    onCallTap: (ChatMessage) -> Unit = {}\n) {\n", 1),
    ("        return\n    }\n\n    val context = LocalContext.current\n    val clipboardManager = LocalClipboardManager.current\n",
     "        return\n    }\n\n"
     "    if (message.type == \"call\") { // CALL_BUBBLE_PATCH\n"
     "        CallBubble(\n"
     "            message = message,\n"
     "            isSelectionMode = isSelectionMode,\n"
     "            isSelected = isSelected,\n"
     "            bubbleTheme = bubbleTheme,\n"
     "            onTap = onTap,\n"
     "            onCallTap = onCallTap,\n"
     "            onLongPress = onLongPress\n"
     "        )\n"
     "        return\n"
     "    }\n\n"
     "    val context = LocalContext.current\n    val clipboardManager = LocalClipboardManager.current\n", 1),
])
# composable ko file ke end mein jodo (idempotent: marker 'private fun CallBubble' check)
s = open(MB_PATH, encoding="utf-8").read()
if "private fun CallBubble(" not in s:
    open(MB_PATH, "a", encoding="utf-8").write(CALL_BUBBLE_COMPOSABLE)
    print("OK    screens/MessageBubble.kt (CallBubble composable added)")

print("Part 3 done. Ab git add/commit/push -> GitHub Actions beta build.")
