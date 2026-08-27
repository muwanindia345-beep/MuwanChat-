#!/data/data/com.termux/files/usr/bin/bash
# patch_conversation_preview_sync.sh
# Fixes: deleting a message (for me / for everyone / via socket from the
# other person) did NOT update the conversation list's preview text —
# it kept showing the old deleted message instead of the new latest one.
# Now, after any delete, the preview is recalculated from whatever message
# actually remains (or "This message was deleted" if the latest one was
# an everyone-delete tombstone, or blank -> "Say hi! 👋" if nothing is left).
# unreadCount is never touched by this — only lastMessage/lastTime/lastSenderUid.
# Run from project root (MuwanChat--main folder):
#   bash patch_conversation_preview_sync.sh

set -e

MSG_DAO="app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt"
CONV_DAO="app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
REPO="app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
CHAT_SCREEN="app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
GROUP_SCREEN="app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

for f in "$MSG_DAO" "$CONV_DAO" "$REPO" "$CHAT_SCREEN" "$GROUP_SCREEN"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

python3 - << 'PYEOF'
def patch(path, replacements, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for old, new in replacements:
        if new in content:
            print(f"SKIP ({label}): already patched")
            continue
        if old in content:
            content = content.replace(old, new, 1)
            changed = True
        else:
            print(f"WARN ({label}): anchor not found — already patched or changed manually?")
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")

# ───────────────────────── MessageDao.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt",
    [
        (
            '''    @Query("DELETE FROM messages")
    suspend fun clearAll()''',
            '''    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(roomId: String): MessageEntity?

    @Query("DELETE FROM messages")
    suspend fun clearAll()'''
        ),
    ],
    "MessageDao.kt"
)

# ───────────────────────── ConversationDao.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt",
    [
        (
            '''    @Query("""
        UPDATE conversations
        SET lastMessage = :lastMessage, lastTime = :lastTime, lastSenderUid = :lastSenderUid,
            unreadCount = CASE WHEN :lastSenderUid != :myUid THEN unreadCount + 1 ELSE unreadCount END
        WHERE roomId = :roomId
    """)
    suspend fun updateLastMessage(roomId: String, lastMessage: String, lastTime: String, lastSenderUid: String, myUid: String)''',
            '''    @Query("""
        UPDATE conversations
        SET lastMessage = :lastMessage, lastTime = :lastTime, lastSenderUid = :lastSenderUid,
            unreadCount = CASE WHEN :lastSenderUid != :myUid THEN unreadCount + 1 ELSE unreadCount END
        WHERE roomId = :roomId
    """)
    suspend fun updateLastMessage(roomId: String, lastMessage: String, lastTime: String, lastSenderUid: String, myUid: String)

    // Message delete/edit hone ke baad preview text re-sync karne ke liye —
    // unreadCount ko bilkul touch nahi karta (naya message nahi hai, sirf existing
    // wale ka preview update ho raha hai)
    @Query("""
        UPDATE conversations
        SET lastMessage = :lastMessage, lastTime = :lastTime, lastSenderUid = :lastSenderUid
        WHERE roomId = :roomId
    """)
    suspend fun syncLastMessagePreview(roomId: String, lastMessage: String, lastTime: String, lastSenderUid: String)'''
        ),
    ],
    "ConversationDao.kt"
)

