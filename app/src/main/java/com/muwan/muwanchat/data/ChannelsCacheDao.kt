package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChannelsCacheDao {
    @Query("SELECT * FROM channels_cache WHERE `key` = 'channels' LIMIT 1")
    suspend fun get(): ChannelsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ChannelsCacheEntity)
}
