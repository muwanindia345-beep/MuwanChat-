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
    val removedByUsername: String? = null,
    // Group ki admin-only-send setting ka local cache — taaki screen open hote
    // hi (network wait kiye bina) sahi input bar / banner turant dikh jaaye.
    val onlyAdminsCanSend: Boolean = false,
    val amIAdmin: Boolean = false,
    // Pin chat feature — purely local/per-device (server ko iska pata nahi
    // chalta, jaise wallpaper/theme). null = pinned nahi hai. Non-null value
    // us waqt ka timestamp hai jab pin kiya gaya — isi se sabse recent pin
    // sabse upar order hota hai (max 3 tak — enforce ChatRepository.pinChats() mein).
    val pinnedAt: Long? = null
)
