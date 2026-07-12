import io
path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """    val conversations = remember(conversationEntities, onlineUids) {
        conversationEntities.map { e ->
            ConversationItem(
                room_id = e.roomId,
                uid = e.uid,
                username = e.username,
                avatar = e.avatar,
                lastMessage = e.lastMessage,
                lastTime = e.lastTime,
                isOnline = onlineUids.contains(e.uid),
                unreadCount = e.unreadCount,
                lastSenderUid = e.lastSenderUid
            )
        }
    }"""
new1 = """    val conversations = remember(conversationEntities, onlineUids) {
        conversationEntities.map { e ->
            ConversationItem(
                room_id = e.roomId,
                uid = e.uid,
                username = e.username,
                avatar = e.avatar,
                lastMessage = e.lastMessage,
                lastTime = e.lastTime,
                isOnline = if (e.isGroup) e.onlineCount > 0 else onlineUids.contains(e.uid),
                unreadCount = e.unreadCount,
                lastSenderUid = e.lastSenderUid,
                isGroup = e.isGroup,
                memberCount = e.memberCount,
                onlineCount = e.onlineCount
            )
        }
    }"""

old2 = """            } else {
                Text(
                    if (conv.isOnline) "Online" else "Offline",
                    color = if (conv.isOnline) Color(0xFF4CD964) else Color(0xFFFF3B30),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }"""
new2 = """            } else if (conv.isGroup) {
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
            }"""

old3 = """                                } else {
                                    scope.launch { ChatRepository.clearUnread(db, conv.room_id) }
                                    navController.navigate(
                                        Screen.Chat.createRoute(conv.uid, conv.username, conv.room_id)
                                    )
                                }"""
new3 = """                                } else {
                                    scope.launch { ChatRepository.clearUnread(db, conv.room_id) }
                                    if (conv.isGroup) {
                                        navController.navigate(
                                            Screen.GroupChat.createRoute(conv.room_id, conv.username)
                                        )
                                    } else {
                                        navController.navigate(
                                            Screen.Chat.createRoute(conv.uid, conv.username, conv.room_id)
                                        )
                                    }
                                }"""

for old, new, label in [(old1, new1, "conversations mapping"), (old2, new2, "row status text"), (old3, new3, "click navigation")]:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched ConversationListScreen.kt")
