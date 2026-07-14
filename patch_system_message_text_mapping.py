path = "app/src/main/java/com/muwan/muwanchat/screens/ChatMessage.kt"
with open(path) as f:
    content = f.read()

old1 = '''fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = if (type == "text") content else "",
    sent = sender_uid == myUid,
    time = formatMessageTime(created_at),
    type = type,
    mediaUrl = if (type != "text") content else null,'''

new1 = '''fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = if (type == "text" || type == "system") content else "",
    sent = sender_uid == myUid,
    time = formatMessageTime(created_at),
    type = type,
    mediaUrl = if (type != "text" && type != "system") content else null,'''

assert old1 in content, "MessageItem.toChatMessage marker not found"
content = content.replace(old1, new1, 1)

old2 = '''fun MessageEntity.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = if (type == "text") content else "",
    sent = senderUid == myUid,
    time = formatMessageTime(createdAt),
    type = type,
    mediaUrl = if (type != "text") content else null,'''

new2 = '''fun MessageEntity.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = if (type == "text" || type == "system") content else "",
    sent = senderUid == myUid,
    time = formatMessageTime(createdAt),
    type = type,
    mediaUrl = if (type != "text" && type != "system") content else null,'''

assert old2 in content, "MessageEntity.toChatMessage marker not found"
content = content.replace(old2, new2, 1)

with open(path, "w") as f:
    f.write(content)

print("ChatMessage.kt patched: system messages ab text field me content dikhayenge, mediaUrl me nahi")
