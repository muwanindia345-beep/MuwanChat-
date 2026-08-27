package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_bubble_themes")
data class ChatBubbleThemeEntity(
    @PrimaryKey val roomId: String,
    val themeId: String
)
