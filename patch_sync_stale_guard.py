p = "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt"
s = open(p, encoding="utf-8").read()

old = '''    suspend fun syncConversations(db: MuwanChatDb, items: List<ConversationItem>) {
        // "Delete chat" (for me) ka hidden record — jab tak backend ka lastTime
        // hiddenAt se naya na ho, us room ko wapas list me nahi daalna
        val hiddenMap = db.hiddenConversationDao().getAll().associateBy { it.roomId }

        val toUpsert = mutableListOf<ConversationEntity>()
        for (it in items) {
            val hidden = hiddenMap[it.room_id]
            if (hidden == null) {'''

new = '''    suspend fun syncConversations(db: MuwanChatDb, items: List<ConversationItem>) {
        // "Delete chat" (for me) ka hidden record — jab tak backend ka lastTime
        // hiddenAt se naya na ho, us room ko wapas list me nahi daalna
        val hiddenMap = db.hiddenConversationDao().getAll().associateBy { it.roomId }

        // Message delete (for me / for everyone) ke turant baad local preview
        // "Say hi!" / blank set hota hai with lastTime = abhi ka waqt. Agar
        // usi second backend se ek full resync aa jaaye (jisko delete ka pata
        // abhi tak nahi chala — ya "delete for me" jo server ko pata hi nahi
        // chalta), to server ka PURANA lastMessage/lastTime local ke naye
        // (correct) state ko overwrite kar deta tha — isi wajah se delete
        // hote hi purana text wapas dikhta tha. Fix: server ka data sirf tabhi
        // apply karo jab uska lastTime local se naya (ya barabar) ho — kabhi
        // bhi ek NAYE local state ko ek PURANE server snapshot se overwrite
        // mat karo.
        val localMap = db.conversationDao().getAll().associateBy { it.roomId }

        val toUpsert = mutableListOf<ConversationEntity>()
        for (it in items) {
            val local = localMap[it.room_id]
            if (local != null && it.lastTime < local.lastTime) {
                // Server ka snapshot local se purana hai — abhi sync mat karo,
                // local (jyada recent) state hi sahi hai
                continue
            }
            val hidden = hiddenMap[it.room_id]
            if (hidden == null) {'''

assert old in s, "anchor not found in ChatRepository.kt"
s = s.replace(old, new)
open(p, "w", encoding="utf-8").write(s)
print("ChatRepository.kt: stale-server-overwrite guard added to syncConversations")
