import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupSettingsScreen.kt"
content = open(path, encoding="utf-8").read()

# --- Edit 1: imports ---
old1 = "import com.muwan.muwanchat.data.ChatRepository\n"
new1 = "import com.google.gson.Gson\nimport com.muwan.muwanchat.data.ChatRepository\nimport com.muwan.muwanchat.data.GroupInfoCacheEntity\n"
if old1 not in content:
    sys.exit("Edit 1 anchor not found -- file already patched ya kuch change ho gaya hai.")
content = content.replace(old1, new1, 1)

# --- Edit 2: db + gson refs ---
old2 = "    val clipboard: ClipboardManager = LocalClipboardManager.current\n\n    var myUid by remember"
new2 = "    val clipboard: ClipboardManager = LocalClipboardManager.current\n    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }\n    val gson = remember { Gson() }\n\n    var myUid by remember"
if old2 not in content:
    sys.exit("Edit 2 anchor not found.")
content = content.replace(old2, new2, 1)

# --- Edit 3: refreshGroup writes to cache + initial load reads cache first ---
old3 = '''    suspend fun refreshGroup() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) group = res.body()?.group
    }

    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        isLoading = true
        try {
            refreshGroup()
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val muteRes = RetrofitClient.chatApi.getMuteStatus("Bearer $token", groupId)
                if (muteRes.isSuccessful) muted = muteRes.body()?.muted ?: false
            }
        } catch (_: Exception) {
        }
        isLoading = false
    }'''

new3 = '''    suspend fun refreshGroup() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) {
            val fresh = res.body()?.group
            if (fresh != null) {
                group = fresh
                db.groupInfoCacheDao().upsert(GroupInfoCacheEntity(groupId = groupId, json = gson.toJson(fresh)))
            }
        }
    }

    // Local cache se turant dikhao (offline-first) -- GroupInfoScreen jaisa hi cache reuse hota hai
    LaunchedEffect(groupId) {
        val cached = db.groupInfoCacheDao().get(groupId)
        if (cached != null) {
            try {
                group = gson.fromJson(cached.json, GroupData::class.java)
                isLoading = false
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        if (group == null) isLoading = true
        try {
            refreshGroup()
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val muteRes = RetrofitClient.chatApi.getMuteStatus("Bearer $token", groupId)
                if (muteRes.isSuccessful) muted = muteRes.body()?.muted ?: false
            }
        } catch (_: Exception) {
        }
        isLoading = false
    }'''

if old3 not in content:
    sys.exit("Edit 3 anchor not found.")
content = content.replace(old3, new3, 1)

open(path, "w", encoding="utf-8").write(content)
print("GroupSettingsScreen.kt patched successfully: 3 edits applied.")
