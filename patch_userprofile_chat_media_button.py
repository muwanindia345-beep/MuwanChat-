import sys

# ── File 1: NavGraph.kt — UserProfile route mein optional fromChat flag ──
path1 = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path1, "r", encoding="utf-8") as f:
    content1 = f.read()

old_route = '''    object UserProfile     : Screen("user_profile/{uid}") {
        fun createRoute(uid: String) = "user_profile/$uid"
    }'''
new_route = '''    object UserProfile     : Screen("user_profile/{uid}?fromChat={fromChat}") {
        fun createRoute(uid: String, fromChat: Boolean = false) = "user_profile/$uid?fromChat=$fromChat"
    }'''
if old_route not in content1:
    print("NavGraph.kt: route anchor not found!"); sys.exit(1)
content1 = content1.replace(old_route, new_route, 1)

old_composable = "        composable(Screen.UserProfile.route) { back ->"
if old_composable not in content1:
    print("NavGraph.kt: composable anchor not found!"); sys.exit(1)
# find the block and inject fromChat arg extraction + pass-through
idx = content1.index(old_composable)
end_idx = content1.index("\n        }", idx) + len("\n        }")
old_block = content1[idx:end_idx]
if "UserProfileScreen(" not in old_block:
    print("NavGraph.kt: UserProfileScreen call not found in block!"); sys.exit(1)
new_block = old_block.replace(
    "composable(Screen.UserProfile.route) { back ->",
    '''composable(
            Screen.UserProfile.route,
            arguments = listOf(navArgument("fromChat") { defaultValue = "false" })
        ) { back ->
            val fromChat = back.arguments?.getString("fromChat")?.toBoolean() ?: false'''
).replace(
    "UserProfileScreen(",
    "UserProfileScreen(\n                fromChat = fromChat,",
    1
)
content1 = content1.replace(old_block, new_block, 1)

if "import androidx.navigation.navArgument" not in content1:
    content1 = content1.replace(
        "import androidx.navigation.NavController",
        "import androidx.navigation.NavController\nimport androidx.navigation.navArgument",
        1
    )

with open(path1, "w", encoding="utf-8") as f:
    f.write(content1)
print("NavGraph.kt patched: fromChat flag added to UserProfile route")

# ── File 2: UserProfileScreen.kt — Media button jab fromChat=true ────────
path2 = "app/src/main/java/com/muwan/muwanchat/screens/UserProfileScreen.kt"
with open(path2, "r", encoding="utf-8") as f:
    content2 = f.read()

old_sig = "fun UserProfileScreen(navController: NavController, uid: String) {"
new_sig = "fun UserProfileScreen(navController: NavController, uid: String, fromChat: Boolean = false) {"
if old_sig not in content2:
    print("UserProfileScreen.kt: signature anchor not found!"); sys.exit(1)
content2 = content2.replace(old_sig, new_sig, 1)

old_friends_button = '''                    "friends" -> {
                        Button(
                            onClick = { openChat() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Message", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }'''
new_friends_button = '''                    "friends" -> {
                        if (fromChat) {
                            Button(
                                onClick = { showMediaComingSoon = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Media", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { openChat() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Message", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }'''
if old_friends_button not in content2:
    print("UserProfileScreen.kt: friends-button anchor not found!"); sys.exit(1)
content2 = content2.replace(old_friends_button, new_friends_button, 1)

old_state = '    var status by remember { mutableStateOf("none") }'
new_state = old_state + '\n    var showMediaComingSoon by remember { mutableStateOf(false) }'
if old_state not in content2:
    print("UserProfileScreen.kt: state anchor not found!"); sys.exit(1)
content2 = content2.replace(old_state, new_state, 1)

# ComingSoonDialog hook — function ke last closing brace se pehle add
last_brace_idx = content2.rstrip().rfind("}")
content2 = (
    content2[:last_brace_idx].rstrip() +
    '''

    if (showMediaComingSoon) {
        ComingSoonDialog(feature = "🖼️ Shared Media", onDismiss = { showMediaComingSoon = false })
    }
}
'''
)

with open(path2, "w", encoding="utf-8") as f:
    f.write(content2)
print("UserProfileScreen.kt patched: fromChat=true par Media button + ComingSoon dialog")

# ── File 3: ChatScreen.kt — navigate call mein fromChat=true bhejo ───────
path3 = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
with open(path3, "r", encoding="utf-8") as f:
    content3 = f.read()

old_nav = '''                onAvatarClick = {
                    navController.navigate(
                        com.muwan.muwanchat.navigation.Screen.UserProfile.createRoute(receiverUid)
                    )
                }
            )'''
new_nav = '''                onAvatarClick = {
                    navController.navigate(
                        com.muwan.muwanchat.navigation.Screen.UserProfile.createRoute(receiverUid, fromChat = true)
                    )
                }
            )'''
if old_nav not in content3:
    print("ChatScreen.kt: navigate anchor not found!"); sys.exit(1)
content3 = content3.replace(old_nav, new_nav, 1)

with open(path3, "w", encoding="utf-8") as f:
    f.write(content3)
print("ChatScreen.kt patched: chat se khulne par fromChat=true pass hoga")
