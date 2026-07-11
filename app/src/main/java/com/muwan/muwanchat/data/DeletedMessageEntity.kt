package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// "Delete for me" (single message) ka local record — HiddenConversationEntity jaisa hi.
// Jab tak ye record hai, syncMessages() is message ko wapas insert nahi karega
// chahe backend baar baar bhej de.
@Entity(tableName = "deleted_messages")
data class DeletedMessageEntity(
    @PrimaryKey val messageId: String,
    val deletedAt: String
)
