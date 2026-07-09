package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatWallpaperDao {

    @Query("SELECT * FROM chat_wallpapers WHERE roomId = :roomId LIMIT 1")
    fun observeByRoomId(roomId: String): Flow<ChatWallpaperEntity?>

    @Query("SELECT * FROM chat_wallpapers WHERE roomId = :roomId LIMIT 1")
    suspend fun getByRoomId(roomId: String): ChatWallpaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wallpaper: ChatWallpaperEntity)

    @Query("DELETE FROM chat_wallpapers WHERE roomId = :roomId")
    suspend fun deleteByRoomId(roomId: String)
}
