path = "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
with open(path) as f:
    content = f.read()

old = "    @Query(\"DELETE FROM conversations WHERE roomId = :roomId\")\n    suspend fun deleteByRoom(roomId: String)"
new = '''    @Query("DELETE FROM conversations WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("UPDATE conversations SET isRemoved = 1, removedByUsername = :removedByUsername WHERE roomId = :roomId")
    suspend fun markRemoved(roomId: String, removedByUsername: String)'''

assert old in content
content = content.replace(old, new, 1)
with open(path, "w") as f:
    f.write(content)
print("ConversationDao.kt patched: markRemoved() added")
