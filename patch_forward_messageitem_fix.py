import sys
path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''    val reactions: List<MessageReaction>? = null,
    val link_preview: LinkPreview? = null
)'''
new = '''    val reactions: List<MessageReaction>? = null,
    val link_preview: LinkPreview? = null,
    val is_forwarded: Boolean = false
)'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatApi.kt patched: MessageItem ab is_forwarded field read karta hai (compile fix)")
