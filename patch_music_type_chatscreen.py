import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_sig = '''private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {'''
new_sig = '''private suspend fun uploadMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    receiverUid: String,
    receiverUsername: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    displayType: String = category,
    setUploading: (Boolean) -> Unit
) {'''
if old_sig not in content:
    print("Signature anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)

old_fail = '''            content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
            otherUsername = receiverUsername, status = "FAILED",'''
new_fail = '''            content = uri.toString(), type = displayType, createdAt = nowIso(), myUid = myUid,
            otherUsername = receiverUsername, status = "FAILED",'''
if old_fail not in content:
    print("FAILED record anchor not found!"); sys.exit(1)
content = content.replace(old_fail, new_fail, 1)

old_uploading = '''        content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
        otherUsername = receiverUsername, status = "UPLOADING",'''
new_uploading = '''        content = uri.toString(), type = displayType, createdAt = nowIso(), myUid = myUid,
        otherUsername = receiverUsername, status = "UPLOADING",'''
if old_uploading not in content:
    print("UPLOADING record anchor not found!"); sys.exit(1)
content = content.replace(old_uploading, new_uploading, 1)

old_socket = '''                    AppSocketManager.sendMessage(id, receiverUid, body.url, category, body.file_name ?: filename, body.mime_type ?: mime) { success ->'''
new_socket = '''                    AppSocketManager.sendMessage(id, receiverUid, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime) { success ->'''
if old_socket not in content:
    print("Socket send anchor not found!"); sys.exit(1)
content = content.replace(old_socket, new_socket, 1)

old_rest = '''                            SendMessageRequest(receiverUid, body.url, category, body.file_name ?: filename, body.mime_type ?: mime)'''
new_rest = '''                            SendMessageRequest(receiverUid, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime)'''
if old_rest not in content:
    print("REST send anchor not found!"); sys.exit(1)
content = content.replace(old_rest, new_rest, 1)

old_music = '''    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }
    }'''
new_music = '''    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db, displayType = "music") {} } }
    }'''
if old_music not in content:
    print("musicPicker anchor not found!"); sys.exit(1)
content = content.replace(old_music, new_music, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatScreen.kt patched: music ab type='music' se bhejega, voice se alag pehchana jayega")
