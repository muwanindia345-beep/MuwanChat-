import sys

p = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
s = open(p, encoding="utf-8").read()

dot = '''                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
'''
vars_ = '''    val isRed = info.status == "missed" || info.status == "declined"
    val dotColor = if (isRed) Color(0xFFFF3B30) else Color(0xFF00E676)
'''
old_c = ("taaki custom theme ke saath bilkul match kare. Green dot = ringing/connected/ended,\n"
         "// red dot = missed/declined. Tap = wapas call.")
new_c = "taaki custom theme ke saath bilkul match kare. Tap = wapas call."

if "dotColor" not in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

if s.count(dot) != 1 or s.count(vars_) != 1:
    print("ERROR: expected code nahi mila (file badli hui hai?). Kuch change nahi kiya.")
    sys.exit(1)

s = s.replace(dot, "").replace(vars_, "").replace(old_c, new_c)
open(p, "w", encoding="utf-8").write(s)
print("Done: call bubble ka dot remove ho gaya.")
