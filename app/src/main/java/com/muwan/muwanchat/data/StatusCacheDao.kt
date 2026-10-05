package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StatusCacheDao {
    @Query("SELECT * FROM status_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): StatusCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StatusCacheEntity)

    @Query("DELETE FROM status_cache WHERE `key` = :key")
    suspend fun delete(key: String)
}
