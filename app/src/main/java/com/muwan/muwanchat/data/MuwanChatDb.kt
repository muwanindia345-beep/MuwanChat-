package com.muwan.muwanchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, HiddenConversationEntity::class, MyProfileEntity::class, ChatWallpaperEntity::class, DeletedMessageEntity::class, ChatBubbleThemeEntity::class, ChatRequestEntity::class, CachedUserProfileEntity::class, GroupInfoCacheEntity::class, AcceptedUsersCacheEntity::class],
    version = 25,
    exportSchema = true
)
abstract class MuwanChatDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun hiddenConversationDao(): HiddenConversationDao
    abstract fun myProfileDao(): MyProfileDao
    abstract fun chatWallpaperDao(): ChatWallpaperDao
    abstract fun deletedMessageDao(): DeletedMessageDao
    abstract fun chatBubbleThemeDao(): ChatBubbleThemeDao
    abstract fun chatRequestDao(): ChatRequestDao
    abstract fun cachedUserProfileDao(): CachedUserProfileDao
    abstract fun groupInfoCacheDao(): GroupInfoCacheDao
    abstract fun acceptedUsersCacheDao(): AcceptedUsersCacheDao

    companion object {
        private val instances = ConcurrentHashMap<String, MuwanChatDb>()

        fun get(context: Context, uid: String): MuwanChatDb {
            return instances.getOrPut(uid) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db_$uid"
                )
                    .addMigrations(*DbMigrations.ALL)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}
