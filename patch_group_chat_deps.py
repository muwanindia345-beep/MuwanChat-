path1 = "app/src/main/java/com/muwan/muwanchat/network/ChatApi.kt"
with open(path1, "r", encoding="utf-8") as f:
    c1 = f.read()

anchor1 = "interface ChatApi {"
newdata = """data class GroupMemberProfile(
    val uid: String,
    val username: String,
    val avatar: String?,
    val isAdmin: Boolean,
    val isOwner: Boolean
)

data class GroupData(
    val id: String,
    val name: String,
    val avatar: String?,
    val owner: String,
    val admins: List<String>,
    val members: List<String>,
    val createdAt: String,
    val memberProfiles: List<GroupMemberProfile> = emptyList()
)

data class GroupResponse(
    val group: GroupData?
)

data class CreateGroupRequest(
    val name: String,
    val avatar: String?,
    val memberUids: List<String>
)

data class CreateGroupResponse(
    val success: Boolean,
    val group: GroupData?
)

interface ChatApi {
    @POST("groups/create")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): Response<CreateGroupResponse>

    @GET("groups/{roomId}")
    suspend fun getGroup(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String
    ): Response<GroupResponse>
"""

if anchor1 not in c1:
    print("[FAIL] ChatApi.kt anchor not found")
    exit(1)
c1 = c1.replace(anchor1, newdata, 1)
with open(path1, "w", encoding="utf-8") as f:
    f.write(c1)
print("[OK] ChatApi.kt patched — createGroup + getGroup added")

# ---------- AppSocketManager.kt ----------
path2 = "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
with open(path2, "r", encoding="utf-8") as f:
    c2 = f.read()

anchor2 = """    fun sendStopTyping(receiverUid: String) {
        val json = JSONObject().apply { put("receiver_uid", receiverUid) }
        socket?.emit("stop_typing", json)
    }"""

new2 = anchor2 + """

    // ── Group versions — receiver_uid ki jagah room_id bhejte hain, backend
    // socket/chat.js "group_" prefix se already detect karta hai ──
    fun sendGroupMessage(
        id: String,
        roomId: String,
        content: String,
        type: String = "text",
        fileName: String? = null,
        mimeType: String? = null,
        replyToId: String? = null,
        onAck: (Boolean) -> Unit = {}
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onAck(false)
            return
        }
        val json = JSONObject().apply {
            put("id", id)
            put("room_id", roomId)
            put("content", content)
            put("type", type)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("reply_to_id", replyToId)
        }
        s.emit("send_message", arrayOf(json), Ack { args ->
            val res = args.getOrNull(0) as? JSONObject
            onAck(res?.optBoolean("success", false) ?: false)
        })
    }

    fun sendGroupTyping(roomId: String) {
        val json = JSONObject().apply { put("room_id", roomId) }
        socket?.emit("typing", json)
    }

    fun sendGroupStopTyping(roomId: String) {
        val json = JSONObject().apply { put("room_id", roomId) }
        socket?.emit("stop_typing", json)
    }"""

if anchor2 not in c2:
    print("[FAIL] AppSocketManager.kt anchor not found")
    exit(1)
c2 = c2.replace(anchor2, new2, 1)
with open(path2, "w", encoding="utf-8") as f:
    f.write(c2)
print("[OK] AppSocketManager.kt patched — sendGroupMessage/Typing/StopTyping added")

# ---------- NavGraph.kt ----------
path3 = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path3, "r", encoding="utf-8") as f:
    c3 = f.read()

anchor3 = """    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
}"""

new3 = """    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
    object GroupChat       : Screen("group_chat/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) =
            "group_chat/$groupId/${android.net.Uri.encode(groupName)}"
    }
}"""

if anchor3 not in c3:
    print("[FAIL] NavGraph.kt Screen anchor not found")
    exit(1)
c3 = c3.replace(anchor3, new3, 1)

anchor4 = """        composable(Screen.Wallpaper.route) { back ->
            WallpaperScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
    }
}"""

new4 = """        composable(Screen.Wallpaper.route) { back ->
            WallpaperScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.GroupChat.route) { back ->
            GroupChatScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: "",
                groupName = back.arguments?.getString("groupName") ?: "New Group",
                groupAvatar = null
            )
        }
    }
}"""

if anchor4 not in c3:
    print("[FAIL] NavGraph.kt composable anchor not found")
    exit(1)
c3 = c3.replace(anchor4, new4, 1)

with open(path3, "w", encoding="utf-8") as f:
    f.write(c3)
print("[OK] NavGraph.kt patched — Screen.GroupChat + composable registered")
