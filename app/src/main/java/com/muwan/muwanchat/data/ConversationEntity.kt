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
    val onlineCount: Int = 0,
    // Admin/owner ne remove kiya to true — chat history read-only rehti hai,
    // sirf input bar area ek banner se replace hota hai (khud-leave case me
    // ye kabhi set nahi hota, wo already deleteChatsLocally se turant hat jaata hai)
    val isRemoved: Boolean = false,
    val removedByUsername: String? = null
)
