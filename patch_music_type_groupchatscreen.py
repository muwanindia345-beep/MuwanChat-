import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_sig = '''private suspend fun uploadGroupMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    setUploading: (Boolean) -> Unit
) {'''
new_sig = '''private suspend fun uploadGroupMediaMessage(
    context: android.content.Context,
    uri: Uri,
    category: String,
    token: String,
    roomId: String,
    myUid: String,
    groupId: String,
    groupName: String,
    db: MuwanChatDb,
    skipCompression: Boolean = false,
    displayType: String = category,
    setUploading: (Boolean) -> Unit
) {'''
if old_sig not in content:
    print("Signature anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)

old_fail = '''            content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
            otherUsername = groupName, status = "FAILED",'''
new_fail = '''            content = uri.toString(), type = displayType, createdAt = nowIso(), myUid = myUid,
            otherUsername = groupName, status = "FAILED",'''
if old_fail not in content:
    print("FAILED record anchor not found!"); sys.exit(1)
content = content.replace(old_fail, new_fail, 1)

old_uploading = '''        content = uri.toString(), type = category, createdAt = nowIso(), myUid = myUid,
        otherUsername = groupName, status = "UPLOADING",'''
new_uploading = '''        content = uri.toString(), type = displayType, createdAt = nowIso(), myUid = myUid,
        otherUsername = groupName, status = "UPLOADING",'''
if old_uploading not in content:
    print("UPLOADING record anchor not found!"); sys.exit(1)
content = content.replace(old_uploading, new_uploading, 1)

old_socket = '''                    AppSocketManager.sendGroupMessage(id, roomId, body.url, category, body.file_name ?: filename, body.mime_type ?: mime) { success ->'''
new_socket = '''                    AppSocketManager.sendGroupMessage(id, roomId, body.url, displayType, body.file_name ?: filename, body.mime_type ?: mime) { success ->'''
if old_socket not in content:
    print("Socket send anchor not found!"); sys.exit(1)
content = content.replace(old_socket, new_socket, 1)

old_music = '''    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db) {} } }
    }'''
new_music = '''    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db, displayType = "music") {} } }
    }'''
if old_music not in content:
    print("musicPicker anchor not found!"); sys.exit(1)
content = content.replace(old_music, new_music, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("GroupChatScreen.kt patched: music ab type='music' se bhejega, voice se alag pehchana jayega")
