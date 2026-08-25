#!/data/data/com.termux/files/usr/bin/bash
# patch_chatinput_visible.sh
# Fixes: Chat screen's "Message..." input box blending into black background.
# Sirf visual fix hai — send/voice/emoji/image functionality bilkul same rahegi.
# Run from project root (MuwanChat--main folder):
#   bash patch_chatinput_visible.sh

set -e

INPUT_FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatInputBar.kt"

if [ ! -f "$INPUT_FILE" ]; then
    echo "ERROR: $INPUT_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

# --- Step 1: swap the DarkHeader import for DarkSheet ---
OLD_IMPORT='import com.muwan.muwanchat.DarkHeader'
NEW_IMPORT='import com.muwan.muwanchat.DarkSheet'

if grep -qF "$NEW_IMPORT" "$INPUT_FILE"; then
    echo "SKIP: DarkSheet import already present in ChatInputBar.kt"
elif grep -qF "$OLD_IMPORT" "$INPUT_FILE"; then
    sed -i "s|$OLD_IMPORT|$NEW_IMPORT|" "$INPUT_FILE"
    echo "Patched: ChatInputBar.kt import -> DarkSheet"
else
    echo "WARN: import anchor not found — file already patched or changed manually?"
fi

# --- Step 2: use DarkSheet as the message box background ---
OLD_BG='.background(DarkHeader)
                .padding(horizontal = 4.dp),'
NEW_BG='.background(DarkSheet)
                .padding(horizontal = 4.dp),'

if grep -qF "$NEW_BG" "$INPUT_FILE"; then
    echo "SKIP: message box background already patched"
elif grep -qF "$OLD_BG" "$INPUT_FILE"; then
    python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatInputBar.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = ".background(DarkHeader)\n                .padding(horizontal = 4.dp),"
new = ".background(DarkSheet)\n                .padding(horizontal = 4.dp),"
if old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: ChatInputBar.kt message box -> DarkSheet background")
else:
    print("WARN: background anchor not found in ChatInputBar.kt")
PYEOF
else
    echo "WARN: background anchor not found — checking with python fallback..."
    python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatInputBar.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = ".background(DarkHeader)\n                .padding(horizontal = 4.dp),"
new = ".background(DarkSheet)\n                .padding(horizontal = 4.dp),"
if old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: ChatInputBar.kt message box -> DarkSheet background")
else:
    print("WARN: anchor not found — file already patched or changed manually?")
PYEOF
fi

echo "Done. Note: agar pehle patch_bottomsheet_color.sh nahi chalaya, pehle wo chalao — DarkSheet color usi mein define hota hai."
