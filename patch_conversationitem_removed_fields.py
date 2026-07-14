path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path) as f:
    content = f.read()

old = '''    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0
)

data class ConversationsResponse('''
new = '''    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    val isRemoved: Boolean = false,
    val removedByUsername: String? = null
)

data class ConversationsResponse('''
assert old in content
content = content.replace(old, new, 1)
with open(path, "w") as f:
    f.write(content)
print("ChatApi.kt patched: ConversationItem me isRemoved/removedByUsername add hua")
