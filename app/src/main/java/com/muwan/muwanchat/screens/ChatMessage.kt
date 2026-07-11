package com.muwan.muwanchat.screens

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muwan.muwanchat.data.MessageEntity
import com.muwan.muwanchat.network.MessageItem
import com.muwan.muwanchat.network.MessageReaction
import java.text.SimpleDateFormat
import java.util.*

private val reactionsGson = Gson()
private val reactionsListType = object : TypeToken<List<MessageReaction>>() {}.type

fun parseReactionsJson(json: String?): List<MessageReaction> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        reactionsGson.fromJson(json, reactionsListType) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

data class ChatMessage(
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
)

fun formatMessageTime(raw: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(raw.take(19)) ?: return raw.take(16).replace("T", " ")
        val display = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        display.timeZone = TimeZone.getDefault()
        display.format(date)
    } catch (_: Exception) {
        raw.take(16).replace("T", " ")
    }
}

fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = if (type == "text") content else "",
    sent = sender_uid == myUid,
    time = formatMessageTime(created_at),
    type = type,
    mediaUrl = if (type != "text") content else null,
    fileName = file_name,
    mimeType = mime_type,
    replyToId = reply_to_id,
    status = "SENT",
    isDeleted = deleted,
    isEdited = edited,
    reactions = reactions ?: emptyList()
)

fun MessageEntity.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = if (type == "text") content else "",
    sent = senderUid == myUid,
    time = formatMessageTime(createdAt),
    type = type,
    mediaUrl = if (type != "text") content else null,
    fileName = fileName,
    mimeType = mimeType,
    replyToId = replyToId,
    status = status,
    isDeleted = deleted,
    isEdited = edited,
    reactions = parseReactionsJson(reactions)
)

fun nowTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}

fun nowIso(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}
