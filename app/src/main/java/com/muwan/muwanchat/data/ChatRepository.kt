package com.muwan.muwanchat.data

import com.google.gson.Gson
import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.MessageItem
import com.muwan.muwanchat.screens.nowIso

object ChatRepository {

    private val gson = Gson()

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
        replyToId: String? = null,
        isForwarded: Boolean = false
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
                replyToId = replyToId,
                isForwarded = isForwarded
            )
        )

        val otherUid = if (senderUid == myUid) receiverUid else senderUid
        val existing = db.conversationDao().getByRoomId(roomId)
        val previewText = if (type == "text") content else when (type) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "audio" -> "🎤 Voice message"
            "music" -> "🎵 ${fileName ?: "Music"}"
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

    // Delete (for me / for everyone / socket se aaya delete event) ke baad
    // conversation list ka preview text recalculate karta hai — jo bhi
    // room ka SABSE NAYA message ho (deleted rows bhi count hoti hai,
    // kyunki "delete for everyone" wale rows tombstone ban ke DB me rehte hai),
    // usi ke hisaab se "lastMessage" set hota hai. Koi message hi na bacha ho
    // to blank kar deta hai (list me phir default "Say hi! 👋" dikhega).
    // unreadCount ko yeh function kabhi touch nahi karta.
    suspend fun refreshLastMessagePreview(db: MuwanChatDb, roomId: String) {
        // Deleted (tombstone) messages ko ignore karke sabse recent zinda
        // message dhoondo — delete karne par preview apne aap peeche wale
        // par cascade hoga. Koi bhi zinda message na bache to blank karo,
        // jisse conversation list me default "Say hi! 👋" dikhega.
        val latest = db.messageDao().getLatestNonDeletedMessage(roomId)
        if (latest == null) {
            db.conversationDao().syncLastMessagePreview(roomId, "", nowIso(), "")
            return
        }
        val previewText = when (latest.type) {
            "text" -> latest.content
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "audio" -> "🎤 Voice message"
            "music" -> "🎵 ${latest.fileName ?: "Music"}"
            "document" -> "📄 ${latest.fileName ?: "Document"}"
            else -> latest.content
        }
        db.conversationDao().syncLastMessagePreview(roomId, previewText, latest.createdAt, latest.senderUid)
    }

    suspend fun addConversationPlaceholder(
        db: MuwanChatDb,
        roomId: String,
        uid: String,
        username: String,
        avatar: String?,
        isGroup: Boolean = false,
        memberCount: Int = 0
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
                    unreadCount = 0,
                    isGroup = isGroup,
                    memberCount = memberCount,
                    onlineCount = 0
                )
            )
        }
    }

    suspend fun syncConversations(db: MuwanChatDb, items: List<ConversationItem>) {
        // "Delete chat" (for me) ka hidden record — jab tak backend ka lastTime
        // hiddenAt se naya na ho, us room ko wapas list me nahi daalna
        val hiddenMap = db.hiddenConversationDao().getAll().associateBy { it.roomId }

        // Message delete (for me / for everyone) ke turant baad local preview
        // "Say hi!" / blank set hota hai with lastTime = abhi ka waqt. Agar
        // usi second backend se ek full resync aa jaaye (jisko delete ka pata
        // abhi tak nahi chala — ya "delete for me" jo server ko pata hi nahi
        // chalta), to server ka PURANA lastMessage/lastTime local ke naye
        // (correct) state ko overwrite kar deta tha — isi wajah se delete
        // hote hi purana text wapas dikhta tha. Fix: server ka data sirf tabhi
        // apply karo jab uska lastTime local se naya (ya barabar) ho — kabhi
        // bhi ek NAYE local state ko ek PURANE server snapshot se overwrite
        // mat karo.
        val localMap = db.conversationDao().getAll().associateBy { it.roomId }

        val toUpsert = mutableListOf<ConversationEntity>()
        for (it in items) {
            val local = localMap[it.room_id]
            if (local != null && it.lastTime < local.lastTime) {
                // Server ka snapshot local se purana hai — abhi sync mat karo,
                // local (jyada recent) state hi sahi hai
                continue
            }
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
                        unreadCount = it.unreadCount,
                        isGroup = it.isGroup,
                        memberCount = it.memberCount,
                        onlineCount = it.onlineCount
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
                        unreadCount = it.unreadCount,
                        isGroup = it.isGroup,
                        memberCount = it.memberCount,
                        onlineCount = it.onlineCount
                    )
                )
            }
            // else: still hidden, skip — is chat ko wapas insert mat karo
        }
        db.conversationDao().upsertAll(toUpsert)
    }

    suspend fun syncMessages(db: MuwanChatDb, items: List<MessageItem>) {
        // "Delete for me" (single message) ka local record — jab tak hai,
        // backend se dobara aane pe bhi is message ko wapas insert mat karo
        val deletedIds = db.deletedMessageDao().getAllIds().toSet()
        val entities = items.filter { it.id !in deletedIds }.map {
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
                replyToId = it.reply_to_id,
                deleted = it.deleted,
                edited = it.edited,
                reactions = it.reactions?.let { r -> gson.toJson(r) },
                previewTitle = it.link_preview?.title,
                previewDescription = it.link_preview?.description,
                previewImage = it.link_preview?.image,
                previewUrl = it.link_preview?.url,
                isForwarded = it.is_forwarded
            )
        }
        db.messageDao().insertAll(entities)
    }

    // Jo messages backend pe "delete for everyone" ho chuke the jab hum offline the,
    // unko local Room DB se bhi hata do — ghost messages ka fix
    suspend fun reconcileDeleted(db: MuwanChatDb, deletedIds: List<String>) {
        if (deletedIds.isNotEmpty()) {
            db.messageDao().markDeletedByIds(deletedIds)
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
