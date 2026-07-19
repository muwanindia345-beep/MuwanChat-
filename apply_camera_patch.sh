#!/data/data/com.termux/files/usr/bin/bash
# MuwanChat — Camera capture patch
# Run from the repo root (the folder containing app/)
set -e

APP="app/src/main/java/com/muwan/muwanchat"

if [ ! -d "$APP" ]; then
  echo "❌ Run this script from the MuwanChat repo root (folder that contains 'app/')."
  exit 1
fi

python3 - << 'PYEOF'
import re, sys

def patch(path, replacements, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    for old, new in replacements:
        count = content.count(old)
        if count != 1:
            print(f"❌ {label}: expected 1 match, found {count} — aborting. File untouched beyond earlier successful edits.")
            print(f"   Looking for:\n{old[:200]}")
            sys.exit(1)
        content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ {label}")

APP = "app/src/main/java/com/muwan/muwanchat"

# 1. AndroidManifest.xml — CAMERA permission + optional feature
patch(
    "app/src/main/AndroidManifest.xml",
    [(
        '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n',
        '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n'
        '    <uses-permission android:name="android.permission.CAMERA" />\n'
        '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n'
    )],
    "AndroidManifest.xml (CAMERA permission)"
)

# 2. file_paths.xml — cache path for camera captures
patch(
    "app/src/main/res/xml/file_paths.xml",
    [(
        '    <cache-path name="chat_documents" path="documents/" />\n',
        '    <cache-path name="chat_documents" path="documents/" />\n'
        '    <cache-path name="camera_images" path="camera/" />\n'
    )],
    "file_paths.xml (camera cache path)"
)

# 3. MediaPickerSheet.kt — add Camera option
media_picker_path = f"{APP}/screens/MediaPickerSheet.kt"
patch(
    media_picker_path,
    [
        (
            'import androidx.compose.material.icons.filled.Description\n'
            'import androidx.compose.material.icons.filled.Image\n'
            'import androidx.compose.material.icons.filled.MusicNote\n'
            'import androidx.compose.material.icons.filled.Videocam\n',
            'import androidx.compose.material.icons.filled.CameraAlt\n'
            'import androidx.compose.material.icons.filled.Description\n'
            'import androidx.compose.material.icons.filled.Image\n'
            'import androidx.compose.material.icons.filled.MusicNote\n'
            'import androidx.compose.material.icons.filled.Videocam\n'
        ),
        (
            '    onSelectPhoto: () -> Unit,\n'
            '    onSelectVideo: () -> Unit,\n'
            '    onSelectDocument: () -> Unit,\n'
            '    onSelectMusic: () -> Unit\n'
            ') {\n'
            '    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {\n'
            '        Column(modifier = Modifier.padding(bottom = 24.dp)) {\n'
            '            MediaPickerOption(Icons.Filled.Image, "Photo") { onSelectPhoto(); onDismiss() }\n',
            '    onSelectPhoto: () -> Unit,\n'
            '    onSelectVideo: () -> Unit,\n'
            '    onSelectDocument: () -> Unit,\n'
            '    onSelectMusic: () -> Unit,\n'
            '    onSelectCamera: () -> Unit\n'
            ') {\n'
            '    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {\n'
            '        Column(modifier = Modifier.padding(bottom = 24.dp)) {\n'
            '            MediaPickerOption(Icons.Filled.CameraAlt, "Camera") { onSelectCamera(); onDismiss() }\n'
            '            MediaPickerOption(Icons.Filled.Image, "Photo") { onSelectPhoto(); onDismiss() }\n'
        ),
    ],
    "MediaPickerSheet.kt (Camera option added)"
)

# 4. ChatScreen.kt — 1:1 chat
chat_path = f"{APP}/screens/ChatScreen.kt"
patch(
    chat_path,
    [
        (
            'import androidx.core.content.ContextCompat\n',
            'import androidx.core.content.ContextCompat\n'
            'import androidx.core.content.FileProvider\n'
        ),
        (
            'import java.io.ByteArrayOutputStream\n'
            'import java.util.*\n',
            'import java.io.ByteArrayOutputStream\n'
            'import java.io.File\n'
            'import java.util.*\n'
        ),
        (
            '// ─── Helpers: file info + image compression (upload se pehle) ─────────────\n'
            'private fun getFileName(context: android.content.Context, uri: Uri): String {\n',
            '// ─── Helpers: file info + image compression (upload se pehle) ─────────────\n'
            'private fun createCameraCaptureUri(context: android.content.Context): Uri {\n'
            '    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)\n'
            '}\n'
            '\n'
            'private fun getFileName(context: android.content.Context, uri: Uri): String {\n'
        ),
        (
            '    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->\n'
            '        uri?.let { scope.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db, displayType = "music") {} } }\n'
            '    }\n',
            '    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->\n'
            '        uri?.let { scope.launch { uploadMediaMessage(context, it, "audio", myToken, roomId, myUid, receiverUid, receiverUsername, db, displayType = "music") {} } }\n'
            '    }\n'
            '\n'
            '    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }\n'
            '    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->\n'
            '        if (success) {\n'
            '            cameraImageUri?.let { uri -> scope.launch { uploadMediaMessage(context, uri, "image", myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }\n'
            '        }\n'
            '    }\n'
            '    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->\n'
            '        if (granted) {\n'
            '            val uri = createCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        }\n'
            '    }\n'
            '    fun launchCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n'
        ),
        (
            '    if (showMediaSheet) {\n'
            '        MediaPickerSheet(\n'
            '            onDismiss = { showMediaSheet = false },\n'
            '            onSelectPhoto = { photoPicker.launch("image/*") },\n'
            '            onSelectVideo = { videoPicker.launch("video/*") },\n'
            '            onSelectDocument = { docPicker.launch(arrayOf("*/*")) },\n'
            '            onSelectMusic = { musicPicker.launch("audio/*") }\n'
            '        )\n'
            '    }\n',
            '    if (showMediaSheet) {\n'
            '        MediaPickerSheet(\n'
            '            onDismiss = { showMediaSheet = false },\n'
            '            onSelectPhoto = { photoPicker.launch("image/*") },\n'
            '            onSelectVideo = { videoPicker.launch("video/*") },\n'
            '            onSelectDocument = { docPicker.launch(arrayOf("*/*")) },\n'
            '            onSelectMusic = { musicPicker.launch("audio/*") },\n'
            '            onSelectCamera = { launchCamera() }\n'
            '        )\n'
            '    }\n'
        ),
    ],
    "ChatScreen.kt (camera wiring)"
)

# 5. GroupChatScreen.kt — group chat
group_path = f"{APP}/screens/GroupChatScreen.kt"
patch(
    group_path,
    [
        (
            'import androidx.core.content.ContextCompat\n',
            'import androidx.core.content.ContextCompat\n'
            'import androidx.core.content.FileProvider\n'
        ),
        (
            'import java.io.ByteArrayOutputStream\n'
            'import java.util.*\n',
            'import java.io.ByteArrayOutputStream\n'
            'import java.io.File\n'
            'import java.util.*\n'
        ),
        (
            '    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->\n'
            '        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db, displayType = "music") {} } }\n'
            '    }\n',
            '    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->\n'
            '        uri?.let { scope.launch { uploadGroupMediaMessage(context, it, "audio", myToken, groupId, myUid, groupId, groupName, db, displayType = "music") {} } }\n'
            '    }\n'
            '\n'
            '    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }\n'
            '    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->\n'
            '        if (success) {\n'
            '            cameraImageUri?.let { uri -> scope.launch { uploadGroupMediaMessage(context, uri, "image", myToken, groupId, myUid, groupId, groupName, db) {} } }\n'
            '        }\n'
            '    }\n'
            '    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->\n'
            '        if (granted) {\n'
            '            val uri = createGroupCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        }\n'
            '    }\n'
            '    fun launchCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createGroupCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n'
        ),
        (
            '    if (showMediaSheet) {\n'
            '        MediaPickerSheet(\n'
            '            onDismiss = { showMediaSheet = false },\n'
            '            onSelectPhoto = { photoPicker.launch("image/*") },\n'
            '            onSelectVideo = { videoPicker.launch("video/*") },\n'
            '            onSelectDocument = { docPicker.launch(arrayOf("*/*")) },\n'
            '            onSelectMusic = { musicPicker.launch("audio/*") }\n'
            '        )\n'
            '    }\n',
            '    if (showMediaSheet) {\n'
            '        MediaPickerSheet(\n'
            '            onDismiss = { showMediaSheet = false },\n'
            '            onSelectPhoto = { photoPicker.launch("image/*") },\n'
            '            onSelectVideo = { videoPicker.launch("video/*") },\n'
            '            onSelectDocument = { docPicker.launch(arrayOf("*/*")) },\n'
            '            onSelectMusic = { musicPicker.launch("audio/*") },\n'
            '            onSelectCamera = { launchCamera() }\n'
            '        )\n'
            '    }\n'
        ),
    ],
    "GroupChatScreen.kt (camera wiring)"
)

# helper fun used by GroupChatScreen (separate name to avoid duplicate top-level clash if both files ever merged)
patch(
    group_path,
    [(
        '// ─── Helpers: file info + image compression (ChatScreen.kt se duplicate —\n'
        '// wo private hain us file mein, yahan se access nahi ho sakte) ─────────────\n'
        'private fun groupChatGetFileName(context: android.content.Context, uri: Uri): String {\n',
        '// ─── Helpers: file info + image compression (ChatScreen.kt se duplicate —\n'
        '// wo private hain us file mein, yahan se access nahi ho sakte) ─────────────\n'
        'private fun createGroupCameraCaptureUri(context: android.content.Context): Uri {\n'
        '    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
        '    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")\n'
        '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)\n'
        '}\n'
        '\n'
        'private fun groupChatGetFileName(context: android.content.Context, uri: Uri): String {\n'
    )],
    "GroupChatScreen.kt (camera capture uri helper)"
)

print("\n🎉 Camera patch applied successfully to all 5 files.")
print("Ab build karo: ./gradlew assembleDebug (ya jo bhi tumhara CI trigger hai)")
PYEOF
