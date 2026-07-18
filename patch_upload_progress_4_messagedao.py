import io
path = "app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """    // Apna bheja message ka status update karne ke liye (PENDING -> SENT/FAILED)
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)"""
new = """    // Apna bheja message ka status update karne ke liye (PENDING -> SENT/FAILED)
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    // Media message ka local content:// uri, upload complete hone ke baad asal
    // server URL se replace karta hai — status ek saath hi update ho jata hai
    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateMediaContent(id: String, content: String, status: String)"""

assert content.count(old) == 1, "match failed"
content = content.replace(old, new)
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched MessageDao.kt")
