import sys
path = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old_sig = '''        status: String = "SENT",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null
    ) {
        db.messageDao().insert(
            MessageEntity(
                id = id,
                roomId = roomId,
                senderUid = senderUid,
                receiverUid = receiverUid,
                content = content,
                type = type,
                seen = 0,
                createdAt = createdAt,
                status = status,
                fileName = fileName,
                mimeType = mimeType,
                replyToId = replyToId
            )
        )'''
new_sig = '''        status: String = "SENT",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        isForwarded: Boolean = false
    ) {
        db.messageDao().insert(
            MessageEntity(
                id = id,
                roomId = roomId,
                senderUid = senderUid,
                receiverUid = receiverUid,
                content = content,
                type = type,
                seen = 0,
                createdAt = createdAt,
                status = status,
                fileName = fileName,
                mimeType = mimeType,
                replyToId = replyToId,
                isForwarded = isForwarded
            )
        )'''
if old_sig not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatRepository.kt patched: recordMessage ab isForwarded accept karta hai")
