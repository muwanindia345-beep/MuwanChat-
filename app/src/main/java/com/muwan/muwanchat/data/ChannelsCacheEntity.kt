package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Broadcast tab ki channels list ka local cache (offline-first). Alag table
// isliye ki "conversations" table ko touch na karna pade — wahan channel row
// aane se Chats tab me leak hota tha. AcceptedUsersCacheEntity jaisa hi pattern:
// ek hi row per account (DB already per-uid hai), list JSON string me.
@Entity(tableName = "channels_cache")
data class ChannelsCacheEntity(
    @PrimaryKey val key: String = "channels",
    val json: String
)
