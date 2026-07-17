import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_root = '''    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {'''
new_root = '''    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        WallpaperPreviewBackground(currentWallpaper)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {'''
if old_root not in content:
    print("Root Column anchor not found!")
    sys.exit(1)
content = content.replace(old_root, new_root, 1)

old_inner = '''        Box(modifier = Modifier.weight(1f)) {
            WallpaperPreviewBackground(currentWallpaper)
            LazyColumn('''
new_inner = '''        Box(modifier = Modifier.weight(1f)) {
            LazyColumn('''
if old_inner not in content:
    print("Inner wallpaper render anchor not found!")
    sys.exit(1)
content = content.replace(old_inner, new_inner, 1)

old_close = '''    }

    if (showBulkDeleteConfirm) {'''
new_close = '''    }
    }

    if (showBulkDeleteConfirm) {'''
if old_close not in content:
    print("Closing brace anchor not found!")
    sys.exit(1)
content = content.replace(old_close, new_close, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("ChatScreen.kt patched: wallpaper ab keyboard ke saath shift nahi hoga")
