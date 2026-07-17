import sys

path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_screen = '''    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }'''
new_screen = '''    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
    object WallpaperPreview : Screen("wallpaper_preview/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper_preview/$roomId"
    }'''
if old_screen not in content:
    print("Screen anchor not found!")
    sys.exit(1)
content = content.replace(old_screen, new_screen)

old_composable = '''        composable(Screen.Wallpaper.route) { back ->
            WallpaperScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }'''
new_composable = '''        composable(Screen.Wallpaper.route) { back ->
            WallpaperScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.WallpaperPreview.route) { back ->
            WallpaperPreviewScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }'''
if old_composable not in content:
    print("Composable anchor not found!")
    sys.exit(1)
content = content.replace(old_composable, new_composable)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("NavGraph.kt patched: WallpaperPreview route added")
