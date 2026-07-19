path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path) as f:
    content = f.read()

changes = 0

old = '''        scope.launch { db.messageDao().updateReactions(messageId, Gson().toJson(optimistic)) }

        scope.launch {
            try {
                val res = RetrofitClient.chatApi.reactToMessage(
                    "Bearer $myToken", roomId, messageId, ReactRequest(emoji)
                )'''
new = '''        scope.launch { db.messageDao().updateReactions(messageId, Gson().toJson(optimistic)) }
        if (!hadSameEmoji) {
            scope.launch {
                db.conversationDao().updateLastMessage(
                    roomId = roomId,
                    lastMessage = "You reacted $emoji",
                    lastTime = nowIso(),
                    lastSenderUid = myUid,
                    myUid = myUid
                )
            }
        }

        scope.launch {
            try {
                val res = RetrofitClient.chatApi.reactToMessage(
                    "Bearer $myToken", roomId, messageId, ReactRequest(emoji)
                )'''
if old in content:
    content = content.replace(old, new, 1)
    changes += 1
else:
    print("WARN: sendReaction anchor not found")

old2 = '''                is SocketEvent.ReactionUpdate -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().updateReactions(event.id, event.reactionsJson) }
                    }
                }'''
new2 = '''                is SocketEvent.ReactionUpdate -> {
                    if (event.roomId == roomId) {
                        scope.launch { db.messageDao().updateReactions(event.id, event.reactionsJson) }
                        if (event.added && event.emoji.isNotBlank()) {
                            scope.launch {
                                db.conversationDao().updateLastMessage(
                                    roomId = roomId,
                                    lastMessage = "$receiverUsername reacted ${event.emoji}",
                                    lastTime = nowIso(),
                                    lastSenderUid = receiverUid,
                                    myUid = myUid
                                )
                            }
                        }
                    }
                }'''
if old2 in content:
    content = content.replace(old2, new2, 1)
    changes += 1
else:
    print("WARN: ReactionUpdate handler anchor not found")

with open(path, "w") as f:
    f.write(content)
print(f"ChatScreen.kt changes: {changes}")
