import re

# 1. ConversationDao.kt — add getByUid
f = "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
s = open(f).read()
old = '''    fun observeByRoomId(roomId: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll'''
new = '''    fun observeByRoomId(roomId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll'''
assert old in s, f"pattern not found in {f}"
open(f, "w").write(s.replace(old, new, 1))
print("✅ ConversationDao.kt patched")

# 2. MessageDao.kt — add observeMediaMessages
f = "app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt"
s = open(f).read()
old = '''    suspend fun getMessages(roomId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll'''
new = '''    suspend fun getMessages(roomId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND type = :type AND deleted = 0 ORDER BY createdAt DESC")
    fun observeMediaMessages(roomId: String, type: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll'''
assert old in s, f"pattern not found in {f}"
open(f, "w").write(s.replace(old, new, 1))
print("✅ MessageDao.kt patched")

# 3. MediaScreen.kt — fix broken tabIndicatorOffset
f = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s = open(f).read()
old = '''            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[pagerState.currentPage]),
                    color = DarkAccent
                )
            }'''
new = '''            indicator = { positions ->
                if (pagerState.currentPage < positions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.fillMaxWidth()
                            .wrapContentSize(align = Alignment.BottomStart)
                            .offset(x = positions[pagerState.currentPage].left)
                            .width(positions[pagerState.currentPage].width),
                        color = DarkAccent
                    )
                }
            }'''
assert old in s, f"pattern not found in {f}"
open(f, "w").write(s.replace(old, new, 1))
print("✅ MediaScreen.kt patched")
