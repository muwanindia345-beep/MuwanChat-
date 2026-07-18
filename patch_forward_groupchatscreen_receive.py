import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''                            fileName = event.fileName,
                            mimeType = event.mimeType,
                            replyToId = event.replyToId
                        )
                        if (event.senderUid != myUid) {
                            try {
                                RetrofitClient.chatApi.markSeen("Bearer $myToken", groupId)'''
new = '''                            fileName = event.fileName,
                            mimeType = event.mimeType,
                            replyToId = event.replyToId,
                            isForwarded = event.isForwarded
                        )
                        if (event.senderUid != myUid) {
                            try {
                                RetrofitClient.chatApi.markSeen("Bearer $myToken", groupId)'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("GroupChatScreen.kt patched: incoming isForwarded flag ab record hota hai")
