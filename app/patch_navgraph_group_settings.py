path = "src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path, "r") as f:
    content = f.read()

old_screen_tail = """    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object Settings        : Screen("settings")
}"""

new_screen_tail = """    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object GroupSettings   : Screen("group_settings/{groupId}") {
        fun createRoute(groupId: String) = "group_settings/$groupId"
    }
    object ApprovalRequests: Screen("approval_requests/{groupId}") {
        fun createRoute(groupId: String) = "approval_requests/$groupId"
    }
    object Settings        : Screen("settings")
}"""

assert old_screen_tail in content
content = content.replace(old_screen_tail, new_screen_tail)

old_composable_tail = """        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }"""

new_composable_tail = """        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.GroupSettings.route) { back ->
            GroupSettingsScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.ApprovalRequests.route) { back ->
            ApprovalRequestsScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }"""

assert old_composable_tail in content
content = content.replace(old_composable_tail, new_composable_tail)

with open(path, "w") as f:
    f.write(content)
print("NavGraph.kt patched successfully")
