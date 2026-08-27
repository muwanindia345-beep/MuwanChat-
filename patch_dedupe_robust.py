import re

def dedupe_block(text, query_snippet, fun_signature):
    """Keep only the first occurrence of a @Query+fun block, remove rest (with its blank line)."""
    block_pattern = re.compile(
        r'\n?[ \t]*@Query\("' + re.escape(query_snippet) + r'"\)\n[ \t]*' + re.escape(fun_signature) + r'\n?'
    )
    matches = list(block_pattern.finditer(text))
    if len(matches) <= 1:
        return text, len(matches)
    new_text = text[:matches[1].start()]
    last_end = matches[1].end()
    for m in matches[2:]:
        new_text += text[last_end:m.start()]
        last_end = m.end()
    new_text += text[last_end:]
    return new_text, len(matches)

p1 = "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
s1 = open(p1, encoding="utf-8").read()
s1_fixed, n1 = dedupe_block(
    s1,
    "SELECT * FROM conversations WHERE uid = :uid LIMIT 1",
    "suspend fun getByUid(uid: String): ConversationEntity?"
)
if n1 == 0:
    anchor = '    suspend fun getByRoomId(roomId: String): ConversationEntity?\n'
    if anchor in s1_fixed and "fun getByUid" not in s1_fixed:
        s1_fixed = s1_fixed.replace(
            anchor,
            anchor + '\n    @Query("SELECT * FROM conversations WHERE uid = :uid LIMIT 1")\n    suspend fun getByUid(uid: String): ConversationEntity?\n'
        )
        print("ConversationDao.kt: getByUid was missing entirely, added fresh")
    else:
        print("ConversationDao.kt: getByUid state unclear, please check manually")
elif n1 == 1:
    print("ConversationDao.kt: getByUid already single occurrence, no change needed")
else:
    print(f"ConversationDao.kt: removed {n1 - 1} duplicate getByUid definition(s)")
open(p1, "w", encoding="utf-8").write(s1_fixed)

p2 = "app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt"
s2 = open(p2, encoding="utf-8").read()
s2_fixed, n2 = dedupe_block(
    s2,
    "SELECT * FROM messages WHERE roomId = :roomId AND type = :type AND deleted = 0 ORDER BY createdAt DESC",
    "fun observeMediaMessages(roomId: String, type: String): Flow<List<MessageEntity>>"
)
if n2 == 0:
    anchor2 = '    @Query("DELETE FROM messages")\n    suspend fun clearAll()\n'
    if anchor2 in s2_fixed and "fun observeMediaMessages" not in s2_fixed:
        s2_fixed = s2_fixed.replace(
            anchor2,
            '    @Query("SELECT * FROM messages WHERE roomId = :roomId AND type = :type AND deleted = 0 ORDER BY createdAt DESC")\n    fun observeMediaMessages(roomId: String, type: String): Flow<List<MessageEntity>>\n\n' + anchor2
        )
        print("MessageDao.kt: observeMediaMessages was missing entirely, added fresh")
    else:
        print("MessageDao.kt: observeMediaMessages state unclear, please check manually")
elif n2 == 1:
    print("MessageDao.kt: observeMediaMessages already single occurrence, no change needed")
else:
    print(f"MessageDao.kt: removed {n2 - 1} duplicate observeMediaMessages definition(s)")
open(p2, "w", encoding="utf-8").write(s2_fixed)

p3 = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s3 = open(p3, encoding="utf-8").read()
old3 = "Modifier.tabIndicatorOffset(positions[pagerState.currentPage])"
new3 = "TabRowDefaults.tabIndicatorOffset(positions[pagerState.currentPage])"
if old3 in s3:
    s3 = s3.replace(old3, new3)
    open(p3, "w", encoding="utf-8").write(s3)
    print("MediaScreen.kt: tabIndicatorOffset call fixed (old buggy version found and fixed)")
elif "tabIndicatorOffset" not in s3:
    print("MediaScreen.kt: no tabIndicatorOffset call found (probably using manual offset/width version) — OK, nothing to do")
else:
    print("MediaScreen.kt: tabIndicatorOffset present but pattern didn't match exactly, please paste the exact line for manual fix")

print("DONE")
