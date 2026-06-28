package com.muwan.muwanchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class MuwanChatDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: MuwanChatDb? = null

        fun get(context: Context): MuwanChatDb {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
