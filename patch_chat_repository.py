import io
path = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """    suspend fun addConversationPlaceholder(
        db: MuwanChatDb,
        roomId: String,
        uid: String,
        username: String,
        avatar: String?
    ) {
        val existing = db.conversationDao().getByRoomId(roomId)
        if (existing == null) {
            db.conversationDao().upsertOne(
                ConversationEntity(
                    roomId = roomId,
                    uid = uid,
                    username = username,
                    avatar = avatar,
                    lastMessage = "",
                    lastTime = nowIso(),
                    lastSenderUid = "",
                    unreadCount = 0
                )
            )
        }
    }"""
new1 = """    suspend fun addConversationPlaceholder(
        db: MuwanChatDb,
        roomId: String,
        uid: String,
        username: String,
        avatar: String?,
        isGroup: Boolean = false,
        memberCount: Int = 0
    ) {
        val existing = db.conversationDao().getByRoomId(roomId)
        if (existing == null) {
            db.conversationDao().upsertOne(
                ConversationEntity(
                    roomId = roomId,
                    uid = uid,
                    username = username,
                    avatar = avatar,
                    lastMessage = "",
                    lastTime = nowIso(),
                    lastSenderUid = "",
                    unreadCount = 0,
                    isGroup = isGroup,
                    memberCount = memberCount,
                    onlineCount = 0
                )
            )
        }
    }"""

old2 = """            if (hidden == null) {
                toUpsert.add(
                    ConversationEntity(
                        roomId = it.room_id,
                        uid = it.uid,
                        username = it.username,
                        avatar = it.avatar,
                        lastMessage = it.lastMessage,
                        lastTime = it.lastTime,
                        lastSenderUid = it.lastSenderUid,
                        unreadCount = it.unreadCount
                    )
                )
            } else if (it.lastTime > hidden.hiddenAt) {
                // Naya message aa chuka hai delete ke baad — WhatsApp jaisa, chat wapas dikhao
                db.hiddenConversationDao().unhide(it.room_id)
                toUpsert.add(
                    ConversationEntity(
                        roomId = it.room_id,
                        uid = it.uid,
                        username = it.username,
                        avatar = it.avatar,
                        lastMessage = it.lastMessage,
                        lastTime = it.lastTime,
                        lastSenderUid = it.lastSenderUid,
                        unreadCount = it.unreadCount
                    )
                )
            }"""
new2 = """            if (hidden == null) {
                toUpsert.add(
                    ConversationEntity(
                        roomId = it.room_id,
                        uid = it.uid,
                        username = it.username,
                        avatar = it.avatar,
                        lastMessage = it.lastMessage,
                        lastTime = it.lastTime,
                        lastSenderUid = it.lastSenderUid,
                        unreadCount = it.unreadCount,
                        isGroup = it.isGroup,
                        memberCount = it.memberCount,
                        onlineCount = it.onlineCount
                    )
                )
            } else if (it.lastTime > hidden.hiddenAt) {
                // Naya message aa chuka hai delete ke baad — WhatsApp jaisa, chat wapas dikhao
                db.hiddenConversationDao().unhide(it.room_id)
                toUpsert.add(
                    ConversationEntity(
                        roomId = it.room_id,
                        uid = it.uid,
                        username = it.username,
                        avatar = it.avatar,
                        lastMessage = it.lastMessage,
                        lastTime = it.lastTime,
                        lastSenderUid = it.lastSenderUid,
                        unreadCount = it.unreadCount,
                        isGroup = it.isGroup,
                        memberCount = it.memberCount,
                        onlineCount = it.onlineCount
                    )
                )
            }"""

for old, new, label in [(old1, new1, "addConversationPlaceholder"), (old2, new2, "syncConversations")]:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched ChatRepository.kt")
