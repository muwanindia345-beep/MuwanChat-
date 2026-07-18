import sys
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatMessage.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_class = '''    val previewImage: String? = null,
    val previewUrl: String? = null
)'''
new_class = '''    val previewImage: String? = null,
    val previewUrl: String? = null,
    val isForwarded: Boolean = false
)'''
if old_class not in content:
    print("ChatMessage class anchor not found!"); sys.exit(1)
content = content.replace(old_class, new_class, 1)

old_item_mapper = '''    previewImage = link_preview?.image,
    previewUrl = link_preview?.url
)'''
new_item_mapper = '''    previewImage = link_preview?.image,
    previewUrl = link_preview?.url,
    isForwarded = is_forwarded
)'''
if old_item_mapper not in content:
    print("MessageItem mapper anchor not found!"); sys.exit(1)
content = content.replace(old_item_mapper, new_item_mapper, 1)

old_entity_mapper = '''    previewImage = previewImage,
    previewUrl = previewUrl
)'''
new_entity_mapper = '''    previewImage = previewImage,
    previewUrl = previewUrl,
    isForwarded = isForwarded
)'''
if old_entity_mapper not in content:
    print("MessageEntity mapper anchor not found!"); sys.exit(1)
content = content.replace(old_entity_mapper, new_entity_mapper, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatMessage.kt patched: isForwarded ab UI model tak pahunchta hai")
