import io, sys

BASE = "app/src/main/java/com/muwan/muwanchat/"

def read(p):
    with io.open(p, "r", encoding="utf-8", newline="") as f:
        return f.read().replace("\r\n", "\n")

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

def replace_once(s, old, new, label):
    if s.count(old) != 1:
        print("ERROR [%s]: anchor %d baar mila (1 chahiye)" % (label, s.count(old)))
        sys.exit(1)
    return s.replace(old, new)

# ---------------- MessageDao.kt: getById ----------------
p = BASE + "data/MessageDao.kt"
s = read(p)
if "suspend fun getById(" in s:
    print("SKIP MessageDao.kt: pehle se patch ho chuka hai")
else:
    s = replace_once(
        s,
        "    suspend fun insert(message: MessageEntity)\n",
        "    suspend fun insert(message: MessageEntity)\n"
        "\n"
        "    @Query(\"SELECT * FROM messages WHERE id = :id LIMIT 1\")\n"
        "    suspend fun getById(id: String): MessageEntity?\n",
        "dao insert")
    write(p, s)
    print("OK MessageDao.kt")

# ---------------- ChatRepository.kt: recordPushMessage ----------------
p = BASE + "data/ChatRepository.kt"
s = read(p)
if "recordPushMessage" in s:
    print("SKIP ChatRepository.kt: pehle se patch ho chuka hai")
else:
    s = replace_once(
        s,
        "import com.google.gson.Gson\n",
        "import com.google.gson.Gson\nimport kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock\n",
        "repo imports")
    s = replace_once(
        s,
        "    private val gson = Gson()\n",
        "    private val gson = Gson()\n"
        "\n"
        "    // FCM push se aaye message (app background / socket dead) ko Room mein save karne ke liye.\n"
        "    // Mutex isliye ki push aur socket ek saath aayein to duplicate/double-unread na ho.\n"
        "    private val pushStoreMutex = Mutex()\n"
        "\n"
        "    suspend fun recordPushMessage(\n"
        "        db: MuwanChatDb,\n"
        "        id: String,\n"
        "        roomId: String,\n"
        "        senderUid: String,\n"
        "        content: String,\n"
        "        type: String,\n"
        "        createdAt: String,\n"
        "        myUid: String,\n"
        "        fileName: String?,\n"
        "        mimeType: String?,\n"
        "        replyToId: String?,\n"
        "        isForwarded: Boolean,\n"
        "        mentions: List<String>\n"
        "    ) {\n"
        "        pushStoreMutex.withLock {\n"
        "            // Socket se ya sync se pehle hi aa chuka hai -- dobara kuch mat karo\n"
        "            if (db.messageDao().getById(id) != null) return\n"
        "            // Conversation local mein nahi hai (naya/hidden chat) to yahan na banao,\n"
        "            // list sync aane par sahi metadata ke saath apne aap aa jaayegi\n"
        "            if (db.conversationDao().getByRoomId(roomId) == null) return\n"
        "            recordMessage(\n"
        "                db = db,\n"
        "                id = id,\n"
        "                roomId = roomId,\n"
        "                senderUid = senderUid,\n"
        "                receiverUid = myUid,\n"
        "                content = content,\n"
        "                type = type,\n"
        "                createdAt = createdAt,\n"
        "                myUid = myUid,\n"
        "                fileName = fileName,\n"
        "                mimeType = mimeType,\n"
        "                replyToId = replyToId,\n"
        "                isForwarded = isForwarded,\n"
        "                mentions = mentions\n"
        "            )\n"
        "        }\n"
        "    }\n",
        "repo gson")
    write(p, s)
    print("OK ChatRepository.kt")

# ---------------- MuwanFirebaseService.kt ----------------
p = BASE + "MuwanFirebaseService.kt"
s = read(p)
if "storePushedMessage" in s:
    print("SKIP MuwanFirebaseService.kt: pehle se patch ho chuka hai")
else:
    s = replace_once(
        s,
        "        CoroutineScope(Dispatchers.IO).launch {\n"
        "            val notificationsEnabled = try {\n",
        "        CoroutineScope(Dispatchers.IO).launch {\n"
        "            // Pehle message local DB mein (notification band ho tab bhi save hona chahiye)\n"
        "            storePushedMessage(message.data)\n"
        "\n"
        "            val notificationsEnabled = try {\n",
        "service launch")
    s = replace_once(
        s,
        "    private fun showNotification(title: String, body: String) {\n",
        "    // Backend (FCM_DATA_ONLY=true) push mein poora message bhejta hai -- use seedha Room mein\n"
        "    // save kar do, taaki app kholte hi chat mein pehle se maujood ho. Koi bhi gadbad ho\n"
        "    // to chupchap skip (notification phir bhi dikhega, message sync se aa jaayega).\n"
        "    private suspend fun storePushedMessage(data: Map<String, String>) {\n"
        "        try {\n"
        "            val msgId = data[\"msg_id\"] ?: return\n"
        "            val roomId = data[\"room_id\"] ?: return\n"
        "            val senderUid = data[\"sender_uid\"] ?: return\n"
        "            val content = data[\"content\"] ?: return // bada text: sirf notification, baaki sync se\n"
        "            val myUid = AuthDataStore.getUid(applicationContext).first() ?: return\n"
        "            if (myUid.isBlank() || senderUid == myUid) return\n"
        "            val db = com.muwan.muwanchat.data.MuwanChatDb.get(applicationContext, myUid)\n"
        "            com.muwan.muwanchat.data.ChatRepository.recordPushMessage(\n"
        "                db = db,\n"
        "                id = msgId,\n"
        "                roomId = roomId,\n"
        "                senderUid = senderUid,\n"
        "                content = content,\n"
        "                type = data[\"msg_type\"] ?: \"text\",\n"
        "                createdAt = data[\"created_at\"]?.takeIf { it.isNotBlank() }\n"
        "                    ?: com.muwan.muwanchat.screens.nowIso(),\n"
        "                myUid = myUid,\n"
        "                fileName = data[\"file_name\"],\n"
        "                mimeType = data[\"mime_type\"],\n"
        "                replyToId = data[\"reply_to_id\"],\n"
        "                isForwarded = data[\"is_forwarded\"] == \"1\",\n"
        "                mentions = data[\"mentions\"]?.split(\",\")?.filter { it.isNotBlank() } ?: emptyList()\n"
        "            )\n"
        "        } catch (_: Exception) {\n"
        "        }\n"
        "    }\n"
        "\n"
        "    private fun showNotification(title: String, body: String) {\n",
        "service function")
    write(p, s)
    print("OK MuwanFirebaseService.kt")
