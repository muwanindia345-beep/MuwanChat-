package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Dusre users ke profile (jo humne kabhi dekhe) ka local cache — screen
// khulte hi turant dikhein, backend se background me refresh hota hai.
// Friendship status ab yahan bhi cache hota hai, taaki offline me sahi
// button (Message/Media vs Request) dikhe — pehle yeh sirf live check hota
// tha aur offline me galat "none" default dikhata tha.
@Entity(tableName = "cached_user_profiles")
data class CachedUserProfileEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val name: String?,
    val bio: String?,
    val city: String?,
    val country: String?,
    val gender: String?,
    val avatar: String?,
    val status: String = "none"
)
