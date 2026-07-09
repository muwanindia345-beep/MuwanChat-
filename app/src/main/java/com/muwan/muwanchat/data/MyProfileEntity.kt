package com.muwan.muwanchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Apna profile cache karne ke liye — ek hi row rehti hai (single logged-in account abhi)
@Entity(tableName = "my_profile")
data class MyProfileEntity(
    @PrimaryKey val id: String = "me",
    val name: String?,
    val bio: String?,
    val city: String?,
    val country: String?,
    val gender: String?,
    val avatar: String?
)
