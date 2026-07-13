import io

def patch(path, replacements, label):
    with io.open(path, "r", encoding="utf-8", newline=None) as f:
        content = f.read()
    for old, new in replacements:
        c = content.count(old)
        if c != 1:
            print(f"MATCH FAILED in {label}: found {c}, expected 1 -> {old[:50]!r}")
            continue
        content = content.replace(old, new)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"Patched {label}")

navgraph_path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
patch(navgraph_path, [
    (
        """    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
}""",
        """    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object Settings        : Screen("settings")
}"""
    ),
    (
        """        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
    }
}""",
        """        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}"""
    ),
], "NavGraph.kt")

convlist_path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
patch(convlist_path, [
    (
        """                        IconButton(onClick = {
                            scope.launch {
                                AppSocketManager.disconnect()
                                AuthDataStore.clearAuth(context)
                                // Room ab clear nahi karte — har account ki apni alag DB
                                // file hai ("muwanchat_db_<uid>"), toh isi account ka
                                // cache safe hai.
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.ConversationList.route) { inclusive = true }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Logout, contentDescription = "Logout", tint = Color(0xFF888888))
                        }""",
        """                        IconButton(onClick = {
                            navController.navigate(Screen.Settings.route)
                        }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings", tint = DarkAccent)
                        }"""
    ),
], "ConversationListScreen.kt")

authds_path = "app/src/main/java/com/muwan/muwanchat/data/AuthDataStore.kt"
patch(authds_path, [
    (
        'private const val LOGIN_TYPE_KEY = "login_type"',
        'private const val LOGIN_TYPE_KEY = "login_type"\n'
        'private const val NOTIFICATIONS_ENABLED_KEY = "notifications_enabled"'
    ),
    (
        """    fun getLoginType(context: Context): Flow<String?> = getString(context, LOGIN_TYPE_KEY)""",
        """    fun getLoginType(context: Context): Flow<String?> = getString(context, LOGIN_TYPE_KEY)

    fun getNotificationsEnabled(context: Context): Flow<Boolean> =
        flow { emit(prefs(context).getBoolean(NOTIFICATIONS_ENABLED_KEY, true)) }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled).apply()
    }"""
    ),
], "AuthDataStore.kt")

print("Done.")
