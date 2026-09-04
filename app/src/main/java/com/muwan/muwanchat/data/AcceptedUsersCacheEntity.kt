package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Accepted users (mutual connections) ki poori list local cache me JSON string
// ke roop me store hoti hai — GroupInfoCacheEntity jaisa hi pattern, ek hi row
// per account (uid) chahiye isliye fixed key "accepted_users" use karte hain.
@Entity(tableName = "accepted_users_cache")
data class AcceptedUsersCacheEntity(
    @PrimaryKey val key: String = "accepted_users",
    val json: String
)
