import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/MediaPickerSheet.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.material.icons.filled.Image"
new_import = "import androidx.compose.material.icons.filled.Image\nimport androidx.compose.material.icons.filled.MusicNote"
if old_import not in content:
    print("Icon import anchor not found!"); sys.exit(1)
content = content.replace(old_import, new_import, 1)

old_sig = '''fun MediaPickerSheet(
    onDismiss: () -> Unit,
    onSelectPhoto: () -> Unit,
    onSelectVideo: () -> Unit,
    onSelectDocument: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            MediaPickerOption(Icons.Filled.Image, "Photo") { onSelectPhoto(); onDismiss() }
            MediaPickerOption(Icons.Filled.Videocam, "Video") { onSelectVideo(); onDismiss() }
            MediaPickerOption(Icons.Filled.Description, "Document") { onSelectDocument(); onDismiss() }
        }
    }
}'''
new_sig = '''fun MediaPickerSheet(
    onDismiss: () -> Unit,
    onSelectPhoto: () -> Unit,
    onSelectVideo: () -> Unit,
    onSelectDocument: () -> Unit,
    onSelectMusic: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            MediaPickerOption(Icons.Filled.Image, "Photo") { onSelectPhoto(); onDismiss() }
            MediaPickerOption(Icons.Filled.Videocam, "Video") { onSelectVideo(); onDismiss() }
            MediaPickerOption(Icons.Filled.Description, "Document") { onSelectDocument(); onDismiss() }
            MediaPickerOption(Icons.Filled.MusicNote, "Music") { onSelectMusic(); onDismiss() }
        }
    }
}'''
if old_sig not in content:
    print("MediaPickerSheet body anchor not found!"); sys.exit(1)
content = content.replace(old_sig, new_sig, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("MediaPickerSheet.kt patched: Music option added")
