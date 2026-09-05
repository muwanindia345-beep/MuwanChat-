# Pin chat feature — conversation list mein max 3 chats pin kar sakte ho
# (local/per-device, jaise wallpaper/theme — server ko iska pata nahi
# chalta). Selection mode mein delete se pehle pin icon aata hai, aur
# pinned chats list mein sabse upar + row par offline/date se pehle
# chhota 📌 badge dikhta hai.
#
# NOTE: iska DB migration 24 -> 25 hai (23 -> 24 slot ab @mentions feature
# use kar raha hai), isliye yeh @mentions patch ke BAAD hi lagana.
#
# Termux mein repo root (jahan app/ folder hai) se run karo:
#   python patch_pin_chats.py

f = "app/src/main/java/com/muwan/muwanchat/data/ConversationEntity.kt"
s = open(f).read()
old = '''    val onlyAdminsCanSend: Boolean = false,
    val amIAdmin: Boolean = false
)'''
new = '''    val onlyAdminsCanSend: Boolean = false,
    val amIAdmin: Boolean = false,
    // Pin chat feature — purely local/per-device (server ko iska pata nahi
    // chalta, jaise wallpaper/theme). null = pinned nahi hai. Non-null value
    // us waqt ka timestamp hai jab pin kiya gaya — isi se sabse recent pin
    // sabse upar order hota hai (max 3 tak — enforce ChatRepository.pinChats() mein).
    val pinnedAt: Long? = null
)'''
assert old in s, "ConversationEntity.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ ConversationEntity.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
s = open(f).read()
old = '''    @Query("SELECT * FROM conversations ORDER BY lastTime DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>'''
new = '''    // Pinned chats (pinnedAt not null) hamesha upar — sabse recent pin sabse
    // top, unke neeche baaki sab normal lastTime DESC order mein
    @Query("""
        SELECT * FROM conversations
        ORDER BY CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt DESC, lastTime DESC
    """)
    fun observeConversations(): Flow<List<ConversationEntity>>'''
assert old in s, "ConversationDao.kt: pattern1 not found"
s = s.replace(old, new, 1)

old2 = '''    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}'''
new2 = '''    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    // ── Pin chat (local-only, max 3 — limit ChatRepository mein enforce hoti hai) ──
    @Query("SELECT COUNT(*) FROM conversations WHERE pinnedAt IS NOT NULL")
    suspend fun getPinnedCount(): Int

    @Query("UPDATE conversations SET pinnedAt = :pinnedAt WHERE roomId = :roomId")
    suspend fun setPinned(roomId: String, pinnedAt: Long?)
}'''
assert old2 in s, "ConversationDao.kt: pattern2 not found"
s = s.replace(old2, new2, 1)
open(f, "w").write(s)
print("✅ ConversationDao.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/data/DbMigrations.kt"
s = open(f).read()
old = '''    val ALL: Array<Migration> = arrayOf(MIGRATION_23_24)
}'''
new = '''    // Pin chat feature — conversations table mein naya nullable column,
    // purana data (chats, unread count, wallpaper, theme sab) safe rehta hai.
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN pinnedAt INTEGER DEFAULT NULL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_23_24, MIGRATION_24_25)
}'''
assert old in s, "DbMigrations.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ DbMigrations.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
s = open(f).read()
old = "    version = 24,"
new = "    version = 25,"
assert old in s, "MuwanChatDb.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ MuwanChatDb.kt patched (version 24 → 25)")

f = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
s = open(f).read()
old = '''    val isRemoved: Boolean = false,
    val removedByUsername: String? = null
)'''
new = '''    val isRemoved: Boolean = false,
    val removedByUsername: String? = null,
    val isPinned: Boolean = false
)'''
assert old in s, "ChatApi.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ ChatApi.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
s = open(f).read()

old1 = '''            val hidden = hiddenMap[it.room_id]
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
            } else if (it.lastTime > hidden.hiddenAt) {'''
new1 = '''            val hidden = hiddenMap[it.room_id]
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
                        onlineCount = it.onlineCount,
                        // Pin sirf local/per-device hai, server ko iska pata nahi —
                        // resync mein purana local pin state carry-forward karo,
                        // warna REPLACE upsert usko chupchap null kar deta
                        pinnedAt = local?.pinnedAt
                    )
                )
            } else if (it.lastTime > hidden.hiddenAt) {'''
assert old1 in s, "ChatRepository.kt: pattern1 not found"
s = s.replace(old1, new1, 1)

old2 = '''                db.hiddenConversationDao().unhide(it.room_id)
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
            }'''
new2 = '''                db.hiddenConversationDao().unhide(it.room_id)
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
                        onlineCount = it.onlineCount,
                        pinnedAt = local?.pinnedAt
                    )
                )
            }'''
assert old2 in s, "ChatRepository.kt: pattern2 not found"
s = s.replace(old2, new2, 1)

