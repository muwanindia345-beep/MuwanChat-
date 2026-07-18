import sys
path = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old = '''data class SendMessageRequest(
    val receiver_uid: String,
    val content: String,
    val type: String = "text",
    val file_name: String? = null,
    val mime_type: String? = null,
    val reply_to_id: String? = null
)'''
new = '''data class SendMessageRequest(
    val receiver_uid: String,
    val content: String,
    val type: String = "text",
    val file_name: String? = null,
    val mime_type: String? = null,
    val reply_to_id: String? = null,
    val is_forwarded: Boolean = false
)'''
if old not in content:
    print("Anchor not found!"); sys.exit(1)
content = content.replace(old, new, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("ChatApi.kt patched: SendMessageRequest ab is_forwarded bhej sakta hai")
