#!/data/data/com.termux/files/usr/bin/bash
# patch_bubbletheme_db.sh
# Registers ChatBubbleThemeEntity + ChatBubbleThemeDao in the Room database
# and bumps the DB version (16 -> 17). fallbackToDestructiveMigration()
# is already used, so this is safe — no manual migration needed.
# Run from project root (MuwanChat--main folder):
#   bash patch_bubbletheme_db.sh

set -e

DB_FILE="app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"

if [ ! -f "$DB_FILE" ]; then
    echo "ERROR: $DB_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/data/MuwanChatDb.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = """@Database(
    entities = [MessageEntity::class, ConversationEntity::class, HiddenConversationEntity::class, MyProfileEntity::class, ChatWallpaperEntity::class, DeletedMessageEntity::class],
    version = 16,
    exportSchema = false
)
abstract class MuwanChatDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun hiddenConversationDao(): HiddenConversationDao
    abstract fun myProfileDao(): MyProfileDao
    abstract fun chatWallpaperDao(): ChatWallpaperDao
    abstract fun deletedMessageDao(): DeletedMessageDao"""

new = """@Database(
    entities = [MessageEntity::class, ConversationEntity::class, HiddenConversationEntity::class, MyProfileEntity::class, ChatWallpaperEntity::class, DeletedMessageEntity::class, ChatBubbleThemeEntity::class],
    version = 17,
    exportSchema = false
)
abstract class MuwanChatDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun hiddenConversationDao(): HiddenConversationDao
    abstract fun myProfileDao(): MyProfileDao
    abstract fun chatWallpaperDao(): ChatWallpaperDao
    abstract fun deletedMessageDao(): DeletedMessageDao
    abstract fun chatBubbleThemeDao(): ChatBubbleThemeDao"""

if new in content:
    print("SKIP: MuwanChatDb.kt already patched")
elif old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: MuwanChatDb.kt -> ChatBubbleThemeEntity registered, version 16 -> 17")
else:
    print("WARN: anchor not found — DB version/entities may already differ. Check manually.")
PYEOF

echo "Done."
