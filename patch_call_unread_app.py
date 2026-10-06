#!/usr/bin/env python3
# Missed call = unread +1 + bold (app) -- Project root se run karo (jahan app/ folder hai).
import sys, os

BASE = "app/src/main/java/com/muwan/muwanchat/"
MARK = "CALL_UNREAD_PATCH"

def patch(rel, pairs, base=BASE):
    path = base + rel
    if not os.path.exists(path):
        sys.exit("ERROR: %s nahi mila -- project root se run karo" % path)
    s = open(path, encoding="utf-8").read()
    if MARK in s:
        print("SKIP  %s (already patched)" % rel)
        return
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            sys.exit("ERROR: %s mein anchor %d baar mila (1 chahiye):\n%s" % (rel, n, old[:140]))
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)
    print("OK    %s" % rel)

patch("data/ConversationDao.kt", [
("""    @Query("UPDATE conversations SET unreadCount = 0 WHERE roomId = :roomId")
    suspend fun clearUnread(roomId: String)
""",
"""    @Query("UPDATE conversations SET unreadCount = 0 WHERE roomId = :roomId")
    suspend fun clearUnread(roomId: String)

    // CALL_UNREAD_PATCH: missed call ke liye unread +1
    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE roomId = :roomId")
    suspend fun incrementUnread(roomId: String)
"""),
])

patch("data/ChatRepository.kt", [
("                    unreadCount = if (senderUid != myUid) 1 else 0\n",
 "                    unreadCount = if (senderUid != myUid && type != \"call\") 1 else 0 // CALL_UNREAD_PATCH\n"),
("            db.conversationDao().updateLastMessage(roomId, previewText, createdAt, senderUid, myUid)\n",
"""            if (type == "call") {
                // CALL_UNREAD_PATCH: ringing call unread nahi badhati -- sirf missed badhata hai (NavGraph, CallMessageUpdate)
                db.conversationDao().syncLastMessagePreview(roomId, previewText, createdAt, senderUid)
            } else {
                db.conversationDao().updateLastMessage(roomId, previewText, createdAt, senderUid, myUid)
            }
"""),
])

patch("navigation/NavGraph.kt", [
("                        com.muwan.muwanchat.data.ChatRepository.refreshLastMessagePreview(db, event.roomId)\n",
"""                        // CALL_UNREAD_PATCH: missed call (sirf callee ko, chat khula ho to nahi) unread +1.
                        // Count server se bhi aata hai (sync) -- wahan overwrite hota hai, jodta nahi, to double count nahi.
                        val newStatus = try { org.json.JSONObject(event.content).optString("status") } catch (_: Exception) { "" }
                        val oldStatus = try { org.json.JSONObject(old?.content ?: "").optString("status") } catch (_: Exception) { "" }
                        if (newStatus == "missed" && oldStatus != "missed" && event.receiverUid == myUid) {
                            val entry = navController.currentBackStackEntry
                            val openRoom = if (entry?.destination?.route == Screen.Chat.route) {
                                entry?.arguments?.getString("roomId")
                            } else null
                            if (openRoom != event.roomId) db.conversationDao().incrementUnread(event.roomId)
                        }
                        com.muwan.muwanchat.data.ChatRepository.refreshLastMessagePreview(db, event.roomId)
"""),
])

print("Done. Push app + Actions beta build.")
