#!/data/data/com.termux/files/usr/bin/bash
# patch_talkwave_toolbar_title.sh
# Renames the top-bar title on ConversationListScreen from "MuwanChat" to
# "TalkWave" — same position, same white/bold styling, just the text.
# Run from project root (MuwanChat--main folder):
#   bash patch_talkwave_toolbar_title.sh

set -e

FILE="app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"

if [ ! -f "$FILE" ]; then
  echo "Error: run this script from the MuwanChat--main repo root."
  exit 1
fi

OLD='Text("MuwanChat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)'
NEW='Text("TalkWave", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)'

if grep -qF "$NEW" "$FILE"; then
  echo "[skip] already patched"
  exit 0
fi

if ! grep -qF "$OLD" "$FILE"; then
  echo "[FAIL] anchor text not found in $FILE — file may have changed."
  exit 1
fi

python3 - "$FILE" << 'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    s = f.read()
old = 'Text("MuwanChat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)'
new = 'Text("TalkWave", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)'
assert old in s, "anchor not found"
s = s.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(s)
print("[ok] toolbar title -> TalkWave")
PYEOF
