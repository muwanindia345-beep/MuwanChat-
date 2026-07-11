package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val senderUid: String,
    val receiverUid: String,
    val content: String,   // text ke liye plain text, media ke liye URL
    val type: String,      // text, image, video, document
    val seen: Int,
    val createdAt: String,
    val status: String = "SENT",
    val fileName: String? = null,
    val mimeType: String? = null,
    val replyToId: String? = null,
    val deleted: Boolean = false,   // true = "delete for everyone" ho chuka hai
    val edited: Boolean = false,
    val reactions: String? = null   // JSON array string: [{"emoji":"👍","userIds":["u1"]}]
)
