package com.muwan.muwanchat.data

import androidx.room.*

@Dao
interface HiddenConversationDao {

    @Query("SELECT * FROM hidden_conversations")
    suspend fun getAll(): List<HiddenConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenConversationEntity)

    @Query("DELETE FROM hidden_conversations WHERE roomId = :roomId")
    suspend fun unhide(roomId: String)
}
