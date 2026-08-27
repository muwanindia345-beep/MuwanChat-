#!/data/data/com.termux/files/usr/bin/bash
# patch_db_migration_fix.sh
# ROOT CAUSE FIX: Message Theme (and any future local-only setting)
# was resetting after every app update — NOT because of uninstall,
# NOT a backend issue. `.fallbackToDestructiveMigration()` in
# MuwanChatDb.kt silently WIPES the entire local database whenever
# the DB version number changes (which happens with most feature
# updates that add a new table). This sets up real Room Migrations
# infrastructure so future updates preserve local data.
# Does NOT reset anything right now (DB version stays the same, 22).
# Run from project root (MuwanChat--main folder):
#   bash patch_db_migration_fix.sh

set -e

GRADLE_FILE="app/build.gradle.kts"
DB_FILE="app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"

for f in "$GRADLE_FILE" "$DB_FILE"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

# ── 1. Create DbMigrations.kt ──
cat > app/src/main/java/com/muwan/muwanchat/data/DbMigrations.kt << 'EOF'
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
EOF
echo "Created: DbMigrations.kt"

# ── 2. Patch build.gradle.kts to export Room schemas ──
python3 - << 'PYEOF'
path = "app/build.gradle.kts"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = '''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}'''
new = '''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

ksp {
    // Room ab har version ka schema JSON save karega app/schemas/ mein —
    // isse aage se real migrations likhna safe ho jaata hai (bina isके
    // migration SQL likhna guesswork jaisa hota hai aur crash ka risk rehta hai)
    arg("room.schemaLocation", "$projectDir/schemas")
}'''

if new in content:
    print("SKIP: build.gradle.kts already patched")
elif old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: build.gradle.kts -> Room schema export enabled")
else:
    print("WARN: plugins block anchor not found in build.gradle.kts — check manually")
PYEOF

# ── 3. Patch MuwanChatDb.kt ──
python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

changed = False

old1 = "    version = 22,\n    exportSchema = false\n)"
new1 = "    version = 22,\n    exportSchema = true\n)"
if new1 in content:
    print("SKIP: exportSchema already true")
elif old1 in content:
    content = content.replace(old1, new1, 1); changed = True
else:
    print("WARN: exportSchema anchor not found — check DB version/exportSchema manually")

old2 = '''                Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db_$uid"
                )
                    .fallbackToDestructiveMigration()
                    .build()'''
new2 = '''                Room.databaseBuilder(
                    context.applicationContext,
                    MuwanChatDb::class.java,
                    "muwanchat_db_$uid"
                )
                    .addMigrations(*DbMigrations.ALL)
                    .fallbackToDestructiveMigration()
                    .build()'''
if new2 in content:
    print("SKIP: addMigrations already wired")
elif old2 in content:
    content = content.replace(old2, new2, 1); changed = True
else:
    print("WARN: databaseBuilder anchor not found — check MuwanChatDb.kt manually")

if changed:
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: MuwanChatDb.kt")
PYEOF

echo ""
echo "Verifying brace/paren balance..."
for f in "$GRADLE_FILE" "$DB_FILE" "app/src/main/java/com/muwan/muwanchat/data/DbMigrations.kt"; do
    python3 -c "
content = open('$f').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$f -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"
done
