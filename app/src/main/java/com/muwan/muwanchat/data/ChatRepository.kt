package com.muwan.muwanchat.data

import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.MessageItem
import com.muwan.muwanchat.screens.nowIso

// Jahan se bhi (socket ya API) message aaye, sirf isi se guzarna hai —
// yehi messages + conversations dono table consistent rakhta hai
object ChatRepository {

    suspend fun recordMessage(
        db: MuwanChatDb,
        id: String,
        roomId: String,
        senderUid: String,
        receiverUid: String,
        content: String,
        type: String,
        createdAt: String,
        myUid: String,
        otherUsername: String? = null,
        otherAvatar: String? = null
    ) {
        db.messageDao().insert(
            MessageEntity(
                id = id,
                roomId = roomId,
                senderUid = senderUid,
                receiverUid = receiverUid,
                content = content,
                type = type,
                seen = 0,
                createdAt = createdAt
            )
        )

        val otherUid = if (senderUid == myUid) receiverUid else senderUid
        val existing = db.conversationDao().getByRoomId(roomId)
        if (existing == null) {
            db.conversationDao().upsertOne(
                ConversationEntity(
                    roomId = roomId,
                    uid = otherUid,
                    username = otherUsername ?: "Unknown",
                    avatar = otherAvatar,
                    lastMessage = content,
                    lastTime = createdAt,
                    lastSenderUid = senderUid,
                    unreadCount = if (senderUid != myUid) 1 else 0
                )
            )
        } else {
            db.conversationDao().updateLastMessage(roomId, content, createdAt, senderUid, myUid)
        }
    }

    // Request accept hote hi conversation list mein turant naya entry daalne ke liye —
    // koi message abhi tak nahi aaya, isliye lastMessage khaali rakhte hain
    suspend fun addConversationPlaceholder(
        db: MuwanChatDb,
        roomId: String,
        uid: String,
        username: String,
        avatar: String?
    ) {
        val existing = db.conversationDao().getByRoomId(roomId)
        if (existing == null) {
            db.conversationDao().upsertOne(
                ConversationEntity(
                    roomId = roomId,
                    uid = uid,
                    username = username,
                    avatar = avatar,
                    lastMessage = "",
                    lastTime = nowIso(),
                    lastSenderUid = "",
                    unreadCount = 0
                )
            )
        }
    }

    suspend fun syncConversations(db: MuwanChatDb, items: List<ConversationItem>) {
        val entities = items.map {
            ConversationEntity(
                roomId = it.room_id,
                uid = it.uid,
                username = it.username,
                avatar = it.avatar,
                lastMessage = it.lastMessage,
                lastTime = it.lastTime,
                lastSenderUid = it.lastSenderUid,
                unreadCount = it.unreadCount
            )
        }
        db.conversationDao().upsertAll(entities)
    }

    suspend fun syncMessages(db: MuwanChatDb, items: List<MessageItem>) {
        val entities = items.map {
            MessageEntity(
                id = it.id,
                roomId = it.room_id,
                senderUid = it.sender_uid,
                receiverUid = it.receiver_uid,
                content = it.content,
                type = it.type,
                seen = it.seen,
                createdAt = it.created_at
            )
        }
        db.messageDao().insertAll(entities)
    }

    suspend fun clearUnread(db: MuwanChatDb, roomId: String) {
        db.conversationDao().clearUnread(roomId)
    }
}
