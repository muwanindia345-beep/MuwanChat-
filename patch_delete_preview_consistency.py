import os, sys

def find_file(name):
    for root, dirs, files in os.walk("."):
        if name in files:
            return os.path.join(root, name)
    return None

changed_files = []

# ---------- 1. MessageDao.kt: naya query — deleted messages skip karke asli latest survivor dhoondo ----------
path = find_file("MessageDao.kt")
if not path:
    print("[-] MessageDao.kt nahi mili!")
    sys.exit(1)

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_dao = """    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(roomId: String): MessageEntity?"""

new_dao = """    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(roomId: String): MessageEntity?

    // Preview ke liye: deleted (tombstone) messages ko skip karke sabse
    // recent zinda message dhoondta hai — taaki delete karne par preview
    // apne aap peeche wale message par cascade ho jaaye.
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND deleted = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestNonDeletedMessage(roomId: String): MessageEntity?"""

if old_dao in content and "getLatestNonDeletedMessage" not in content:
    content = content.replace(old_dao, new_dao, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] MessageDao.kt: naya query add hua")
    changed_files.append(path)
elif "getLatestNonDeletedMessage" in content:
    print("[*] MessageDao.kt: already patched, skip.")
else:
    print("[-] MessageDao.kt: anchor nahi mila — manual check karo.")

# ---------- 2. ChatRepository.kt: refreshLastMessagePreview ab cascade karega ----------
path = find_file("ChatRepository.kt")
if not path:
    print("[-] ChatRepository.kt nahi mili!")
    sys.exit(1)

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_repo = """    suspend fun refreshLastMessagePreview(db: MuwanChatDb, roomId: String) {
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
    }"""

new_repo = """    suspend fun refreshLastMessagePreview(db: MuwanChatDb, roomId: String) {
        // Deleted (tombstone) messages ko ignore karke sabse recent zinda
        // message dhoondo — delete karne par preview apne aap peeche wale
        // par cascade hoga. Koi bhi zinda message na bache to blank karo,
        // jisse conversation list me default "Say hi! 👋" dikhega.
        val latest = db.messageDao().getLatestNonDeletedMessage(roomId)
        if (latest == null) {
            db.conversationDao().syncLastMessagePreview(roomId, "", nowIso(), "")
            return
        }
        val previewText = when (latest.type) {
            "text" -> latest.content
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "audio" -> "🎤 Voice message"
            "music" -> "🎵 ${latest.fileName ?: "Music"}"
            "document" -> "📄 ${latest.fileName ?: "Document"}"
            else -> latest.content
        }
        db.conversationDao().syncLastMessagePreview(roomId, previewText, latest.createdAt, latest.senderUid)
    }"""

count = content.count(old_repo)
if count == 1:
    content = content.replace(old_repo, new_repo)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] ChatRepository.kt: cascade logic apply hua")
    changed_files.append(path)
elif "getLatestNonDeletedMessage(roomId)" in content:
    print("[*] ChatRepository.kt: already patched, skip.")
else:
    print(f"[-] ChatRepository.kt: pattern {count} baar mila, manual check karo.")

# ---------- 3. ConversationListScreen.kt: home screen par bhi delete turant reflect ho ----------
path = find_file("ConversationListScreen.kt")
if not path:
    print("[-] ConversationListScreen.kt nahi mili!")
    sys.exit(1)

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

anchor = "                is SocketEvent.NewRequest -> {"
insertion = """                is SocketEvent.MessageDeleted -> {
                    // Home screen par baithe ho tab bhi delete turant preview
                    // par reflect ho (chat ke andar jaane ki zarurat nahi) —
                    // pichla zinda message dikhega, kuch na bacha to "Say hi! 👋"
                    scope.launch {
                        db.messageDao().markDeleted(event.id)
                        ChatRepository.refreshLastMessagePreview(db, event.roomId)
                    }
                }
"""

count = content.count(anchor)
if count == 1 and "is SocketEvent.MessageDeleted ->" not in content:
    content = content.replace(anchor, insertion + anchor, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] ConversationListScreen.kt: MessageDeleted listener add hua")
    changed_files.append(path)
elif "is SocketEvent.MessageDeleted ->" in content:
    print("[*] ConversationListScreen.kt: already patched, skip.")
else:
    print(f"[-] ConversationListScreen.kt: anchor {count} baar mila, manual check karo.")

print(f"\n[+] Total {len(changed_files)}/3 file(s) update hui.")
