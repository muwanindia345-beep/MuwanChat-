package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AcceptedUsersCacheDao {
    @Query("SELECT * FROM accepted_users_cache WHERE `key` = 'accepted_users' LIMIT 1")
    suspend fun get(): AcceptedUsersCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AcceptedUsersCacheEntity)
}
