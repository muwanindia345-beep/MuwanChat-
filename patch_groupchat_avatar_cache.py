import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
content = open(path, encoding="utf-8").read()

# --- Edit 1: import ---
old1 = "import com.muwan.muwanchat.data.DeletedMessageEntity\n"
new1 = "import com.muwan.muwanchat.data.DeletedMessageEntity\nimport com.muwan.muwanchat.data.GroupInfoCacheEntity\n"
if old1 not in content:
    sys.exit("Anchor 1 not found -- mujhe 'grep -n \"import com.muwan.muwanchat.data.DeletedMessageEntity\" GroupChatScreen.kt' ka output bhejo.")
content = content.replace(old1, new1, 1)

# --- Edit 2: instant cache-read before network fetch, + write cache after fetch ---
old2 = '''    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token
        myUid = AuthDataStore.getUid(context).first() ?: ""

        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(groupId)

        // Group info fetch \u2014 member names (typing display ke liye) + count
        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
                db.conversationDao().updateAdminSettings(groupId, g.onlyAdminsCanSend, g.admins.contains(myUid))
            }
        } catch (_: Exception) {}'''

new2 = '''    // Local cache se turant member avatars/names dikhao (agar kabhi GroupInfo/Settings
    // khula ho) -- taaki chat screen khulte hi avatar turant dikhe, 0.1s ka flash na ho.
    LaunchedEffect(Unit) {
        if (group != null) return@LaunchedEffect
        val cached = db.groupInfoCacheDao().get(groupId)
        if (cached != null && group == null) {
            try {
                val g = Gson().fromJson(cached.json, GroupData::class.java)
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        myToken = token
        myUid = AuthDataStore.getUid(context).first() ?: ""

        AppSocketManager.connect(token)
        AppSocketManager.joinRoom(groupId)

        // Group info fetch \u2014 member names (typing display ke liye) + count
        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
                db.conversationDao().updateAdminSettings(groupId, g.onlyAdminsCanSend, g.admins.contains(myUid))
                db.groupInfoCacheDao().upsert(GroupInfoCacheEntity(groupId = groupId, json = Gson().toJson(g)))
            }
        } catch (_: Exception) {}'''

if old2 not in content:
    sys.exit("Anchor 2 not found -- mujhe 'sed -n \"715,745p\" GroupChatScreen.kt' ka output bhejo.")
content = content.replace(old2, new2, 1)

open(path, "w", encoding="utf-8").write(content)
print("GroupChatScreen.kt patched: member avatars/names ab instant cache se aate hain, delay khatam.")
