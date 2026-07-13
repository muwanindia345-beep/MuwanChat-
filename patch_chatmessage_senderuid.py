import io
path = "app/src/main/java/com/muwan/muwanchat/screens/ChatMessage.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old1 = """data class ChatMessage(
    val id: String,
    val text: String,
    val sent: Boolean,
    val time: String,
    val type: String = "text",       // text, image, video, document
    val mediaUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val replyTo: ChatMessage? = null,
    val replyToId: String? = null,
    val status: String = "SENT",
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val reactions: List<MessageReaction> = emptyList()
)"""

new1 = """data class ChatMessage(
    val id: String,
    val text: String,
    val sent: Boolean,
    val time: String,
    val type: String = "text",       // text, image, video, document, system
    val mediaUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val replyTo: ChatMessage? = null,
    val replyToId: String? = null,
    val status: String = "SENT",
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val reactions: List<MessageReaction> = emptyList(),
    val senderUid: String = ""
)"""

old2 = """    isDeleted = deleted,
    isEdited = edited,
    reactions = reactions ?: emptyList()
)"""

new2 = """    isDeleted = deleted,
    isEdited = edited,
    reactions = reactions ?: emptyList(),
    senderUid = sender_uid
)"""

old3 = """    isDeleted = deleted,
    isEdited = edited,
    reactions = parseReactionsJson(reactions)
)"""

new3 = """    isDeleted = deleted,
    isEdited = edited,
    reactions = parseReactionsJson(reactions),
    senderUid = senderUid
)"""

patches = [(old1, new1, "data class field"), (old2, new2, "MessageItem.toChatMessage"), (old3, new3, "MessageEntity.toChatMessage")]
for old, new, label in patches:
    count = content.count(old)
    if count != 1:
        print(f"MATCH FAILED ({label}): found {count}, expected 1")
    else:
        content = content.replace(old, new)
        print(f"Patched: {label}")

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Done -> ChatMessage.kt")
