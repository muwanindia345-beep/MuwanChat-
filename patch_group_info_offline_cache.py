import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
content = open(path, encoding="utf-8").read()

# --- Edit 1: imports ---
old1 = "import com.muwan.muwanchat.data.ChatRepository\n"
new1 = "import com.google.gson.Gson\nimport com.muwan.muwanchat.data.ChatRepository\nimport com.muwan.muwanchat.data.GroupInfoCacheEntity\n"
if old1 not in content:
    sys.exit("Edit 1 anchor not found — file already patched ya kuch change ho gaya hai.")
content = content.replace(old1, new1, 1)

# --- Edit 2: db + gson refs ---
old2 = "    val scope = rememberCoroutineScope()\n\n    var myUid by remember"
new2 = "    val scope = rememberCoroutineScope()\n    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }\n    val gson = remember { Gson() }\n\n    var myUid by remember"
if old2 not in content:
    sys.exit("Edit 2 anchor not found.")
content = content.replace(old2, new2, 1)

# --- Edit 3: refreshGroup + initial load ---
old3 = '''        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) {
            group = res.body()?.group
        } else {
            errorMsg = "Group load nahi ho paya"
        }
    }

    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        isLoading = true
        try {
            refreshGroup()
        } catch (e: Exception) {
            errorMsg = e.message ?: "Network error"
        }
        isLoading = false
    }'''

new3 = '''        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) {
            val fresh = res.body()?.group
            if (fresh != null) {
                group = fresh
                db.groupInfoCacheDao().upsert(GroupInfoCacheEntity(groupId = groupId, json = gson.toJson(fresh)))
            }
        } else if (group == null) {
            errorMsg = "Group load nahi ho paya"
        }
    }

    // Local cache se turant dikhao (offline-first) -- background me refreshGroup() fresh data laata hai
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
        } catch (e: Exception) {
            if (group == null) errorMsg = e.message ?: "Network error"
        }
        isLoading = false
    }'''

if old3 not in content:
    sys.exit("Edit 3 anchor not found.")
content = content.replace(old3, new3, 1)

open(path, "w", encoding="utf-8").write(content)
print("GroupInfoScreen.kt patched successfully: 3 edits applied.")