# ───────────────────────── ChatRepository.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt",
    [
        (
            "    suspend fun addConversationPlaceholder(",
            '''    // Delete (for me / for everyone / socket se aaya delete event) ke baad
    // conversation list ka preview text recalculate karta hai — jo bhi
    // room ka SABSE NAYA message ho (deleted rows bhi count hoti hai,
    // kyunki "delete for everyone" wale rows tombstone ban ke DB me rehte hai),
    // usi ke hisaab se "lastMessage" set hota hai. Koi message hi na bacha ho
    // to blank kar deta hai (list me phir default "Say hi! 👋" dikhega).
    // unreadCount ko yeh function kabhi touch nahi karta.
    suspend fun refreshLastMessagePreview(db: MuwanChatDb, roomId: String) {
        val latest = db.messageDao().getLatestMessage(roomId)
        if (latest == null) {
            db.conversationDao().syncLastMessagePreview(roomId, "", nowIso(), "")
            return
        }
        val previewText = when {
            latest.deleted -> "This message was deleted"
            latest.type == "text" -> latest.content
            latest.type == "image" -> "📷 Photo"
            latest.type == "video" -> "🎥 Video"
            latest.type == "audio" -> "🎤 Voice message"
            latest.type == "music" -> "🎵 ${latest.fileName ?: "Music"}"
            latest.type == "document" -> "📄 ${latest.fileName ?: "Document"}"
            else -> latest.content
        }
        db.conversationDao().syncLastMessagePreview(roomId, previewText, latest.createdAt, latest.senderUid)
    }

    suspend fun addConversationPlaceholder('''
        ),
    ],
    "ChatRepository.kt"
)

# ───────────────────────── ChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt",
    [
        (
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            // Record karo taaki agla sync backend se inhe wapas na le aaye
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
        }
        exitSelectionMode()
    }

    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)
                } catch (_: Exception) {
                    // Backend call fail ho jaaye (jaise no internet) to bhi apni screen se hata dete hain;
                    // dusre user tak socket event backend se hi jaayega jab connection wapas aayega.
                }
                db.messageDao().markDeleted(id)
            }
        }
        exitSelectionMode()
    }''',
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            // Record karo taaki agla sync backend se inhe wapas na le aaye
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
            ChatRepository.refreshLastMessagePreview(db, roomId)
        }
        exitSelectionMode()
    }

    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)
                } catch (_: Exception) {
                    // Backend call fail ho jaaye (jaise no internet) to bhi apni screen se hata dete hain;
                    // dusre user tak socket event backend se hi jaayega jab connection wapas aayega.
                }
                db.messageDao().markDeleted(id)
            }
            ChatRepository.refreshLastMessagePreview(db, roomId)
        }
        exitSelectionMode()
    }'''
        ),
        (
            '''                is SocketEvent.MessageDeleted -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().markDeleted(event.id) }
                    }
                }''',
            '''                is SocketEvent.MessageDeleted -> {
                    if (event.roomId == roomId) {
                        scope.launch {
                            db.messageDao().markDeleted(event.id)
                            ChatRepository.refreshLastMessagePreview(db, roomId)
                        }
                    }
                }'''
        ),
    ],
    "ChatScreen.kt"
)

# ───────────────────────── GroupChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
    [
        (
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
        }
        exitSelectionMode()
    }

    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", groupId, id)
                } catch (_: Exception) {}
                db.messageDao().markDeleted(id)
            }
        }
        exitSelectionMode()
    }''',
            '''    fun deleteSelectedForMe() {
        val ids = selectedMessageIds.toList()
        val now = nowIso()
        scope.launch {
            db.messageDao().deleteByIds(ids)
            db.deletedMessageDao().markDeleted(ids.map { DeletedMessageEntity(it, now) })
            ChatRepository.refreshLastMessagePreview(db, groupId)
        }
        exitSelectionMode()
    }

    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", groupId, id)
                } catch (_: Exception) {}
                db.messageDao().markDeleted(id)
            }
            ChatRepository.refreshLastMessagePreview(db, groupId)
        }
        exitSelectionMode()
    }'''
        ),
        (
            '''                is SocketEvent.MessageDeleted -> {
                    if (event.roomId == groupId) {
                        scope.launch { db.messageDao().markDeleted(event.id) }
                    }
                }''',
            '''                is SocketEvent.MessageDeleted -> {
                    if (event.roomId == groupId) {
                        scope.launch {
                            db.messageDao().markDeleted(event.id)
                            ChatRepository.refreshLastMessagePreview(db, groupId)
                        }
                    }
                }'''
        ),
    ],
    "GroupChatScreen.kt"
)
PYEOF

echo ""
echo "Verifying brace/paren balance..."
for f in "$MSG_DAO" "$CONV_DAO" "$REPO" "$CHAT_SCREEN" "$GROUP_SCREEN"; do
    python3 -c "
content = open('$f').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$f -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"
done
