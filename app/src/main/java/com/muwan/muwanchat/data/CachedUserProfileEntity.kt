package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Dusre users ke profile (jo humne kabhi dekhe) ka local cache — screen
// khulte hi turant dikhein, backend se background me refresh hota hai.
// Friendship status yahan cache nahi hota — wo fast-changing hai, hamesha live check hota hai.
@Entity(tableName = "cached_user_profiles")
data class CachedUserProfileEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val name: String?,
    val bio: String?,
    val city: String?,
    val country: String?,
    val gender: String?,
    val avatar: String?
)
