import io, sys
P = "app/src/main/java/com/muwan/muwanchat/screens/CallHistoryScreen.kt"
s = io.open(P, encoding="utf-8", newline="").read()
if "CALL_HISTORY_LIVE" in s:
    print("already patched"); sys.exit(0)
crlf = "\r\n" in s
s = s.replace("\r\n", "\n")

def rep(old, new):
    global s
    if s.count(old) != 1:
        print("NOT FOUND / not unique:\n" + old[:80]); sys.exit(1)
    s = s.replace(old, new)

rep("import androidx.compose.runtime.collectAsState\n",
    "import androidx.compose.runtime.collectAsState\n"
    "import androidx.compose.runtime.LaunchedEffect\n"
    "import androidx.compose.runtime.mutableStateOf\n"
    "import androidx.compose.runtime.setValue\n"
    "import androidx.compose.foundation.shape.CircleShape\n"
    "import androidx.compose.ui.draw.clip\n"
    "import kotlinx.coroutines.delay\n"
    "import com.muwan.muwanchat.data.parseCallInfo\n"
    "import java.text.SimpleDateFormat\n"
    "import java.util.Locale\n"
    "import java.util.TimeZone\n")

rep("    val avatar: String?\n)\n",
    "    val avatar: String?,\n"
    "    val lastStatus: String,   // CALL_HISTORY_LIVE\n"
    "    val lastCreatedAt: String\n)\n")
rep("                    avatar = conv.avatar\n",
    "                    avatar = conv.avatar,\n"
    "                    lastStatus = parseCallInfo(m.content).status,\n"
    "                    lastCreatedAt = m.createdAt\n")

rep("    val contacts = remember(callMessages, conversations) {",
    "    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }\n"
    "    LaunchedEffect(Unit) {\n"
    "        while (true) { delay(15_000); nowMs = System.currentTimeMillis() }\n"
    "    }\n\n"
    "    val contacts = remember(callMessages, conversations) {")

rep("""                            onClick = {
                                navController.navigate(
                                    Screen.Chat.createRoute(c.uid, c.username, c.roomId)
                                )
                            },
""", "                            nowMs = nowMs,\n")
rep("    contact: CallContact,\n    onClick: () -> Unit,\n",
    "    contact: CallContact,\n    nowMs: Long,\n")
rep("            .clickable { onClick() }\n", "")

rep("""        Text(
            contact.username,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
""", """        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.username,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val createdMs = callCreatedMs(contact.lastCreatedAt)
            val ageMs = if (createdMs != null) nowMs - createdMs else Long.MAX_VALUE
            // 6 ghante se purana "answered" = stale, ongoing nahi maante
            val ongoing = contact.lastStatus == "answered" && ageMs < 6L * 60 * 60 * 1000
            val dateTime = callDateTimeText(contact.lastCreatedAt)
            Spacer(modifier = Modifier.height(2.dp))
            when {
                ongoing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ongoing call", color = Color(0xFF25D366), fontSize = 13.sp)
                }
                contact.lastStatus == "missed" ->
                    Text("Missed call \\u00B7 $dateTime", color = Color(0xFFFF5252), fontSize = 13.sp, maxLines = 1)
                else ->
                    Text(dateTime, color = Color(0xFF888888), fontSize = 13.sp, maxLines = 1)
            }
        }
""")

s = s.rstrip("\n") + """

// CALL_HISTORY_LIVE: createdAt UTC ISO hota hai -> local "05 Oct, 1:43 PM"
private fun callCreatedMs(raw: String): Long? = try {
    val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    p.timeZone = TimeZone.getTimeZone("UTC")
    p.parse(raw.take(19))?.time
} catch (_: Exception) { null }

private fun callDateTimeText(raw: String): String {
    val ms = callCreatedMs(raw) ?: return raw.take(16).replace("T", " ")
    val f = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
    f.timeZone = TimeZone.getDefault()
    return f.format(java.util.Date(ms))
}
"""
if crlf: s = s.replace("\n", "\r\n")
io.open(P, "w", encoding="utf-8", newline="").write(s)
print("OK patched", P)
