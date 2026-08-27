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

    // Media screen ke 3 tabs (Photos/Videos/Documents) ke liye — koi limit
    // nahi, chat mein jitne bhi hain sab yahan aayenge, sabse naya sabse upar
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND type = :type AND deleted = 0 ORDER BY createdAt DESC")
    fun observeMediaMessages(roomId: String, type: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    // "Delete for me" — row hi hata do, dusre bande ki screen se koi matlab nahi
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    // "Delete for everyone" — row zinda rehti hai, bas tombstone bubble ban jaati hai
    @Query("UPDATE messages SET deleted = 1, content = '' WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("UPDATE messages SET deleted = 1, content = '' WHERE id IN (:ids)")
    suspend fun markDeletedByIds(ids: List<String>)

    // Edit message — content update + edited flag ek saath
    @Query("UPDATE messages SET content = :content, edited = 1 WHERE id = :id")
    suspend fun editMessage(id: String, content: String)

    // Reaction add/remove — poora reactions JSON string replace ho jata hai (server hi source of truth hai)
    @Query("UPDATE messages SET reactions = :reactionsJson WHERE id = :id")
    suspend fun updateReactions(id: String, reactionsJson: String)

    @Query("UPDATE messages SET previewTitle = :title, previewDescription = :description, previewImage = :image, previewUrl = :url WHERE id = :id")
    suspend fun updateLinkPreview(id: String, title: String?, description: String?, image: String?, url: String?)

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("UPDATE messages SET seen = 1 WHERE roomId = :roomId AND senderUid != :myUid")
    suspend fun markSeen(roomId: String, myUid: String)

    // Apna bheja message ka status update karne ke liye (PENDING -> SENT/FAILED)
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    // Media message ka local content:// uri, upload complete hone ke baad asal
    // server URL se replace karta hai — status ek saath hi update ho jata hai
    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateMediaContent(id: String, content: String, status: String)

    // Doosre banda ne dekh liya to hamare bheje saare messages SEEN ho jaate hai
    @Query("UPDATE messages SET status = 'SEEN' WHERE roomId = :roomId AND senderUid = :myUid AND status != 'SEEN'")
    suspend fun markMySentAsSeen(roomId: String, myUid: String)

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(roomId: String): MessageEntity?

    // Preview ke liye: deleted (tombstone) messages ko skip karke sabse
    // recent zinda message dhoondta hai — taaki delete karne par preview
    // apne aap peeche wale message par cascade ho jaaye.
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND deleted = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestNonDeletedMessage(roomId: String): MessageEntity?

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
