package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Incoming chat requests ka local cache — screen khulte hi turant dikhein,
// backend se background me refresh hota hai.
@Entity(tableName = "chat_requests")
data class ChatRequestEntity(
    @PrimaryKey val id: String,
    val senderUid: String,
    val receiverUid: String,
    val status: String,
    val createdAt: String,
    val username: String,
    val avatar: String?
)
