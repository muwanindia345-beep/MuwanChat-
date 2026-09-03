def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

# ---------------------------------------------------------------------------
# 1) ChatApi.kt — MessageItem mein seen_by add (group history-sync ke liye)
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt",
'''    val reactions: List<MessageReaction>? = null,
    val link_preview: LinkPreview? = null,
    val is_forwarded: Boolean = false
)''',
'''    val reactions: List<MessageReaction>? = null,
    val link_preview: LinkPreview? = null,
    val is_forwarded: Boolean = false,
    // Group messages ke liye: kaun-kaun se members ne yeh dekh liya hai.
    // Tick green tabhi jab isme SABHI group members ka uid ho.
    val seen_by: List<String>? = null
)''',
    "ChatApi.kt: MessageItem.seen_by"
)

# ---------------------------------------------------------------------------
# 2) AppSocketManager.kt — MessagesSeen data class + socket.on parsing
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt",
'''    data class MessagesSeen(val roomId: String, val seenBy: String) : SocketEvent()''',
'''    data class MessagesSeen(
        val roomId: String,
        val seenBy: String,
        val fullySeenIds: List<String> = emptyList()
    ) : SocketEvent()''',
    "AppSocketManager.kt: MessagesSeen data class"
)

apply(
    "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt",
'''            s.on("messages_seen") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.MessagesSeen(json.optString("room_id"), json.optString("seen_by"))
                )
            }''',
'''            s.on("messages_seen") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                // Group chats mein backend "fully_seen_ids" bhejta hai — sirf
                // woh messages jinhe SABHI group members ne dekh liya hai.
                // 1-1 chat mein yeh array nahi aata (khali rahega, koi farak
                // nahi padta kyunki ChatScreen isko use hi nahi karta).
                val fullySeenArr = json.optJSONArray("fully_seen_ids")
                val fullySeenIds = if (fullySeenArr != null) {
                    (0 until fullySeenArr.length()).map { fullySeenArr.getString(it) }
                } else emptyList()
                _events.tryEmit(
                    SocketEvent.MessagesSeen(
                        json.optString("room_id"),
                        json.optString("seen_by"),
                        fullySeenIds
                    )
                )
            }''',
    "AppSocketManager.kt: messages_seen socket parsing"
)

# ---------------------------------------------------------------------------
# 3) MessageDao.kt — naya markSeenByIds query (specific IDs, one-way ratchet)
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt",
'''    // Doosre banda ne dekh liya to hamare bheje saare messages SEEN ho jaate hai
    @Query("UPDATE messages SET status = 'SEEN' WHERE roomId = :roomId AND senderUid = :myUid AND status != 'SEEN'")
    suspend fun markMySentAsSeen(roomId: String, myUid: String)''',
'''    // Doosre banda ne dekh liya to hamare bheje saare messages SEEN ho jaate hai
    // (1-1 chat ke liye hi use hota hai — wahan "kisi ne dekha" == "sole other
    // member ne dekha" == sabne dekha, isliye blanket update sahi hai)
    @Query("UPDATE messages SET status = 'SEEN' WHERE roomId = :roomId AND senderUid = :myUid AND status != 'SEEN'")
    suspend fun markMySentAsSeen(roomId: String, myUid: String)

    // Group chat ke liye: sirf woh specific messages SEEN karo jinhe backend
    // ne "fully_seen_ids" mein bola (matlab group ke SABHI members ne dekh
    // liya). `status != 'SEEN'` guard se yeh one-way hai — ek baar green hua
    // toh yeh query dobara touch hi nahi karegi, wapas grey kabhi nahi hoga.
    @Query("UPDATE messages SET status = 'SEEN' WHERE id IN (:ids) AND status != 'SEEN'")
    suspend fun markSeenByIds(ids: List<String>)''',
    "MessageDao.kt: markSeenByIds"
)

# ---------------------------------------------------------------------------
# 4) ChatRepository.kt — syncMessages ko groupMemberCount param do
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt",
'''    suspend fun syncMessages(db: MuwanChatDb, items: List<MessageItem>) {
        // "Delete for me" (single message) ka local record — jab tak hai,
        // backend se dobara aane pe bhi is message ko wapas insert mat karo
        val deletedIds = db.deletedMessageDao().getAllIds().toSet()
        val entities = items.filter { it.id !in deletedIds }.map {
            MessageEntity(
                id = it.id,
                roomId = it.room_id,
                senderUid = it.sender_uid,
                receiverUid = it.receiver_uid,
                content = it.content,
                type = it.type,
                seen = it.seen,
                createdAt = it.created_at,
                status = "SENT",''',
'''    // groupMemberCount: sirf group rooms ke liye pass karo (1-1 chat ke liye
    // null/default rehne do). Isse fresh open / app reinstall ke baad bhi
    // pehle se "sabne dekh liya" wale messages turant green dikhte hain,
    // real-time "messages_seen" event ka wait nahi karna padta.
    suspend fun syncMessages(db: MuwanChatDb, items: List<MessageItem>, groupMemberCount: Int? = null) {
        // "Delete for me" (single message) ka local record — jab tak hai,
        // backend se dobara aane pe bhi is message ko wapas insert mat karo
        val deletedIds = db.deletedMessageDao().getAllIds().toSet()
        val entities = items.filter { it.id !in deletedIds }.map {
            val computedStatus = if (groupMemberCount != null && groupMemberCount > 0) {
                if ((it.seen_by?.size ?: 1) >= groupMemberCount) "SEEN" else "SENT"
            } else "SENT"
            MessageEntity(
                id = it.id,
                roomId = it.room_id,
                senderUid = it.sender_uid,
                receiverUid = it.receiver_uid,
                content = it.content,
                type = it.type,
                seen = it.seen,
                createdAt = it.created_at,
                status = computedStatus,''',
    "ChatRepository.kt: syncMessages groupMemberCount param"
)

# ---------------------------------------------------------------------------
# 5) GroupChatScreen.kt — call site + real-time event handler
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
'''            val res = RetrofitClient.chatApi.getMessages("Bearer $token", groupId)
            if (res.isSuccessful) {
                ChatRepository.syncMessages(db, res.body()?.messages ?: emptyList())
            }''',
'''            val res = RetrofitClient.chatApi.getMessages("Bearer $token", groupId)
            if (res.isSuccessful) {
                ChatRepository.syncMessages(db, res.body()?.messages ?: emptyList(), group?.members?.size)
            }''',
    "GroupChatScreen.kt: syncMessages call site"
)

apply(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
'''                is SocketEvent.MessagesSeen -> {
                    if (event.roomId == groupId) {
                        db.messageDao().markMySentAsSeen(groupId, myUid)
                    }
                }''',
'''                is SocketEvent.MessagesSeen -> {
                    // Group mein blanket "ek ne dekha = sab green" nahi chalega.
                    // Backend sirf un ids ko fully_seen_ids mein bhejta hai jinhe
                    // group ke SABHI members ne dekh liya ho — sirf unhi ko yahan
                    // SEEN karo, baaki grey hi rahenge jab tak baaki log na dekhein.
                    if (event.roomId == groupId && event.fullySeenIds.isNotEmpty()) {
                        db.messageDao().markSeenByIds(event.fullySeenIds)
                    }
                }''',
    "GroupChatScreen.kt: MessagesSeen event handler"
)

print("\n[DONE] Group 'all seen' tick fix applied across 5 Android files")
