# -*- coding: utf-8 -*-
# ConversationRow (already public, ConversationListScreen.kt me defined)
# me naya optional param `showOnlineStatus` add karta hai -- default true
# hai, isliye normal Chats list bilkul waisa hi dikhega jaisa abhi dikhta
# hai. Broadcast channel rows ke liye false pass karenge taaki online/
# offline text bilkul na dikhe, sirf timestamp+unread badge dikhe.

path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_sig = '''@Composable
fun ConversationRow(
    conv: ConversationItem,
    isTyping: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAvatarClick: (() -> Unit)? = null
) {'''

new_sig = '''@Composable
fun ConversationRow(
    conv: ConversationItem,
    isTyping: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    showOnlineStatus: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAvatarClick: (() -> Unit)? = null
) {'''

n = src.count(old_sig)
if n != 1:
    raise SystemExit(f"[FAIL] ConversationRow signature: found {n} matches (expected 1)")
src = src.replace(old_sig, new_sig, 1)

old_trailing = '''            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkAccent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (conv.unreadCount > 9) "9+" else "${conv.unreadCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (conv.isGroup) {
                val othersCount = (conv.memberCount - 1).coerceAtLeast(0)
                val offlineCount = (othersCount - conv.onlineCount).coerceAtLeast(0)
                val statusText = if (conv.onlineCount > 0)
                    "Online (${if (conv.onlineCount > 9) "9+" else "${conv.onlineCount}"})"
                else
                    "Offline (${if (offlineCount > 9) "9+" else "${offlineCount}"})"
                Text(
                    statusText,
                    color = if (conv.onlineCount > 0) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    if (conv.isOnline) "Online" else "Offline",
                    color = if (conv.isOnline) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }'''

new_trailing = '''            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkAccent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (conv.unreadCount > 9) "9+" else "${conv.unreadCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!showOnlineStatus) {
                // Broadcast channel row -- sirf timestamp, koi online/
                // offline text nahi.
            } else if (conv.isGroup) {
                val othersCount = (conv.memberCount - 1).coerceAtLeast(0)
                val offlineCount = (othersCount - conv.onlineCount).coerceAtLeast(0)
                val statusText = if (conv.onlineCount > 0)
                    "Online (${if (conv.onlineCount > 9) "9+" else "${conv.onlineCount}"})"
                else
                    "Offline (${if (offlineCount > 9) "9+" else "${offlineCount}"})"
                Text(
                    statusText,
                    color = if (conv.onlineCount > 0) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    if (conv.isOnline) "Online" else "Offline",
                    color = if (conv.isOnline) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }'''

n2 = src.count(old_trailing)
if n2 != 1:
    raise SystemExit(f"[FAIL] trailing status block: found {n2} matches (expected 1)")
src = src.replace(old_trailing, new_trailing, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] ConversationRow supports showOnlineStatus=false (used by Broadcast Channels list)")
