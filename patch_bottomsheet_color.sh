#!/data/data/com.termux/files/usr/bin/bash
# patch_bottomsheet_color.sh
# Fixes: "Create Group" bottom sheet blending into black background.
# Run this from the project root (MuwanChat--main folder), e.g.:
#   cd MuwanChat--main
#   bash patch_bottomsheet_color.sh

set -e

COLORS_FILE="app/src/main/java/com/muwan/muwanchat/Colors.kt"
CONV_LIST_FILE="app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"

if [ ! -f "$COLORS_FILE" ]; then
    echo "ERROR: $COLORS_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

if [ ! -f "$CONV_LIST_FILE" ]; then
    echo "ERROR: $CONV_LIST_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

# --- Step 1: Add DarkSheet color to Colors.kt (skip if already patched) ---
if grep -q "val DarkSheet" "$COLORS_FILE"; then
    echo "SKIP: DarkSheet already exists in Colors.kt"
else
    if ! grep -q "val DarkHeader = Color(0xFF000000)" "$COLORS_FILE"; then
        echo "WARN: anchor line for DarkHeader not found — Colors.kt may have changed. Aborting Colors.kt patch."
    else
        sed -i '/val DarkHeader = Color(0xFF000000)/a val DarkSheet = Color(0xFF1C1C1E)' "$COLORS_FILE"
        echo "Patched: Colors.kt -> added DarkSheet (#1C1C1E)"
    fi
fi

# --- Step 2: Use DarkSheet as the containerColor for the FAB bottom sheet ---
OLD_LINE='ModalBottomSheet(onDismissRequest = { showFabSheet = false }, containerColor = DarkHeader) {'
NEW_LINE='ModalBottomSheet(onDismissRequest = { showFabSheet = false }, containerColor = DarkSheet) {'

if grep -qF "$NEW_LINE" "$CONV_LIST_FILE"; then
    echo "SKIP: ConversationListScreen.kt already patched"
elif grep -qF "$OLD_LINE" "$CONV_LIST_FILE"; then
    sed -i "s|$OLD_LINE|$NEW_LINE|" "$CONV_LIST_FILE"
    echo "Patched: ConversationListScreen.kt -> FAB sheet now uses DarkSheet color"
else
    echo "WARN: anchor line not found in ConversationListScreen.kt — file already patched or changed manually?"
fi

echo "Done."
