path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path) as f:
    content = f.read()

content = content.replace(
    "import androidx.navigation.compose.rememberNavController",
    "import androidx.navigation.compose.rememberNavController\nimport androidx.navigation.navDeepLink"
)

screen_marker = "    object Settings        : Screen(\"settings\")"
new_screen = '''    object JoinGroup       : Screen("join/{code}") {
        fun createRoute(code: String) = "join/$code"
    }
    object Settings        : Screen("settings")'''
assert screen_marker in content
content = content.replace(screen_marker, new_screen, 1)

composable_marker = '        composable(Screen.Settings.route) { SettingsScreen(navController) }'
new_composable = '''        composable(
            Screen.JoinGroup.route,
            deepLinks = listOf(navDeepLink { uriPattern = "muwanchat://join/{code}" })
        ) { back ->
            JoinGroupScreen(
                navController = navController,
                code = back.arguments?.getString("code") ?: ""
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }'''
assert composable_marker in content
content = content.replace(composable_marker, new_composable, 1)

with open(path, "w") as f:
    f.write(content)

print("NavGraph.kt patched: JoinGroup screen + deep link route added")
