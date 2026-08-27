package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GroupInfoCacheDao {
    @Query("SELECT * FROM group_info_cache WHERE groupId = :groupId LIMIT 1")
    suspend fun get(groupId: String): GroupInfoCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GroupInfoCacheEntity)
}
