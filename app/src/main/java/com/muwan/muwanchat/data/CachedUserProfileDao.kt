package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedUserProfileDao {
    @Query("SELECT * FROM cached_user_profiles WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): CachedUserProfileEntity?

    @Query("SELECT * FROM cached_user_profiles WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): Flow<CachedUserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CachedUserProfileEntity)
}
