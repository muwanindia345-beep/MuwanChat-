path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path) as f:
    content = f.read()

# --- 1. mapping: entity -> ConversationItem me naye fields pass karo ---
old1 = '''                isGroup = e.isGroup,
                memberCount = e.memberCount,
                onlineCount = e.onlineCount
            )
        }
    }'''
new1 = '''                isGroup = e.isGroup,
                memberCount = e.memberCount,
                onlineCount = e.onlineCount,
                isRemoved = e.isRemoved,
                removedByUsername = e.removedByUsername
            )
        }
    }'''
assert old1 in content, "mapping block not found"
content = content.replace(old1, new1, 1)

# --- 2. socket event handling: GroupRemoved case add karo ---
old2 = '''                is SocketEvent.RequestAccepted -> {'''
new2 = '''                is SocketEvent.GroupRemoved -> {
                    // selfLeave case yahan kabhi nahi aata -- khud-leave turant
                    // deleteChatsLocally se list se hi hat jaata hai (GroupInfoScreen).
                    // Yahan sirf admin/owner-triggered removal handle hota hai.
                    if (!event.selfLeave) {
                        db.conversationDao().markRemoved(
                            event.roomId,
                            event.removedByUsername ?: "Admin"
                        )
                    }
                }
                is SocketEvent.RequestAccepted -> {'''
assert old2 in content, "RequestAccepted marker not found"
content = content.replace(old2, new2, 1)

# --- 3. row subtitle: isRemoved case override karo ---
old3 = '''            Text(
                if (isTyping) "typing..." else conv.lastMessage.ifBlank { "Say hi! 👋" },
                color = if (isTyping) DarkAccent else if (hasUnread) Color.White else Color(0xFF888888),
                fontWeight = if (isTyping || hasUnread) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )'''
new3 = '''            Text(
                when {
                    conv.isRemoved -> "You were removed from this group by @${conv.removedByUsername ?: "Admin"}"
                    isTyping -> "typing..."
                    else -> conv.lastMessage.ifBlank { "Say hi! 👋" }
                },
                color = when {
                    conv.isRemoved -> Color(0xFFFF6B6B)
                    isTyping -> DarkAccent
                    hasUnread -> Color.White
                    else -> Color(0xFF888888)
                },
                fontWeight = if (isTyping || hasUnread) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )'''
assert old3 in content, "row subtitle block not found"
content = content.replace(old3, new3, 1)

with open(path, "w") as f:
    f.write(content)
print("ConversationListScreen.kt patched: removed-banner subtitle + live socket handling")
