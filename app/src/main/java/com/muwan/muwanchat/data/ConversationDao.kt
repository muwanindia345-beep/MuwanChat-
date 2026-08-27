package com.muwan.muwanchat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastTime DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE roomId = :roomId LIMIT 1")
    suspend fun getByRoomId(roomId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE roomId = :roomId LIMIT 1")
    fun observeByRoomId(roomId: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(conversation: ConversationEntity)

    @Query("""
        UPDATE conversations
        SET lastMessage = :lastMessage, lastTime = :lastTime, lastSenderUid = :lastSenderUid,
            unreadCount = CASE WHEN :lastSenderUid != :myUid THEN unreadCount + 1 ELSE unreadCount END
        WHERE roomId = :roomId
    """)
    suspend fun updateLastMessage(roomId: String, lastMessage: String, lastTime: String, lastSenderUid: String, myUid: String)

    // Message delete/edit hone ke baad preview text re-sync karne ke liye —
    // unreadCount ko bilkul touch nahi karta (naya message nahi hai, sirf existing
    // wale ka preview update ho raha hai)
    @Query("""
        UPDATE conversations
        SET lastMessage = :lastMessage, lastTime = :lastTime, lastSenderUid = :lastSenderUid
        WHERE roomId = :roomId
    """)
    suspend fun syncLastMessagePreview(roomId: String, lastMessage: String, lastTime: String, lastSenderUid: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE roomId = :roomId")
    suspend fun clearUnread(roomId: String)

    @Query("DELETE FROM conversations WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("UPDATE conversations SET isRemoved = 1, removedByUsername = :removedByUsername WHERE roomId = :roomId")
    suspend fun markRemoved(roomId: String, removedByUsername: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}
