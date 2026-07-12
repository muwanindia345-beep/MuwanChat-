import re

path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_sig = """    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit,
    skipCompression: Boolean = false
) {
    setUploading(true)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {"""

new_sig = """    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {"""

if old_sig not in content:
    raise SystemExit("old_sig not found - file may already be patched or differs")
content = content.replace(old_sig, new_sig)

old_call = 'uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, { uploadingMedia = it }, skipCompression = true)'
new_call = 'uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, skipCompression = true, setUploading = { uploadingMedia = it })'

if old_call not in content:
    raise SystemExit("old_call not found - file may already be patched or differs")
content = content.replace(old_call, new_call)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("Patched successfully:", path)
