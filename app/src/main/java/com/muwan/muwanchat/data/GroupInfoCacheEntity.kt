package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Group ka poora data (members, admins, settings, invite code) local cache
// me JSON string ke roop me store hota hai — nested lists/objects (memberProfiles,
// pendingRequests) ke liye alag Room columns/TypeConverters banane se simpler
// aur backend response ke sath hamesha sync (naya field aaye to bhi tootega nahi).
@Entity(tableName = "group_info_cache")
data class GroupInfoCacheEntity(
    @PrimaryKey val groupId: String,
    val json: String
)
