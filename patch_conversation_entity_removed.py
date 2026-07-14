path1 = "app/src/main/java/com/muwan/muwanchat/data/ConversationEntity.kt"
with open(path1) as f:
    c1 = f.read()

old1 = '''    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0
)'''
new1 = '''    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    // Admin/owner ne remove kiya to true — chat history read-only rehti hai,
    // sirf input bar area ek banner se replace hota hai (khud-leave case me
    // ye kabhi set nahi hota, wo already deleteChatsLocally se turant hat jaata hai)
    val isRemoved: Boolean = false,
    val removedByUsername: String? = null
)'''
assert old1 in c1
c1 = c1.replace(old1, new1, 1)
with open(path1, "w") as f:
    f.write(c1)
print("ConversationEntity.kt patched")

path2 = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
with open(path2) as f:
    c2 = f.read()

old2 = "    version = 13,"
new2 = "    version = 14,"
assert old2 in c2
c2 = c2.replace(old2, new2, 1)
with open(path2, "w") as f:
    f.write(c2)
print("MuwanChatDb.kt patched: version 13 -> 14 (fallbackToDestructiveMigration already handles it)")
