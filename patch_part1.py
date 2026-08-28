def patch(path, old, new, label):
    with open(path, encoding='utf-8') as f:
        s = f.read()
    if new in s and old not in s:
        print(f"[skip] {label} (already applied)")
        return
    assert old in s, f"[FAIL] pattern not found for: {label} in {path}"
    s = s.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print(f"[ok] {label}")

BASE = "app/src/main/java/com/muwan/muwanchat"

patch(f"{BASE}/network/ChatApi.kt",
    "    val reactions: List<MessageReaction>? = null,\n"
    "    val link_preview: LinkPreview? = null,\n"
    "    val is_forwarded: Boolean = false\n"
    ")",
    "    val reactions: List<MessageReaction>? = null,\n"
    "    val link_preview: LinkPreview? = null,\n"
    "    val is_forwarded: Boolean = false,\n"
    "    val mentions: List<String>? = null\n"
    ")",
    "MessageItem.mentions field")

patch(f"{BASE}/screens/ChatMessage.kt",
    "    val previewUrl: String? = null,\n"
    "    val isForwarded: Boolean = false\n"
    ")",
    "    val previewUrl: String? = null,\n"
    "    val isForwarded: Boolean = false,\n"
    "    val mentions: List<String> = emptyList()\n"
    ")",
    "ChatMessage.mentions field")

patch(f"{BASE}/screens/ChatMessage.kt",
    "    previewUrl = link_preview?.url,\n"
    "    isForwarded = is_forwarded\n"
    ")",
    "    previewUrl = link_preview?.url,\n"
    "    isForwarded = is_forwarded,\n"
    "    mentions = mentions ?: emptyList()\n"
    ")",
    "MessageItem.toChatMessage mentions mapping")

patch(f"{BASE}/screens/ChatMessage.kt",
    "    previewUrl = previewUrl,\n"
    "    isForwarded = isForwarded\n"
    ")",
    "    previewUrl = previewUrl,\n"
    "    isForwarded = isForwarded,\n"
    "    mentions = mentions?.split(\",\")?.filter { it.isNotBlank() } ?: emptyList()\n"
    ")",
    "MessageEntity.toChatMessage mentions mapping")

patch(f"{BASE}/data/MessageEntity.kt",
    "    val previewUrl: String? = null\n"
    ")",
    "    val previewUrl: String? = null,\n"
    "    val mentions: String? = null   // comma-separated uids jinhe is message mein mention kiya gaya\n"
    ")",
    "MessageEntity.mentions column")

patch(f"{BASE}/data/MuwanChatDb.kt",
    "    version = 17,",
    "    version = 18,",
    "MuwanChatDb version bump 17->18")

patch(f"{BASE}/data/ChatRepository.kt",
    "        replyToId: String? = null,\n"
    "        isForwarded: Boolean = false\n"
    "    ) {\n"
    "        db.messageDao().insert(\n"
    "            MessageEntity(\n"
    "                id = id,\n"
    "                roomId = roomId,\n"
    "                senderUid = senderUid,\n"
    "                receiverUid = receiverUid,\n"
    "                content = content,\n"
    "                type = type,\n"
    "                seen = 0,\n"
    "                createdAt = createdAt,\n"
    "                status = status,\n"
    "                fileName = fileName,\n"
    "                mimeType = mimeType,\n"
    "                replyToId = replyToId,\n"
    "                isForwarded = isForwarded\n"
    "            )\n"
    "        )",
    "        replyToId: String? = null,\n"
    "        isForwarded: Boolean = false,\n"
    "        mentions: List<String> = emptyList()\n"
    "    ) {\n"
    "        db.messageDao().insert(\n"
    "            MessageEntity(\n"
    "                id = id,\n"
    "                roomId = roomId,\n"
    "                senderUid = senderUid,\n"
    "                receiverUid = receiverUid,\n"
    "                content = content,\n"
    "                type = type,\n"
    "                seen = 0,\n"
    "                createdAt = createdAt,\n"
    "                status = status,\n"
    "                fileName = fileName,\n"
    "                mimeType = mimeType,\n"
    "                replyToId = replyToId,\n"
    "                isForwarded = isForwarded,\n"
    "                mentions = if (mentions.isNotEmpty()) mentions.joinToString(\",\") else null\n"
    "            )\n"
    "        )",
    "ChatRepository.recordMessage mentions param")

print("Part 1 done.")
