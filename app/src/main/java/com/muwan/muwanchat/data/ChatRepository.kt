package com.muwan.muwanchat.data

import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.MessageItem
import com.muwan.muwanchat.screens.nowIso

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
        otherAvatar: String? = null,
        status: String = "SENT",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null
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
                createdAt = createdAt,
                status = status,
                fileName = fileName,
                mimeType = mimeType,
                replyToId = replyToId
            )
        )

        val otherUid = if (senderUid == myUid) receiverUid else senderUid
        val existing = db.conversationDao().getByRoomId(roomId)
        val previewText = if (type == "text") content else when (type) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "document" -> "📄 ${fileName ?: "Document"}"
            else -> content
        }
        if (existing == null) {
            db.conversationDao().upsertOne(
                ConversationEntity(
                    roomId = roomId,
                    uid = otherUid,
                    username = otherUsername ?: "Unknown",
                    avatar = otherAvatar,
                    lastMessage = previewText,
                    lastTime = createdAt,
                    lastSenderUid = senderUid,
                    unreadCount = if (senderUid != myUid) 1 else 0
                )
            )
        } else {
            db.conversationDao().updateLastMessage(roomId, previewText, createdAt, senderUid, myUid)
        }
    }

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
        // "Delete chat" (for me) ka hidden record — jab tak backend ka lastTime
        // hiddenAt se naya na ho, us room ko wapas list me nahi daalna
        val hiddenMap = db.hiddenConversationDao().getAll().associateBy { it.roomId }

        val toUpsert = mutableListOf<ConversationEntity>()
        for (it in items) {
            val hidden = hiddenMap[it.room_id]
            if (hidden == null) {
                toUpsert.add(
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
                )
            } else if (it.lastTime > hidden.hiddenAt) {
                // Naya message aa chuka hai delete ke baad — WhatsApp jaisa, chat wapas dikhao
                db.hiddenConversationDao().unhide(it.room_id)
                toUpsert.add(
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
                )
            }
            // else: still hidden, skip — is chat ko wapas insert mat karo
        }
        db.conversationDao().upsertAll(toUpsert)
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
                createdAt = it.created_at,
                status = "SENT",
                fileName = it.file_name,
                mimeType = it.mime_type,
                replyToId = it.reply_to_id
            )
        }
        db.messageDao().insertAll(entities)
    }

    // Jo messages backend pe "delete for everyone" ho chuke the jab hum offline the,
    // unko local Room DB se bhi hata do — ghost messages ka fix
    suspend fun reconcileDeleted(db: MuwanChatDb, deletedIds: List<String>) {
        if (deletedIds.isNotEmpty()) {
            db.messageDao().deleteByIds(deletedIds)
        }
    }

    // "Delete chat" (for me only) — messages + conversation row local se hatao,
    // backend/doosre user ko kuch touch nahi hota
    suspend fun deleteChatsLocally(db: MuwanChatDb, roomIds: Set<String>) {
        val now = nowIso()
        for (roomId in roomIds) {
            db.messageDao().deleteByRoom(roomId)
            db.conversationDao().deleteByRoom(roomId)
            db.hiddenConversationDao().hide(HiddenConversationEntity(roomId, now))
        }
    }

    suspend fun clearUnread(db: MuwanChatDb, roomId: String) {
        db.conversationDao().clearUnread(roomId)
    }
}
