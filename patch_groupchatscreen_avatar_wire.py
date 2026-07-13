import io
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }"""
new1 = """    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberAvatars by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }"""

old2 = """        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberCount = g.members.size
            }
        } catch (_: Exception) {}"""
new2 = """        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
            }
        } catch (_: Exception) {}"""

old3 = """                    MessageBubble(
                        message = msg,
                        myUid = myUid,
                        onReactionLongPress = { id, emoji -> sendReaction(id, emoji) },"""
new3 = """                    MessageBubble(
                        message = msg,
                        myUid = myUid,
                        senderAvatar = memberAvatars[msg.senderUid],
                        senderName = if (msg.senderUid != myUid) memberNames[msg.senderUid] else null,
                        onReactionLongPress = { id, emoji -> sendReaction(id, emoji) },"""

patches = [(old1, new1, "memberAvatars state"), (old2, new2, "populate memberAvatars"), (old3, new3, "wire into MessageBubble call")]
for old, new, label in patches:
    count = content.count(old)
    if count != 1:
        print(f"MATCH FAILED ({label}): found {count}, expected 1")
    else:
        content = content.replace(old, new)
        print(f"Patched: {label}")

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Done -> GroupChatScreen.kt")