old3 = '''    suspend fun clearUnread(db: MuwanChatDb, roomId: String) {
        db.conversationDao().clearUnread(roomId)
    }
}'''
new3 = '''    suspend fun clearUnread(db: MuwanChatDb, roomId: String) {
        db.conversationDao().clearUnread(roomId)
    }

    // ── Pin chat (ConversationListScreen ke multi-select se call hota hai) ──
    // WhatsApp jaisa max 3 pinned chats — is limit ke andar hi in roomIds
    // mein se jo abhi unpinned hain unko pin karta hai (already-pinned wale
    // untouched rehte hain, unka purana pin-order bana rehta hai).
    // Return false = limit cross ho rahi thi, kuch bhi pin nahi hua (all-or-nothing).
    suspend fun pinChats(db: MuwanChatDb, roomIds: Set<String>): Boolean {
        val all = db.conversationDao().getAll().associateBy { it.roomId }
        val alreadyPinnedCount = all.values.count { it.pinnedAt != null }
        val newlyToPin = roomIds.filter { all[it]?.pinnedAt == null }

        if (alreadyPinnedCount + newlyToPin.size > MAX_PINNED_CHATS) return false

        // Har naye pin ko alag-alag millisecond timestamp taaki order stable
        // rahe (sabse recent pin sabse upar dikhega)
        var t = System.currentTimeMillis()
        for (roomId in newlyToPin) {
            db.conversationDao().setPinned(roomId, t)
            t += 1
        }
        return true
    }

    suspend fun unpinChats(db: MuwanChatDb, roomIds: Set<String>) {
        for (roomId in roomIds) {
            db.conversationDao().setPinned(roomId, null)
        }
    }

    const val MAX_PINNED_CHATS = 3
}'''
assert old3 in s, "ChatRepository.kt: pattern3 not found"
s = s.replace(old3, new3, 1)
open(f, "w").write(s)
print("✅ ChatRepository.kt patched")

f = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
s = open(f).read()

old1 = '''import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*'''
new1 = '''import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*'''
assert old1 in s, "ConversationListScreen.kt: pattern1 not found"
s = s.replace(old1, new1, 1)

old2 = '''import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController'''
new2 = '''import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavController'''
assert old2 in s, "ConversationListScreen.kt: pattern2 not found"
s = s.replace(old2, new2, 1)

old3 = '''                isRemoved = e.isRemoved,
                removedByUsername = e.removedByUsername
            )'''
new3 = '''                isRemoved = e.isRemoved,
                removedByUsername = e.removedByUsername,
                isPinned = e.pinnedAt != null
            )'''
assert old3 in s, "ConversationListScreen.kt: pattern3 not found"
s = s.replace(old3, new3, 1)

old4 = '''    fun toggleSelection(roomId: String) {
        selectedRoomIds = if (selectedRoomIds.contains(roomId)) {
            selectedRoomIds - roomId
        } else {
            selectedRoomIds + roomId
        }
        if (selectedRoomIds.isEmpty()) isSelectionMode = false
    }

    suspend fun reloadConversations(token: String) {'''
new4 = '''    fun toggleSelection(roomId: String) {
        selectedRoomIds = if (selectedRoomIds.contains(roomId)) {
            selectedRoomIds - roomId
        } else {
            selectedRoomIds + roomId
        }
        if (selectedRoomIds.isEmpty()) isSelectionMode = false
    }

    // Selection mein sab already pinned hai to yeh unpin karega, warna pin
    // (max 3 tak — limit cross hone par Toast dikha ke kuch bhi change nahi karta)
    fun togglePinSelected() {
        val pinnedByRoom = conversationEntities.associate { it.roomId to (it.pinnedAt != null) }
        val allSelectedPinned = selectedRoomIds.isNotEmpty() && selectedRoomIds.all { pinnedByRoom[it] == true }
        scope.launch {
            if (allSelectedPinned) {
                ChatRepository.unpinChats(db, selectedRoomIds)
            } else {
                val ok = ChatRepository.pinChats(db, selectedRoomIds)
                if (!ok) {
                    Toast.makeText(
                        context,
                        "You can pin up to ${ChatRepository.MAX_PINNED_CHATS} chats only",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
            }
            exitSelectionMode()
        }
    }

    suspend fun reloadConversations(token: String) {'''
assert old4 in s, "ConversationListScreen.kt: pattern4 not found"
s = s.replace(old4, new4, 1)

old5 = '''                    IconButton(
                        onClick = { if (selectedRoomIds.isNotEmpty()) showDeleteConfirm = true },
                        enabled = selectedRoomIds.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = if (selectedRoomIds.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF555577)
                        )
                    }'''
new5 = '''                    IconButton(
                        onClick = { togglePinSelected() },
                        enabled = selectedRoomIds.isNotEmpty()
                    ) {
                        val allSelectedPinned = selectedRoomIds.isNotEmpty() &&
                            selectedRoomIds.all { id -> conversationEntities.find { it.roomId == id }?.pinnedAt != null }
                        Icon(
                            if (allSelectedPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (allSelectedPinned) "Unpin" else "Pin",
                            tint = if (selectedRoomIds.isNotEmpty()) DarkAccent else Color(0xFF555577)
                        )
                    }
                    IconButton(
                        onClick = { if (selectedRoomIds.isNotEmpty()) showDeleteConfirm = true },
                        enabled = selectedRoomIds.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = if (selectedRoomIds.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF555577)
                        )
                    }'''
assert old5 in s, "ConversationListScreen.kt: pattern5 not found"
s = s.replace(old5, new5, 1)

old6 = '''        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatConvTime(conv.lastTime), color = Color(0xFF666688), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))'''
new6 = '''        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            // Pin badge — offline/date se pehle, sirf pinned chats par dikhta hai
            if (conv.isPinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = Color(0xFF666688),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(formatConvTime(conv.lastTime), color = Color(0xFF666688), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))'''
assert old6 in s, "ConversationListScreen.kt: pattern6 not found"
s = s.replace(old6, new6, 1)

open(f, "w").write(s)
print("✅ ConversationListScreen.kt patched")

print("\n🎉 Pin chat feature patched successfully. MuwanChatDb.kt version 25 hai (Room migration wired) — build karke test kar lena.")
