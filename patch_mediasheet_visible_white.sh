#!/data/data/com.termux/files/usr/bin/bash
# patch_mediasheet_visible_white.sh
# Fixes: Camera/Record Video/Photo/Video/Document/Music bottom sheet
# (shown from both 1-on-1 and group chat, since they share MediaPickerSheet.kt):
#   1. Sheet background was black -> now uses the visible DarkSheet color
#   2. Icons were orange (DarkAccent) -> now white
# Nothing else changes: labels, click actions, order all stay the same.
# Run from project root (MuwanChat--main folder):
#   bash patch_mediasheet_visible_white.sh

set -e

FILE="app/src/main/java/com/muwan/muwanchat/screens/MediaPickerSheet.kt"

if [ ! -f "$FILE" ]; then
    echo "ERROR: $FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/MediaPickerSheet.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

changed = False

# 1. Swap DarkHeader import for DarkSheet (drop DarkAccent import too, now unused)
old_imports = "import com.muwan.muwanchat.DarkAccent\nimport com.muwan.muwanchat.DarkHeader"
new_imports = "import com.muwan.muwanchat.DarkSheet"
if new_imports in content:
    print("SKIP: imports already patched")
elif old_imports in content:
    content = content.replace(old_imports, new_imports, 1)
    changed = True
else:
    print("WARN: import anchor not found — checking individual pieces...")
    # fallback: handle piecemeal in case only one of the two exists
    if "import com.muwan.muwanchat.DarkHeader" in content and "import com.muwan.muwanchat.DarkSheet" not in content:
        content = content.replace(
            "import com.muwan.muwanchat.DarkHeader",
            "import com.muwan.muwanchat.DarkSheet",
            1
        )
        changed = True
    if "import com.muwan.muwanchat.DarkAccent" in content and "tint = DarkAccent" not in content:
        content = content.replace("import com.muwan.muwanchat.DarkAccent\n", "", 1)
        changed = True

# 2. Sheet background: DarkHeader -> DarkSheet
old_bg = "ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {"
new_bg = "ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkSheet) {"
if new_bg in content:
    print("SKIP: sheet background already patched")
elif old_bg in content:
    content = content.replace(old_bg, new_bg, 1)
    changed = True
else:
    print("WARN: background anchor not found — already patched or changed manually?")

# 3. Icon tint: DarkAccent (orange) -> Color.White
old_icon = "tint = DarkAccent, modifier = Modifier.size(24.dp))"
new_icon = "tint = Color.White, modifier = Modifier.size(24.dp))"
if new_icon in content:
    print("SKIP: icon tint already patched")
elif old_icon in content:
    content = content.replace(old_icon, new_icon, 1)
    changed = True
else:
    print("WARN: icon tint anchor not found — already patched or changed manually?")

if changed:
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: MediaPickerSheet.kt -> sheet now visible, icons now white")
else:
    print("No changes made (already up to date).")
PYEOF

echo "Done."
