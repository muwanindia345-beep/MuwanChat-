package com.muwan.muwanchat.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * IMPORTANT — READ BEFORE BUMPING THE DB VERSION IN MuwanChatDb.kt
 *
 * `MuwanChatDb` currently uses `.fallbackToDestructiveMigration()` as a
 * safety net. That means: agar version number badalta hai aur koi matching
 * Migration yaha nahi milti, Room CHUPCHAP poora local database delete
 * karke naya bana deta hai — matlab Wallpaper, Message Theme, cached
 * profiles, sab kuch reset ho jaata hai. Yeh ab tak har feature update
 * ke saath ho raha tha (isliye Message Theme baar-baar "Original" pe
 * wapas chala jaata tha).
 *
 * AB SE: jab bhi koi naya @Entity table add karo YA kisi existing entity
 * mein naya column add karo, saath hi yaha ek Migration bhi add karo,
 * taaki purana data safe rahe. Neeche ek EXAMPLE diya hai (comment out
 * kiya hua hai, kaam nahi karega jab tak use nahi karoge) — isi pattern
 * ko copy karke naya migration likh sakte ho.
 *
 * Naya CREATE TABLE likhte waqt columns ka naam/type EXACTLY wahi hona
 * chahiye jo us @Entity data class mein hai (String -> TEXT,
 * Int/Boolean/Long -> INTEGER), warna Room "Migration didn't properly
 * handle" crash dega app start hote hi.
 *
 * Example (agar kal ek naya "PinnedChatEntity" table add karna ho,
 * version 22 -> 23 karke):
 *
 * val MIGRATION_22_23 = object : Migration(22, 23) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL(
 *             "CREATE TABLE IF NOT EXISTS `pinned_chats` (" +
 *                 "`roomId` TEXT NOT NULL, " +
 *                 "`pinnedAt` TEXT NOT NULL, " +
 *                 "PRIMARY KEY(`roomId`))"
 *         )
 *     }
 * }
 *
 * Phir usko neeche ALL array mein add kar dena:
 *   val ALL: Array<Migration> = arrayOf(MIGRATION_22_23)
 */
object DbMigrations {
    val ALL: Array<Migration> = arrayOf()
}
