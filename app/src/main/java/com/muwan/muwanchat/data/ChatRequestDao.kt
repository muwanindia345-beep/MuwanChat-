package com.muwan.muwanchat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRequestDao {
    @Query("SELECT * FROM chat_requests ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ChatRequestEntity>>

    @Query("SELECT * FROM chat_requests ORDER BY createdAt DESC")
    suspend fun getAll(): List<ChatRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: ChatRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(requests: List<ChatRequestEntity>)

    @Query("DELETE FROM chat_requests WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_requests")
    suspend fun clearAll()
}
