path = "src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path, "r") as f:
    content = f.read()

old_screen_tail = '''    object JoinGroup       : Screen("join/{code}") {
        fun createRoute(code: String) = "join/$code"
    }
    object Settings        : Screen("settings")
}'''
new_screen_tail = '''    object JoinGroup       : Screen("join/{code}") {
        fun createRoute(code: String) = "join/$code"
    }
    object Settings        : Screen("settings")
    object AcceptedUsers   : Screen("accepted_users")
}'''
assert old_screen_tail in content
content = content.replace(old_screen_tail, new_screen_tail)

old_composable_tail = '''        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}'''
new_composable_tail = '''        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.AcceptedUsers.route) { AcceptedUsersScreen(navController) }
    }
}'''
assert old_composable_tail in content
content = content.replace(old_composable_tail, new_composable_tail)

with open(path, "w") as f:
    f.write(content)
print("NavGraph.kt patched: AcceptedUsers route added")
