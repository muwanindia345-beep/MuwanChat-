import sys
path = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = "    version = 14,"
new = "    version = 15,"
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("MuwanChatDb.kt patched: version 14 -> 15")
