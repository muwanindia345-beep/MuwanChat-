package com.muwan.muwanchat.screens

import android.net.Uri
import com.muwan.muwanchat.data.MessageEntity
import com.muwan.muwanchat.network.MessageItem
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String,
    val text: String,
    val sent: Boolean,         // true = aapka message
    val time: String,
    val imageUri: Uri? = null,
    val replyTo: ChatMessage? = null,
    val status: String = "SENT" // PENDING, SENT, SEEN, FAILED
)

fun formatMessageTime(raw: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(raw.take(19)) ?: return raw.take(16).replace("T", " ")
        val display = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        display.timeZone = TimeZone.getDefault() // device local zone
        display.format(date)
    } catch (_: Exception) {
        raw.take(16).replace("T", " ")
    }
}

fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = content,
    sent = sender_uid == myUid,
    time = formatMessageTime(created_at),
    imageUri = null,
    status = "SENT"
)

fun MessageEntity.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = content,
    sent = senderUid == myUid,
    time = formatMessageTime(createdAt),
    imageUri = null,
    status = status
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
