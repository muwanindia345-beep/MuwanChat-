#!/data/data/com.termux/files/usr/bin/bash
# fix_build_errors.sh
# Fixes the CI build failure:
#   ChatInputBar.kt:49 Unresolved reference: DarkHeader
#   ConversationListScreen.kt:308 Unresolved reference: DarkSheet
# Root cause: earlier patches were only half-applied (color changed but
# import missing, or import changed but color line untouched).
# This script re-checks and corrects both files, safe to re-run anytime.
# Run from project root (MuwanChat--main folder):
#   bash fix_build_errors.sh

set -e

COLORS_FILE="app/src/main/java/com/muwan/muwanchat/Colors.kt"
CONV_FILE="app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
INPUT_FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatInputBar.kt"

for f in "$COLORS_FILE" "$CONV_FILE" "$INPUT_FILE"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

# --- 1. Make sure DarkSheet is actually defined in Colors.kt ---
if grep -q "val DarkSheet" "$COLORS_FILE"; then
    echo "OK: DarkSheet already defined in Colors.kt"
else
    sed -i '/val DarkHeader = Color(0xFF000000)/a val DarkSheet = Color(0xFF1C1C1E)' "$COLORS_FILE"
    echo "FIXED: added DarkSheet to Colors.kt"
fi

# --- 2. Make sure ConversationListScreen.kt imports DarkSheet ---
if grep -q "^import com.muwan.muwanchat.DarkSheet$" "$CONV_FILE"; then
    echo "OK: ConversationListScreen.kt already imports DarkSheet"
else
    sed -i '/^import com.muwan.muwanchat.DarkHeader$/a import com.muwan.muwanchat.DarkSheet' "$CONV_FILE"
    echo "FIXED: added DarkSheet import to ConversationListScreen.kt"
fi

# --- 3. Make sure ChatInputBar.kt's message box actually uses DarkSheet ---
if grep -qF '.background(DarkSheet)' "$INPUT_FILE"; then
    echo "OK: ChatInputBar.kt background already uses DarkSheet"
elif grep -qF '.background(DarkHeader)' "$INPUT_FILE"; then
    sed -i 's/\.background(DarkHeader)/.background(DarkSheet)/' "$INPUT_FILE"
    echo "FIXED: ChatInputBar.kt background -> DarkSheet"
else
    echo "WARN: no .background(DarkHeader) or .background(DarkSheet) found in ChatInputBar.kt — check manually"
fi

# --- 4. Make sure ChatInputBar.kt's import matches (DarkSheet, not stray DarkHeader) ---
if grep -q "^import com.muwan.muwanchat.DarkSheet$" "$INPUT_FILE"; then
    echo "OK: ChatInputBar.kt already imports DarkSheet"
elif grep -q "^import com.muwan.muwanchat.DarkHeader$" "$INPUT_FILE"; then
    sed -i 's/^import com.muwan.muwanchat.DarkHeader$/import com.muwan.muwanchat.DarkSheet/' "$INPUT_FILE"
    echo "FIXED: ChatInputBar.kt import -> DarkSheet"
else
    sed -i '/^import com.muwan.muwanchat.DarkBg$/a import com.muwan.muwanchat.DarkSheet' "$INPUT_FILE"
    echo "FIXED: added missing DarkSheet import to ChatInputBar.kt"
fi

echo ""
echo "Done. Verifying no leftover unresolved references..."
grep -n "DarkHeader" "$INPUT_FILE" && echo "^^ still present in ChatInputBar.kt, check manually" || echo "OK: no DarkHeader left in ChatInputBar.kt"
