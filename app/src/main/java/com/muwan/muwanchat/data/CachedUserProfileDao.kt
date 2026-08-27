package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedUserProfileDao {
    @Query("SELECT * FROM cached_user_profiles WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): CachedUserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CachedUserProfileEntity)
}
