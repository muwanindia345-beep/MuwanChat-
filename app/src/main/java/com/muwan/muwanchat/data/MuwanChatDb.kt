package com.muwan.muwanchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, HiddenConversationEntity::class, MyProfileEntity::class],
    version = 7,
    exportSchema = false
)
abstract class MuwanChatDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun hiddenConversationDao(): HiddenConversationDao
    abstract fun myProfileDao(): MyProfileDao

    companion object {
        @Volatile
        private var INSTANCE: MuwanChatDb? = null

        fun get(context: Context): MuwanChatDb {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db"
                )
                    // Trial project hai, koi migration data important nahi — fresh schema pe reset kar do
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
