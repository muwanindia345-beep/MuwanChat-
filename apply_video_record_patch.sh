#!/data/data/com.termux/files/usr/bin/bash
# MuwanChat — "Record Video" (camera) patch
# ⚠️ Run this AFTER apply_camera_patch.sh (needs onSelectCamera already wired)
set -e

APP="app/src/main/java/com/muwan/muwanchat"

if [ ! -d "$APP" ]; then
  echo "❌ Run this script from the MuwanChat repo root (folder that contains 'app/')."
  exit 1
fi

python3 - << 'PYEOF'
import sys

def patch(path, replacements, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    for old, new in replacements:
        count = content.count(old)
        if count != 1:
            print(f"❌ {label}: expected 1 match, found {count} — aborting.")
            print(f"   Looking for:\n{old[:200]}")
            sys.exit(1)
        content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ {label}")

APP = "app/src/main/java/com/muwan/muwanchat"

# 1. MediaPickerSheet.kt — add "Record Video" option
media_picker_path = f"{APP}/screens/MediaPickerSheet.kt"
patch(
    media_picker_path,
    [
        (
            'import androidx.compose.material.icons.filled.Description\n',
            'import androidx.compose.material.icons.filled.Description\n'
            'import androidx.compose.material.icons.filled.FiberManualRecord\n'
        ),
        (
            '    onSelectMusic: () -> Unit,\n'
            '    onSelectCamera: () -> Unit\n'
            ') {\n'
            '    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {\n'
            '        Column(modifier = Modifier.padding(bottom = 24.dp)) {\n'
            '            MediaPickerOption(Icons.Filled.CameraAlt, "Camera") { onSelectCamera(); onDismiss() }\n',
            '    onSelectMusic: () -> Unit,\n'
            '    onSelectCamera: () -> Unit,\n'
            '    onSelectRecordVideo: () -> Unit\n'
            ') {\n'
            '    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {\n'
            '        Column(modifier = Modifier.padding(bottom = 24.dp)) {\n'
            '            MediaPickerOption(Icons.Filled.CameraAlt, "Camera") { onSelectCamera(); onDismiss() }\n'
            '            MediaPickerOption(Icons.Filled.FiberManualRecord, "Record Video") { onSelectRecordVideo(); onDismiss() }\n'
        ),
    ],
    "MediaPickerSheet.kt (Record Video option added)"
)

# 2. ChatScreen.kt
chat_path = f"{APP}/screens/ChatScreen.kt"
patch(
    chat_path,
    [
        (
            'private fun createCameraCaptureUri(context: android.content.Context): Uri {\n'
            '    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)\n'
            '}\n',
            'private fun createCameraCaptureUri(context: android.content.Context): Uri {\n'
            '    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)\n'
            '}\n'
            '\n'
            'private fun createCameraVideoCaptureUri(context: android.content.Context): Uri {\n'
            '    val videosDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val videoFile = File(videosDir, "VID_${System.currentTimeMillis()}.mp4")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)\n'
            '}\n'
        ),
        (
            '    fun launchCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n',
            '    fun launchCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n'
            '\n'
            '    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }\n'
            '    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->\n'
            '        if (success) {\n'
            '            cameraVideoUri?.let { uri -> scope.launch { uploadVideoMessage(context, uri, myToken, roomId, myUid, receiverUid, receiverUsername, db) {} } }\n'
            '        }\n'
            '    }\n'
            '    val cameraVideoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->\n'
            '        if (granted) {\n'
            '            val uri = createCameraVideoCaptureUri(context)\n'
            '            cameraVideoUri = uri\n'
            '            cameraVideoLauncher.launch(uri)\n'
            '        }\n'
            '    }\n'
            '    fun launchVideoCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createCameraVideoCaptureUri(context)\n'
            '            cameraVideoUri = uri\n'
            '            cameraVideoLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraVideoPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n'
        ),
        (
            '            onSelectCamera = { launchCamera() }\n'
            '        )\n'
            '    }\n'
            '\n'
            '    if (showVoiceRecorder) {\n',
            '            onSelectCamera = { launchCamera() },\n'
            '            onSelectRecordVideo = { launchVideoCamera() }\n'
            '        )\n'
            '    }\n'
            '\n'
            '    if (showVoiceRecorder) {\n'
        ),
    ],
    "ChatScreen.kt (record-video wiring)"
)

# 3. GroupChatScreen.kt
group_path = f"{APP}/screens/GroupChatScreen.kt"
patch(
    group_path,
    [
        (
            'private fun createGroupCameraCaptureUri(context: android.content.Context): Uri {\n'
            '    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)\n'
            '}\n',
            'private fun createGroupCameraCaptureUri(context: android.content.Context): Uri {\n'
            '    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)\n'
            '}\n'
            '\n'
            'private fun createGroupCameraVideoCaptureUri(context: android.content.Context): Uri {\n'
            '    val videosDir = File(context.cacheDir, "camera").apply { mkdirs() }\n'
            '    val videoFile = File(videosDir, "VID_${System.currentTimeMillis()}.mp4")\n'
            '    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)\n'
            '}\n'
        ),
        (
            '    fun launchCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createGroupCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n',
            '    fun launchCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createGroupCameraCaptureUri(context)\n'
            '            cameraImageUri = uri\n'
            '            cameraLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n'
            '\n'
            '    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }\n'
            '    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->\n'
            '        if (success) {\n'
            '            cameraVideoUri?.let { uri -> scope.launch { uploadGroupVideoMessage(context, uri, myToken, groupId, myUid, groupId, groupName, db) {} } }\n'
            '        }\n'
            '    }\n'
            '    val cameraVideoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->\n'
            '        if (granted) {\n'
            '            val uri = createGroupCameraVideoCaptureUri(context)\n'
            '            cameraVideoUri = uri\n'
            '            cameraVideoLauncher.launch(uri)\n'
            '        }\n'
            '    }\n'
            '    fun launchVideoCamera() {\n'
            '        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {\n'
            '            val uri = createGroupCameraVideoCaptureUri(context)\n'
            '            cameraVideoUri = uri\n'
            '            cameraVideoLauncher.launch(uri)\n'
            '        } else {\n'
            '            cameraVideoPermissionLauncher.launch(Manifest.permission.CAMERA)\n'
            '        }\n'
            '    }\n'
        ),
        (
            '            onSelectCamera = { launchCamera() }\n'
            '        )\n'
            '    }\n'
            '\n'
            '    if (showVoiceRecorder) {\n',
            '            onSelectCamera = { launchCamera() },\n'
            '            onSelectRecordVideo = { launchVideoCamera() }\n'
            '        )\n'
            '    }\n'
            '\n'
            '    if (showVoiceRecorder) {\n'
        ),
    ],
    "GroupChatScreen.kt (record-video wiring)"
)

print("\n🎉 Record Video patch applied successfully.")
print("Ab build karo: ./gradlew assembleDebug")
PYEOF
