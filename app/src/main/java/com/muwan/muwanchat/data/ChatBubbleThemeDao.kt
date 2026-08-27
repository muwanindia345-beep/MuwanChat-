package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatBubbleThemeDao {

    @Query("SELECT * FROM chat_bubble_themes WHERE roomId = :roomId LIMIT 1")
    fun observeByRoomId(roomId: String): Flow<ChatBubbleThemeEntity?>

    @Query("SELECT * FROM chat_bubble_themes WHERE roomId = :roomId LIMIT 1")
    suspend fun getByRoomId(roomId: String): ChatBubbleThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(theme: ChatBubbleThemeEntity)

    @Query("DELETE FROM chat_bubble_themes WHERE roomId = :roomId")
    suspend fun deleteByRoomId(roomId: String)
}
