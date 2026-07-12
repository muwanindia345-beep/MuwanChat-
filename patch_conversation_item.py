import io
path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """    val unreadCount: Int = 0,
    val lastSenderUid: String = "",
    val isTyping: Boolean = false
)"""
new = """    val unreadCount: Int = 0,
    val lastSenderUid: String = "",
    val isTyping: Boolean = false,
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0
)"""

assert content.count(old) == 1, "match failed"
content = content.replace(old, new)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched ConversationItem in ChatApi.kt")
