import sys

# ── File 1: ConversationDao.kt — uid se roomId dhoondhne ke liye ─────────
path1 = "app/src/main/java/com/muwan/muwanchat/data/ConversationDao.kt"
with open(path1, "r", encoding="utf-8") as f:
    c1 = f.read()

old1 = '''    @Query("SELECT * FROM conversations WHERE roomId = :roomId LIMIT 1")
    suspend fun getByRoomId(roomId: String): ConversationEntity?'''
new1 = old1 + '''

    @Query("SELECT * FROM conversations WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): ConversationEntity?'''
if old1 not in c1:
    print("ConversationDao.kt: anchor not found!"); sys.exit(1)
c1 = c1.replace(old1, new1, 1)
with open(path1, "w", encoding="utf-8") as f:
    f.write(c1)
print("ConversationDao.kt patched: getByUid() added")

# ── File 2: MessageDao.kt — type ke hisaab se media messages ─────────────
path2 = "app/src/main/java/com/muwan/muwanchat/data/MessageDao.kt"
with open(path2, "r", encoding="utf-8") as f:
    c2 = f.read()

old2 = '''    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    suspend fun getMessages(roomId: String): List<MessageEntity>'''
new2 = old2 + '''

    // Media screen ke 3 tabs (Photos/Videos/Documents) ke liye — koi limit
    // nahi, chat mein jitne bhi hain sab yahan aayenge, sabse naya sabse upar
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND type = :type AND deleted = 0 ORDER BY createdAt DESC")
    fun observeMediaMessages(roomId: String, type: String): Flow<List<MessageEntity>>'''
if old2 not in c2:
    print("MessageDao.kt: anchor not found!"); sys.exit(1)
c2 = c2.replace(old2, new2, 1)
with open(path2, "w", encoding="utf-8") as f:
    f.write(c2)
print("MessageDao.kt patched: observeMediaMessages() added")

# ── File 3: NavGraph.kt — naya route media/{uid} ─────────────────────────
path3 = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path3, "r", encoding="utf-8") as f:
    c3 = f.read()

old3a = '''    object ViewAvatar       : Screen("view_avatar")
}'''
new3a = '''    object ViewAvatar       : Screen("view_avatar")
    object Media           : Screen("media/{uid}") {
        fun createRoute(uid: String) = "media/$uid"
    }
}'''
if old3a not in c3:
    print("NavGraph.kt: Screen anchor not found!"); sys.exit(1)
c3 = c3.replace(old3a, new3a, 1)

old3b = '''        composable(Screen.ViewAvatar.route) { ViewAvatarScreen(navController) }'''
new3b = old3b + '''
        composable(Screen.Media.route) { back ->
            MediaScreen(
                navController = navController,
                uid = back.arguments?.getString("uid") ?: ""
            )
        }'''
if old3b not in c3:
    print("NavGraph.kt: composable anchor not found!"); sys.exit(1)
c3 = c3.replace(old3b, new3b, 1)

with open(path3, "w", encoding="utf-8") as f:
    f.write(c3)
print("NavGraph.kt patched: media/{uid} route added")

# ── File 4: UserProfileScreen.kt — Media button ab MediaScreen kholega ──
path4 = "app/src/main/java/com/muwan/muwanchat/screens/UserProfileScreen.kt"
with open(path4, "r", encoding="utf-8") as f:
    c4 = f.read()

old4 = '                                onClick = { showMediaComingSoon = true },'
new4 = '''                                onClick = {
                                    navController.navigate(Screen.Media.createRoute(uid))
                                },'''
if old4 not in c4:
    print("UserProfileScreen.kt: button anchor not found!"); sys.exit(1)
c4 = c4.replace(old4, new4, 1)

with open(path4, "w", encoding="utf-8") as f:
    f.write(c4)
print("UserProfileScreen.kt patched: Media button ab MediaScreen navigate karega")
