import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_pickers = '''    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadVideoMessage(context, it, myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "document", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }'''
new_pickers = '''    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it }
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadVideoMessage(context, it, myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "document", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db) { uploadingMedia = it } } }
    }'''
if old_pickers not in content:
    print("Pickers anchor not found!"); sys.exit(1)
content = content.replace(old_pickers, new_pickers, 1)

old_sheet = '''    if (showMediaSheet) {
        MediaPickerSheet(
            onDismiss = { showMediaSheet = false },
            onSelectPhoto = { photoPicker.launch("image/*") },
            onSelectVideo = { videoPicker.launch("video/*") },
            onSelectDocument = { docPicker.launch(arrayOf("*/*")) }
        )
    }'''
new_sheet = '''    if (showMediaSheet) {
        MediaPickerSheet(
            onDismiss = { showMediaSheet = false },
            onSelectPhoto = { photoPicker.launch("image/*") },
            onSelectVideo = { videoPicker.launch("video/*") },
            onSelectDocument = { docPicker.launch(arrayOf("*/*")) },
            onSelectMusic = { musicPicker.launch("audio/*") }
        )
    }'''
if old_sheet not in content:
    print("MediaPickerSheet call anchor not found!"); sys.exit(1)
content = content.replace(old_sheet, new_sheet, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatScreen.kt patched: multi-image select + music picker wired")
