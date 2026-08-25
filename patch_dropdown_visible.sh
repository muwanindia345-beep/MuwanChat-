#!/data/data/com.termux/files/usr/bin/bash
# patch_dropdown_visible.sh
# Fixes: "Settings" dropdown (conversation list) and chat header's
# 3-dot dropdown menu ("Set Wallpaper" etc.) blending into black background.
# Only visual fix — all menu items/actions work exactly as before.
# Run from project root (MuwanChat--main folder):
#   bash patch_dropdown_visible.sh

set -e

CONV_FILE="app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
HEADER_FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt"

for f in "$CONV_FILE" "$HEADER_FILE"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

python3 - << 'PYEOF'
import re

files_and_patches = [
    (
        "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt",
        [
            ("import com.muwan.muwanchat.DarkHeader\n",
             "import com.muwan.muwanchat.DarkHeader\nimport com.muwan.muwanchat.DarkSheet\n"),
            ("onDismissRequest = { showMenu = false },\n                                modifier = Modifier.background(DarkHeader)",
             "onDismissRequest = { showMenu = false },\n                                modifier = Modifier.background(DarkSheet)"),
        ]
    ),
    (
        "app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt",
        [
            ("import com.muwan.muwanchat.DarkHeader\n",
             "import com.muwan.muwanchat.DarkHeader\nimport com.muwan.muwanchat.DarkSheet\n"),
            ("onDismissRequest = onMenuDismiss,\n                    modifier = Modifier.background(DarkHeader)",
             "onDismissRequest = onMenuDismiss,\n                    modifier = Modifier.background(DarkSheet)"),
        ]
    ),
]

for path, patches in files_and_patches:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for old, new in patches:
        if new in content:
            print(f"SKIP: {path} already has this patch")
            continue
        if old in content:
            content = content.replace(old, new, 1)
            changed = True
        else:
            print(f"WARN: anchor not found in {path} — already patched or changed manually?")
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")
PYEOF

echo "Done."
