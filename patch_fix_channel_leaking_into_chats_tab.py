# -*- coding: utf-8 -*-
# Root cause: ChatRepository.recordMessage() kisi bhi room ka pehla message
# aane par, agar local Chats-tab conversation entity exist nahi karti, to
# khud bana deta hai -- ye 16+ jagah GroupChatScreen.kt me call hota hai,
# aur kisi ko bhi pata nahi ki room channel hai ya normal group. Channel ka
# pehla message (hamara naya hardcoded welcome system message) isi wajah se
# use Chats tab me daal deta hai, jabki backend ka /chat/conversations
# already isChannel groups exclude karta hai (ye sirf local-DB side issue
# hai).
#
# Fix: har baar channel screen khulti hai, group data fetch hone ke turant
# baad, agar group.isChannel true hai to us roomId ki local conversation
# entity delete kar do (deleteByRoom() already exist karta hai DAO me).
# Ye self-healing hai -- jo channel abhi galti se Chats list me dikh raha
# hai, use ek baar khol dene se hi wo list se hat jaayega, koi migration
# ya naya param thread karne ki zaroorat nahi.

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''                db.conversationDao().updateAdminSettings(groupId, g.onlyAdminsCanSend, g.admins.contains(myUid))
                db.groupInfoCacheDao().upsert(GroupInfoCacheEntity(groupId = groupId, json = Gson().toJson(g)))'''

new = '''                db.conversationDao().updateAdminSettings(groupId, g.onlyAdminsCanSend, g.admins.contains(myUid))
                db.groupInfoCacheDao().upsert(GroupInfoCacheEntity(groupId = groupId, json = Gson().toJson(g)))
                if (g.isChannel) {
                    // Broadcast channel kabhi Chats tab me nahi dikhna chahiye --
                    // agar recordMessage() ne pehle message par galti se yahan
                    // ek row bana di thi, use yahin hata do.
                    db.conversationDao().deleteByRoom(groupId)
                }'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] group-fetch anchor: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Opening a channel now self-heals it out of the Chats tab local DB")
