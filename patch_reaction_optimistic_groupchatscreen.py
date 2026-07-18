import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = '''    fun sendReaction(messageId: String, emoji: String) {
        scope.launch {
            try {
                val res = RetrofitClient.chatApi.reactToMessage(
                    "Bearer $myToken", groupId, messageId, ReactRequest(emoji)
                )
                res.body()?.reactions?.let { reactions ->
                    db.messageDao().updateReactions(messageId, Gson().toJson(reactions))
                }
            } catch (_: Exception) {}
        }
    }'''
new = '''    val reactionInFlight = remember { mutableStateOf(setOf<String>()) }

    fun sendReaction(messageId: String, emoji: String) {
        if (reactionInFlight.value.contains(messageId)) return
        reactionInFlight.value = reactionInFlight.value + messageId

        val current = messages.firstOrNull { it.id == messageId }?.reactions ?: emptyList()
        val hadSameEmoji = current.any { it.emoji == emoji && it.userIds.contains(myUid) }
        val optimistic = current
            .map { com.muwan.muwanchat.network.MessageReaction(it.emoji, it.userIds.filter { u -> u != myUid }) }
            .filter { it.userIds.isNotEmpty() }
            .toMutableList()
        if (!hadSameEmoji) {
            val groupIdx = optimistic.indexOfFirst { it.emoji == emoji }
            if (groupIdx >= 0) {
                optimistic[groupIdx] = com.muwan.muwanchat.network.MessageReaction(emoji, optimistic[groupIdx].userIds + myUid)
            } else {
                optimistic.add(com.muwan.muwanchat.network.MessageReaction(emoji, listOf(myUid)))
            }
        }
        scope.launch { db.messageDao().updateReactions(messageId, Gson().toJson(optimistic)) }

        scope.launch {
            try {
                val res = RetrofitClient.chatApi.reactToMessage(
                    "Bearer $myToken", groupId, messageId, ReactRequest(emoji)
                )
                res.body()?.reactions?.let { reactions ->
                    db.messageDao().updateReactions(messageId, Gson().toJson(reactions))
                }
            } catch (_: Exception) {
            } finally {
                reactionInFlight.value = reactionInFlight.value - messageId
            }
        }
    }'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("GroupChatScreen.kt patched: reaction ab optimistic hai, double-tap se ulta nahi hoga")
