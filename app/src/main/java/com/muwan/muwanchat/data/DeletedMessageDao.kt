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

    // DELETE_FOR_ME_FIX: socket/recordMessage path ke liye O(1) check
    @Query("SELECT EXISTS(SELECT 1 FROM deleted_messages WHERE messageId = :messageId)")
    suspend fun isDeleted(messageId: String): Boolean
}
