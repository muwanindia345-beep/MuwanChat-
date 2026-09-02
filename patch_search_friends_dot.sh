#!/data/data/com.termux/files/usr/bin/bash
# patch_search_friends_dot.sh
# UserSearchScreen: "friends" status ka "Accepted ✅" pill hata ke
# ek chota green dot + chat icon kar deta hai — layout/UI/UX same rehta hai,
# bas username/name ke liye zyada jagah bach jaati hai.
# Run from project root (MuwanChat--main folder):
#   bash patch_search_friends_dot.sh

set -e

FILE="app/src/main/java/com/muwan/muwanchat/screens/UserSearchScreen.kt"

if [ ! -f "$FILE" ]; then
  echo "Error: run this script from the MuwanChat--main repo root."
  exit 1
fi

if grep -qF 'IconButton' "$FILE" && ! grep -qF 'Text("Accepted' "$FILE"; then
  echo "[skip] already patched"
  exit 0
fi

if ! grep -qF 'Text("Accepted' "$FILE"; then
  echo "[FAIL] anchor text not found in $FILE — file may have changed."
  exit 1
fi

python3 - "$FILE" << 'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    s = f.read()

old = '''                        "friends" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(AcceptedGreen, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Accepted \u2705", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton('''

new = '''                        "friends" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(AcceptedGreen)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                IconButton('''

assert old in s, "anchor not found"
s = s.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(s)
print("[ok] friends status -> green dot + chat icon")
PYEOF
