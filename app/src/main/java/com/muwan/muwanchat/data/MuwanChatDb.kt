package com.muwan.muwanchat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, HiddenConversationEntity::class, MyProfileEntity::class, ChatWallpaperEntity::class, DeletedMessageEntity::class],
    version = 11,
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
        @Volatile
        private var INSTANCE: MuwanChatDb? = null

        // Kis uid ke liye INSTANCE khula hai — agar dusra uid maanga jaaye
        // (account switch same device pe), purana instance band karke naya kholte hain.
        @Volatile
        private var INSTANCE_UID: String? = null

        // Har account ki apni alag DB file ("muwanchat_db_<uid>") — Instagram/WhatsApp
        // jaisa. Isse: (a) ek account ka data dusre account ki screen pe kabhi nahi dikhta,
        // (b) logout/login same account pe wapas aane par Room clear karne ki zaroorat
        // nahi padti — purani file wahi ki wahi milti hai, turant data dikhta hai.
        fun get(context: Context, uid: String): MuwanChatDb {
            val safeUid = uid.ifBlank { "guest" }
            synchronized(this) {
                if (INSTANCE != null && INSTANCE_UID == safeUid) {
                    return INSTANCE!!
                }
                // Account badal gaya — purana instance band karo taaki naya khulne mein
                // conflict na ho
                INSTANCE?.close()
                val fresh = Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db_$safeUid"
                )
                    // Trial project hai, koi migration data important nahi — fresh schema pe reset kar do
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = fresh
                INSTANCE_UID = safeUid
                return fresh
            }
        }
    }
}
