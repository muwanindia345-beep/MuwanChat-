#!/data/data/com.termux/files/usr/bin/bash
# patch_compact_timestamp.sh
# Fixes: message bubble timestamp showed full date+time ("2026-08-27 13:18"),
# making bubbles unnecessarily tall/wide. Now shows just the time in
# WhatsApp-style 12-hour format ("1:18 PM"). Applies everywhere automatically
# (1-on-1 chat, group chat, conversation list stays separate/unaffected)
# since it's one shared formatting function used by both screens.
# Run from project root (MuwanChat--main folder):
#   bash patch_compact_timestamp.sh

set -e

FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatMessage.kt"

if [ ! -f "$FILE" ]; then
    echo "ERROR: $FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatMessage.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = '''fun formatMessageTime(raw: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(raw.take(19)) ?: return raw.take(16).replace("T", " ")
        val display = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        display.timeZone = TimeZone.getDefault()
        display.format(date)
    } catch (_: Exception) {
        raw.take(16).replace("T", " ")
    }
}'''

new = '''fun formatMessageTime(raw: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(raw.take(19)) ?: return raw.take(16).replace("T", " ")
        // Sirf time dikhate hai (date nahi) — bubble compact rehta hai, WhatsApp jaisa "1:18 PM"
        val display = SimpleDateFormat("h:mm a", Locale.getDefault())
        display.timeZone = TimeZone.getDefault()
        display.format(date)
    } catch (_: Exception) {
        raw.take(16).replace("T", " ")
    }
}'''

if new in content:
    print("SKIP: already patched")
elif old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: ChatMessage.kt -> bubble timestamp now compact (time only)")
else:
    print("WARN: anchor not found — already patched or changed manually?")
PYEOF

echo "Done."
