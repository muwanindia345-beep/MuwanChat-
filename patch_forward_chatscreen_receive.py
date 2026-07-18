import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''                            fileName = event.fileName,
                            mimeType = event.mimeType,
                            replyToId = event.replyToId
                        )
                        if (event.senderUid != myUid) {
                            isReceiverTyping = false'''
new = '''                            fileName = event.fileName,
                            mimeType = event.mimeType,
                            replyToId = event.replyToId,
                            isForwarded = event.isForwarded
                        )
                        if (event.senderUid != myUid) {
                            isReceiverTyping = false'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatScreen.kt patched: incoming isForwarded flag ab record hota hai")
