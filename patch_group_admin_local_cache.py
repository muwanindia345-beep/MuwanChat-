f = "app/src/main/java/com/muwan/muwanchat/data/ConversationEntity.kt"
s = open(f).read()
old = '''    val isRemoved: Boolean = false,
    val removedByUsername: String? = null
)'''
new = '''    val isRemoved: Boolean = false,
    val removedByUsername: String? = null,
    // Group ki admin-only-send setting ka local cache — taaki screen open hote
    // hi (network wait kiye bina) sahi input bar / banner turant dikh jaaye.
    val onlyAdminsCanSend: Boolean = false,
    val amIAdmin: Boolean = false
)'''
assert old in s, "ConversationEntity.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ ConversationEntity.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
s = open(f).read()
old = '''    @Query("DELETE FROM conversations")
    suspend fun clearAll()'''
new = '''    @Query("UPDATE conversations SET onlyAdminsCanSend = :onlyAdminsCanSend, amIAdmin = :amIAdmin WHERE roomId = :roomId")
    suspend fun updateAdminSettings(roomId: String, onlyAdminsCanSend: Boolean, amIAdmin: Boolean)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()'''
assert old in s, "ConversationDao.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ ConversationDao.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
s = open(f).read()
old = "    version = 17,"
new = "    version = 18,"
assert old in s, "MuwanChatDb.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ MuwanChatDb.kt patched (version 17 → 18)")

f = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
s = open(f).read()

old_1 = '''    var group by remember { mutableStateOf<GroupData?>(null) }
    var showAdminsSheet by remember { mutableStateOf(false) }
    val isAdmin = group?.admins?.contains(myUid) == true'''
new_1 = '''    var group by remember { mutableStateOf<GroupData?>(null) }
    var showAdminsSheet by remember { mutableStateOf(false) }
    val onlyAdminsCanSend = group?.onlyAdminsCanSend ?: conversationEntity?.onlyAdminsCanSend ?: false
    val isAdmin = group?.admins?.contains(myUid) ?: conversationEntity?.amIAdmin ?: false'''
assert old_1 in s, "GroupChatScreen.kt: pattern1 not found"
s = s.replace(old_1, new_1, 1)

old_2 = '''            res.body()?.group?.let { g ->
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
            }'''
new_2 = '''            res.body()?.group?.let { g ->
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
                db.conversationDao().updateAdminSettings(groupId, g.onlyAdminsCanSend, g.admins.contains(myUid))
            }'''
assert old_2 in s, "GroupChatScreen.kt: pattern2 not found"
s = s.replace(old_2, new_2, 1)

old_3 = '''        } else if (group?.onlyAdminsCanSend == true && !isAdmin) {'''
new_3 = '''        } else if (onlyAdminsCanSend && !isAdmin) {'''
assert old_3 in s, "GroupChatScreen.kt: pattern3 not found"
s = s.replace(old_3, new_3, 1)

open(f, "w").write(s)
print("✅ GroupChatScreen.kt patched")
