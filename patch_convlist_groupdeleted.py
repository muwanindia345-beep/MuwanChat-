path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path) as f:
    content = f.read()

changes = 0
def rep(old, new, label):
    global content, changes
    if old in content:
        content = content.replace(old, new, 1); changes += 1
    else:
        print(f"WARN: {label} anchor not found")

rep(
    '''                    if (!event.selfLeave) {
                        db.conversationDao().markRemoved(
                            event.roomId,
                            event.removedByUsername ?: "Admin"
                        )
                    }''',
    '''                    if (!event.selfLeave) {
                        db.conversationDao().markRemoved(
                            event.roomId,
                            if (event.groupDeleted) "GROUP_DELETED" else (event.removedByUsername ?: "Admin")
                        )
                    }''',
    "markRemoved call"
)

rep(
    '''                    conv.isRemoved -> "You were removed from this group by @${conv.removedByUsername ?: "Admin"}"''',
    '''                    conv.isRemoved && conv.removedByUsername == "GROUP_DELETED" -> "This group was deleted by the owner"
                    conv.isRemoved -> "You were removed from this group by @${conv.removedByUsername ?: "Admin"}"''',
    "list preview text"
)

with open(path, "w") as f:
    f.write(content)
print(f"ConversationListScreen.kt changes: {changes}")
