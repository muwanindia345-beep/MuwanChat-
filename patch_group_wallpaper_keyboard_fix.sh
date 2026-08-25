#!/data/data/com.termux/files/usr/bin/bash
# patch_group_wallpaper_keyboard_fix.sh
# Fixes: Group chat wallpaper shrinking/resizing when keyboard opens.
# Root cause: wallpaper was drawn inside the same Column that has
# .imePadding(), so it shrank along with the keyboard-adjusted layout.
# Fix: hoist wallpaper out to a sibling Box (same pattern as 1-on-1 ChatScreen),
# so it stays full-size and fixed regardless of keyboard state.
# No functional change — messages, input bar, header all work the same.
# Run from project root (MuwanChat--main folder):
#   bash patch_group_wallpaper_keyboard_fix.sh

set -e

GROUP_FILE="app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

if [ ! -f "$GROUP_FILE" ]; then
    echo "ERROR: $GROUP_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

if grep -q "WallpaperPreviewBackground(currentWallpaper)" "$GROUP_FILE" && grep -qF 'Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
    WallpaperPreviewBackground(currentWallpaper)' "$GROUP_FILE" 2>/dev/null; then
    echo "SKIP: GroupChatScreen.kt already patched"
    exit 0
fi

python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Already patched check
if "WallpaperPreviewBackground(currentWallpaper)\n    Column(" in content:
    print("SKIP: already patched")
else:
    # 1. Replace the outer Column opening with Box + inner Column
    old_open = """Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {"""
    new_open = """Box(
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
    ) {"""
    if old_open not in content:
        print("WARN: outer Column anchor not found — file already patched or changed manually?")
    else:
        content = content.replace(old_open, new_open, 1)

        # 2. Remove the now-duplicate WallpaperPreviewBackground call inside the message list Box
        old_msg = """        Box(modifier = Modifier.weight(1f)) {
            WallpaperPreviewBackground(currentWallpaper)
            LazyColumn("""
        new_msg = """        Box(modifier = Modifier.weight(1f)) {
            LazyColumn("""
        if old_msg in content:
            content = content.replace(old_msg, new_msg, 1)
        else:
            print("WARN: duplicate wallpaper call not found in message list — may already be removed")

        # 3. Add the extra closing brace for the new outer Box, right after the
        #    existing Column's closing brace (identified by the ChatInputBar block
        #    ending followed by blank line + "if (showMediaSheet) {")
        old_close = """        }
    }

    if (showMediaSheet) {"""
        new_close = """        }
    }
    }

    if (showMediaSheet) {"""
        if old_close in content:
            content = content.replace(old_close, new_close, 1)
        else:
            print("WARN: closing-brace anchor not found — check GroupChatScreen.kt manually for brace balance")

        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Patched: GroupChatScreen.kt -> wallpaper no longer shrinks with keyboard")
PYEOF

echo "Done. Verifying brace balance..."
python3 - << 'PYEOF'
content = open("app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt").read()
o, c = content.count("{"), content.count("}")
print(f"open braces: {o}, close braces: {c}")
if o != c:
    print("WARNING: braces are NOT balanced — please check the file manually before building!")
else:
    print("OK: braces balanced.")
PYEOF
