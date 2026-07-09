package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// type = "color" | "gradient" | "pattern" | "image"
// value:
//   color    -> hex string, e.g. "#1a1a2e"
//   gradient -> preset id, e.g. "grad_1"
//   pattern  -> preset id, e.g. "pattern_1"
//   image    -> absolute file path on internal storage
@Entity(tableName = "chat_wallpapers")
data class ChatWallpaperEntity(
    @PrimaryKey val roomId: String,
    val type: String,
    val value: String
)
