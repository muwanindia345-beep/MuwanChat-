import io, sys

p = "app/src/main/java/com/muwan/muwanchat/screens/CreateChannelScreen.kt"
with io.open(p, "r", encoding="utf-8", newline="") as f:
    s = f.read().replace("\r\n", "\n")

old = "colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),"
new = (
    "colors = ButtonDefaults.buttonColors(\n"
    "                        containerColor = DarkAccent,\n"
    "                        disabledContainerColor = DarkAccent.copy(alpha = 0.45f),\n"
    "                        disabledContentColor = Color.White\n"
    "                    ),"
)

if "disabledContainerColor = DarkAccent" in s:
    print("SKIP: pehle se patch ho chuka hai"); sys.exit(0)
if s.count(old) != 1:
    print("ERROR: Confirm button ka colors line exactly 1 baar nahi mila (%d)" % s.count(old)); sys.exit(1)

s = s.replace(old, new)
with io.open(p, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("OK: Confirm button ab orange hai (name khali ho to halka orange)")
