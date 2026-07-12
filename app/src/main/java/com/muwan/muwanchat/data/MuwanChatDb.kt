package com.muwan.muwanchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, HiddenConversationEntity::class, MyProfileEntity::class, ChatWallpaperEntity::class, DeletedMessageEntity::class],
    version = 13,
    exportSchema = false
)
abstract class MuwanChatDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun hiddenConversationDao(): HiddenConversationDao
    abstract fun myProfileDao(): MyProfileDao
    abstract fun chatWallpaperDao(): ChatWallpaperDao
    abstract fun deletedMessageDao(): DeletedMessageDao

    companion object {
        // Har logged-in user ka apna alag local DB file — taaki device pe
        // account switch/logout-login karne pe purane user ka data naye
        // user ki screen pe kabhi na dikhe (per-uid isolation)
        private val instances = ConcurrentHashMap<String, MuwanChatDb>()

        fun get(context: Context, uid: String): MuwanChatDb {
            return instances.getOrPut(uid) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db_$uid"
                )
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}
