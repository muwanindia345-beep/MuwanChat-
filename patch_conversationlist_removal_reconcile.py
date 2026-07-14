path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path) as f:
    content = f.read()

old = '''    suspend fun reloadConversations(token: String) {
        try {
            val res = RetrofitClient.chatApi.getConversations("Bearer $token")
            if (res.isSuccessful) {
                ChatRepository.syncConversations(db, res.body()?.conversations ?: emptyList())
            }
        } catch (_: Exception) {}
    }'''

new = '''    suspend fun reloadConversations(token: String) {
        try {
            val res = RetrofitClient.chatApi.getConversations("Bearer $token")
            if (res.isSuccessful) {
                val serverItems = res.body()?.conversations ?: emptyList()
                ChatRepository.syncConversations(db, serverItems)

                // Offline hote waqt jo "removed" socket event miss ho gaya tha,
                // usko yahan REST se catch-up karte hain -- server response me
                // jo local group conversation missing hai (aur already
                // isRemoved mark nahi hai), uska removal-status check karo.
                val serverGroupIds = serverItems.filter { it.isGroup }.map { it.room_id }.toSet()
                val localGroups = db.conversationDao().getAll().filter { it.isGroup }
                for (local in localGroups) {
                    if (local.roomId in serverGroupIds || local.isRemoved) continue
                    try {
                        val statusRes = RetrofitClient.chatApi.getRemovalStatus("Bearer $token", local.roomId)
                        val status = statusRes.body()
                        if (statusRes.isSuccessful && status?.removed == true) {
                            db.conversationDao().markRemoved(local.roomId, status.removedByUsername ?: "Admin")
                        } else {
                            // Koi removal record nahi mila -- ya to khud-leave tha
                            // (jo already delete ho chuka hoga) ya group hi delete
                            // ho gaya, dono case me local stale entry hata do.
                            db.conversationDao().deleteByRoom(local.roomId)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }'''

assert old in content, "reloadConversations block not found"
content = content.replace(old, new, 1)

with open(path, "w") as f:
    f.write(content)
print("ConversationListScreen.kt patched: offline-missed removal reconciliation added")
