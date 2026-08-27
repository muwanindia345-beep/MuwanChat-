import os, sys

def find_file(name):
    for root, dirs, files in os.walk("."):
        if name in files:
            return os.path.join(root, name)
    return None

path = find_file("ConversationListScreen.kt")
if not path:
    print("[-] ConversationListScreen.kt nahi mili!")
    sys.exit(1)

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = """                        ChatRepository.recordMessage(
                            db = db,
                            id = event.id,
                            roomId = event.roomId,
                            senderUid = event.senderUid,
                            receiverUid = myUid,
                            content = event.content,
                            type = "text",
                            createdAt = event.createdAt.ifBlank { nowIso() },
                            myUid = myUid
                        )"""

new = """                        ChatRepository.recordMessage(
                            db = db,
                            id = event.id,
                            roomId = event.roomId,
                            senderUid = event.senderUid,
                            receiverUid = myUid,
                            content = event.content,
                            type = event.type,
                            createdAt = event.createdAt.ifBlank { nowIso() },
                            myUid = myUid,
                            fileName = event.fileName,
                            mimeType = event.mimeType,
                            replyToId = event.replyToId,
                            isForwarded = event.isForwarded
                        )"""

count = content.count(old)
if count == 1:
    content = content.replace(old, new)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] Fix applied: {path}")
    print("[+] Ab conversation list preview sahi type (Photo/Video/Voice/etc) dikhayega")
elif count == 0:
    print("[*] Pattern nahi mila — shayad already patched, skip.")
else:
    print(f"[-] Pattern {count} baar mila, ambiguous — manual check karo.")
