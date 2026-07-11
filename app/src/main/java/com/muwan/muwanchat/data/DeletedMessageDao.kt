package com.muwan.muwanchat.data

import androidx.room.*

@Dao
interface DeletedMessageDao {

    @Query("SELECT messageId FROM deleted_messages")
    suspend fun getAllIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markDeleted(entities: List<DeletedMessageEntity>)

    @Query("DELETE FROM deleted_messages WHERE messageId = :messageId")
    suspend fun unmark(messageId: String)
}
