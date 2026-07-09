package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// "Delete chat" (for me only) ka local record — jab tak backend se koi
// NAYA message (hiddenAt se newer) na aaye, yeh room list me nahi dikhega
@Entity(tableName = "hidden_conversations")
data class HiddenConversationEntity(
    @PrimaryKey val roomId: String,
    val hiddenAt: String
)
