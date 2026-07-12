import re, sys

path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

orig = content

# 1. onGifReceived — "gif" -> "image", skipCompression=true
old1 = '''            onGifReceived = { uri, _, release ->
                scope.launch {
                    uploadMediaMessage(context, uri, "gif", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it }
                    release()
                }
            },'''
new1 = '''            onGifReceived = { uri, _, release ->
                scope.launch {
                    // Backend sirf "image" | "document" category accept karta hai — "gif" bhejne se
                    // type silently "text" pe fallback ho jata tha aur chat me raw URL dikhta tha.
                    // "image" bhejo, MessageBubble already isko AsyncImage se render karta hai.
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db, { uploadingMedia = it }, skipCompression = true)
                    release()
                }
            },'''

if old1 in content:
    content = content.replace(old1, new1)
    print("[OK] onGifReceived block patched")
else:
    print("[SKIP] onGifReceived block not found (already patched or changed)")

# 2. uploadMediaMessage signature — add skipCompression param
old2 = '''private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit
) {
    setUploading(true)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image") compressImageToBase64(context, uri)
            else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP) to (context.contentResolver.getType(uri) ?: "application/octet-stream")
            }
        }'''
new2 = '''private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    setUploading: (Boolean) -> Unit,
    skipCompression: Boolean = false
) {
    setUploading(true)
    try {
        val (base64Data, mime) = withContext(Dispatchers.IO) {
            if (category == "image" && !skipCompression) compressImageToBase64(context, uri)
            else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP) to (context.contentResolver.getType(uri) ?: "application/octet-stream")
            }
        }'''

if old2 in content:
    content = content.replace(old2, new2)
    print("[OK] uploadMediaMessage signature patched")
else:
    print("[SKIP] uploadMediaMessage signature not found (already patched or changed)")

if content == orig:
    print("\\n[WARN] Nothing changed — file content doesn't match expected patterns. Check manually.")
    sys.exit(1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("\\nDone. Patched:", path)
