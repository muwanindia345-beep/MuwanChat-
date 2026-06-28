package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val senderUid: String,
    val receiverUid: String,
    val content: String,
    val type: String,
    val seen: Int,
    val createdAt: String
)
