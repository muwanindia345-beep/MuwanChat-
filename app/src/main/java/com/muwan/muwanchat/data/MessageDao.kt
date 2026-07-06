package com.muwan.muwanchat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // Reactive: ChatScreen isko collect karega, Room khud UI update karega
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    fun observeMessages(roomId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    suspend fun getMessages(roomId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("UPDATE messages SET seen = 1 WHERE roomId = :roomId AND senderUid != :myUid")
    suspend fun markSeen(roomId: String, myUid: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
