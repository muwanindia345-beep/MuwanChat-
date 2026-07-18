import sys
path = "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_event = '''        val fileName: String? = null,
        val mimeType: String? = null,
        val replyToId: String? = null
    ) : SocketEvent()'''
new_event = '''        val fileName: String? = null,
        val mimeType: String? = null,
        val replyToId: String? = null,
        val isForwarded: Boolean = false
    ) : SocketEvent()'''
if old_event not in content:
    print("NewMessage data class anchor not found!"); sys.exit(1)
content = content.replace(old_event, new_event, 1)

old_parse = '''                        replyToId = if (json.isNull("reply_to_id")) null else json.optString("reply_to_id")
                    )
                )
            }

            s.on("user_online")'''
new_parse = '''                        replyToId = if (json.isNull("reply_to_id")) null else json.optString("reply_to_id"),
                        isForwarded = json.optBoolean("is_forwarded", false)
                    )
                )
            }

            s.on("user_online")'''
if old_parse not in content:
    print("NewMessage parse anchor not found!"); sys.exit(1)
content = content.replace(old_parse, new_parse, 1)

old_send_sig = '''    fun sendMessage(
        id: String,
        receiverUid: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("receiver_uid", receiverUid)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
        }'''
new_send_sig = '''    fun sendMessage(
        id: String,
        receiverUid: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        isForwarded: Boolean = false,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("receiver_uid", receiverUid)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
            put("is_forwarded", isForwarded)
        }'''
if old_send_sig not in content:
    print("sendMessage anchor not found!"); sys.exit(1)
content = content.replace(old_send_sig, new_send_sig, 1)

old_group_sig = '''    fun sendGroupMessage(
        id: String,
        roomId: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("room_id", roomId)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
        }'''
new_group_sig = '''    fun sendGroupMessage(
        id: String,
        roomId: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        isForwarded: Boolean = false,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("room_id", roomId)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
            put("is_forwarded", isForwarded)
        }'''
if old_group_sig not in content:
    print("sendGroupMessage anchor not found!"); sys.exit(1)
content = content.replace(old_group_sig, new_group_sig, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("AppSocketManager.kt patched: isForwarded end-to-end (send + receive) wired")
