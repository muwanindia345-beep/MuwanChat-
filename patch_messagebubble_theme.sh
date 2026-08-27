#!/data/data/com.termux/files/usr/bin/bash
# patch_messagebubble_theme.sh
# Makes MessageBubble.kt theme-aware: sent-bubble color, corner radius,
# padding and text size now come from a `bubbleTheme` parameter instead
# of being hardcoded. Defaults to "Original" (current orange look) so
# every existing call site keeps working exactly as before until wired.
# NOTHING ELSE changes — swipe-to-reply, forward, image/video/document/
# audio handling, reactions, timestamps, selection mode: all untouched.
# Run from project root (MuwanChat--main folder):
#   bash patch_messagebubble_theme.sh

set -e

FILE="app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"

if [ ! -f "$FILE" ]; then
    echo "ERROR: $FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

changed = False

# 1. Imports
old = """import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBubbleReceived
import com.muwan.muwanchat.DarkBubbleSent
import com.muwan.muwanchat.DarkInputBg"""
new = """import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBubbleReceived
import com.muwan.muwanchat.DarkBubbleSent
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.data.BubbleTheme
import com.muwan.muwanchat.data.BubbleThemePresets"""
if new in content:
    print("SKIP: imports already patched")
elif old in content:
    content = content.replace(old, new, 1); changed = True
else:
    print("WARN: import anchor not found")

# 2. Function parameter
old = """    senderAvatar: String? = null,
    senderName: String? = null
) {"""
new = """    senderAvatar: String? = null,
    senderName: String? = null,
    bubbleTheme: BubbleTheme = BubbleThemePresets.ORIGINAL
) {"""
if new in content:
    print("SKIP: bubbleTheme parameter already added")
elif old in content:
    content = content.replace(old, new, 1); changed = True
else:
    print("WARN: parameter anchor not found")

# 3. Local computed size/color values
old = '    val isSticker = message.type == "gif"'
new = '''    val isSticker = message.type == "gif"

    // Message Theme ke hisaab se sirf bubble ka color/size/shape decide hota hai —
    // baaki kuch bhi (gestures, media layout, reactions, timestamp) unaffected rehta hai.
    val bubbleCornerBig = if (bubbleTheme.compact) 14.dp else 18.dp
    val bubbleCornerTail = 4.dp
    val bubbleHPad = if (bubbleTheme.compact) 10.dp else 14.dp
    val bubbleVPad = if (bubbleTheme.compact) 7.dp else 10.dp
    val bubbleFontSize = if (bubbleTheme.compact) 14.sp else 15.sp'''
if "val bubbleCornerBig" in content:
    print("SKIP: theme size values already added")
elif old in content:
    content = content.replace(old, new, 1); changed = True
else:
    print("WARN: isSticker anchor not found")

# 4. Bubble shape/color/padding block
old = """                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomEnd = if (message.sent) 4.dp else 18.dp,
                        bottomStart = if (message.sent) 18.dp else 4.dp
                    )
                )
                .background(if (isSticker) Color.Transparent else if (message.sent) DarkBubbleSent else DarkBubbleReceived)
                .padding(
                    horizontal = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else 14.dp,
                    vertical = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else 10.dp
                )"""
new = """                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = bubbleCornerBig, topEnd = bubbleCornerBig,
                        bottomEnd = if (message.sent) bubbleCornerTail else bubbleCornerBig,
                        bottomStart = if (message.sent) bubbleCornerBig else bubbleCornerTail
                    )
                )
                .background(if (isSticker) Color.Transparent else if (message.sent) bubbleTheme.sentColor else DarkBubbleReceived)
                .padding(
                    horizontal = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else bubbleHPad,
                    vertical = if (isSticker) 0.dp else if (isMedia && !message.isDeleted) 4.dp else bubbleVPad
                )"""
if "bubbleTheme.sentColor else DarkBubbleReceived" in content:
    print("SKIP: bubble shape/color/padding already patched")
elif old in content:
    content = content.replace(old, new, 1); changed = True
else:
    print("WARN: bubble shape/color/padding anchor not found")

# 5. Text font size
old = '''                if (message.text.isNotBlank()) {
                    if (isSelectionMode) {
                        // Selection mode me poore bubble ka tap select/deselect ke liye reserved hai
                        Text(message.text, color = Color.White, fontSize = 15.sp)
                    } else {
                        Text(
                            text = annotatedText,
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                            onTextLayout = { textLayoutResult = it }
                        )
                    }
                }'''
new = '''                if (message.text.isNotBlank()) {
                    if (isSelectionMode) {
                        // Selection mode me poore bubble ka tap select/deselect ke liye reserved hai
                        Text(message.text, color = Color.White, fontSize = bubbleFontSize)
                    } else {
                        Text(
                            text = annotatedText,
                            style = TextStyle(color = Color.White, fontSize = bubbleFontSize),
                            onTextLayout = { textLayoutResult = it }
                        )
                    }
                }'''
if "fontSize = bubbleFontSize" in content:
    print("SKIP: text font size already patched")
elif old in content:
    content = content.replace(old, new, 1); changed = True
else:
    print("WARN: text fontSize anchor not found")

if changed:
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: MessageBubble.kt")
else:
    print("No changes made (already up to date).")
PYEOF

echo ""
echo "Verifying brace/paren balance..."
python3 -c "
content = open('app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'braces {o}/{c}, parens {po}/{pc} -> {status}')
"
