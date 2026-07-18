import sys
path = "app/src/main/java/com/muwan/muwanchat/data/MessageEntity.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''    val edited: Boolean = false,
    val reactions: String? = null   // JSON array string: [{"emoji":"👍","userIds":["u1"]}]
)'''
new = '''    val edited: Boolean = false,
    val reactions: String? = null,   // JSON array string: [{"emoji":"👍","userIds":["u1"]}]
    val isForwarded: Boolean = false
)'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("MessageEntity.kt patched: isForwarded field added")
