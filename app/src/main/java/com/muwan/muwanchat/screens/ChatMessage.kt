package com.muwan.muwanchat.screens

import android.net.Uri
import com.muwan.muwanchat.network.MessageItem
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String,
    val text: String,
    val sent: Boolean,         // true = aapka message
    val time: String,
    val imageUri: Uri? = null,
    val replyTo: ChatMessage? = null
)

fun MessageItem.toChatMessage(myUid: String) = ChatMessage(
    id = id,
    text = content,
    sent = sender_uid == myUid,
    time = created_at.take(16).replace("T", " "),
    imageUri = null
)

fun nowTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}
