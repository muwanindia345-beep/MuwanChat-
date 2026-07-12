package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val roomId: String,
    val uid: String,
    val username: String,
    val avatar: String?,
    val lastMessage: String,
    val lastTime: String,
    val lastSenderUid: String,
    val unreadCount: Int,
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val onlineCount: Int = 0
)
