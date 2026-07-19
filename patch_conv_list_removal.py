path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path) as f:
    content = f.read()

changes = 0

old = '''                is SocketEvent.GroupRemoved -> {
                    // selfLeave case yahan kabhi nahi aata -- khud-leave turant
                    // deleteChatsLocally se list se hi hat jaata hai (GroupInfoScreen).
                    // Yahan sirf admin/owner-triggered removal handle hota hai.
                    if (!event.selfLeave) {
                        db.conversationDao().markRemoved(
                            event.roomId,
                            event.removedByUsername ?: "Admin"
                        )
                    }
                }'''
new = '''                is SocketEvent.GroupRemoved -> {
                    // selfLeave case yahan kabhi nahi aata -- khud-leave turant
                    // deleteChatsLocally se list se hi hat jaata hai (GroupInfoScreen).
                    // Yahan sirf admin/owner-triggered removal handle hota hai.
                    if (!event.selfLeave) {
                        db.conversationDao().markRemoved(
                            event.roomId,
                            event.removedByUsername ?: "Admin"
                        )
                    }
                }
                is SocketEvent.ConnectionRemoved -> {
                    // Dusre user ne humein "Accepted Users" se permanently remove
                    // kiya -- humari taraf se bhi conversation + poori chat history
                    // turant local se saaf, list se live disappear ho jaayegi.
                    val roomId = listOf(myUid, event.uid).sorted().joinToString("_")
                    ChatRepository.deleteChatsLocally(db, setOf(roomId))
                }'''
if old in content:
    content = content.replace(old, new, 1)
    changes += 1
else:
    print("WARN: GroupRemoved handler anchor not found")

with open(path, "w") as f:
    f.write(content)
print(f"ConversationListScreen.kt changes: {changes}")
