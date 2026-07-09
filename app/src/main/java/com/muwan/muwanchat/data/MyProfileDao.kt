package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MyProfileDao {
    @Query("SELECT * FROM my_profile WHERE id = 'me' LIMIT 1")
    suspend fun get(): MyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: MyProfileEntity)
}
