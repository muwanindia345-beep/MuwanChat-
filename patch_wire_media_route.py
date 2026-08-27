p1 = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
s1 = open(p1, encoding="utf-8").read()

if "object Media " not in s1 and "object Media:" not in s1 and "object Media           :" not in s1:
    old = '''    object UserProfile     : Screen("user_profile/{uid}?fromChat={fromChat}") {
        fun createRoute(uid: String, fromChat: Boolean = false) = "user_profile/$uid?fromChat=$fromChat"
    }'''
    new = old + '''
    object Media           : Screen("media/{uid}") {
        fun createRoute(uid: String) = "media/$uid"
    }'''
    assert old in s1, "UserProfile Screen object not found in NavGraph.kt"
    s1 = s1.replace(old, new)
    print("NavGraph.kt: Screen.Media route object added")
else:
    print("NavGraph.kt: Screen.Media object already present, skipped")

if "composable(Screen.Media.route)" not in s1:
    old2 = "                fromChat = fromChat\n            )\n        }\n        composable(Screen.Chat.route) { back ->"
    new2 = '''                fromChat = fromChat
            )
        }
        composable(Screen.Media.route) { back ->
            MediaScreen(
                navController = navController,
                uid = back.arguments?.getString("uid") ?: ""
            )
        }
        composable(Screen.Chat.route) { back ->'''
    assert old2 in s1, "Chat composable anchor not found in NavGraph.kt"
    s1 = s1.replace(old2, new2)
    print("NavGraph.kt: Media composable route added")
else:
    print("NavGraph.kt: Media composable already present, skipped")

open(p1, "w", encoding="utf-8").write(s1)

p2 = "app/src/main/java/com/muwan/muwanchat/screens/UserProfileScreen.kt"
s2 = open(p2, encoding="utf-8").read()
old3 = "onClick = { showMediaComingSoon = true },"
new3 = "onClick = { navController.navigate(Screen.Media.createRoute(uid)) },"
if old3 in s2:
    s2 = s2.replace(old3, new3)
    open(p2, "w", encoding="utf-8").write(s2)
    print("UserProfileScreen.kt: Media button now navigates to MediaScreen")
elif new3 in s2:
    print("UserProfileScreen.kt: already wired, skipped")
else:
    print("UserProfileScreen.kt: WARNING anchor not found, check manually")

print("DONE")
