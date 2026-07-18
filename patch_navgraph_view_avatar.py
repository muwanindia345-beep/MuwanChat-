import sys
path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_screen = '''    object Forward          : Screen("forward")
}'''
new_screen = '''    object Forward          : Screen("forward")
    object ViewAvatar       : Screen("view_avatar")
}'''
if old_screen not in content:
    print("Screen anchor not found!"); sys.exit(1)
content = content.replace(old_screen, new_screen, 1)

old_composable = '''        composable(Screen.Forward.route) { ForwardScreen(navController) }'''
new_composable = '''        composable(Screen.Forward.route) { ForwardScreen(navController) }
        composable(Screen.ViewAvatar.route) { ViewAvatarScreen(navController) }'''
if old_composable not in content:
    print("Composable anchor not found!"); sys.exit(1)
content = content.replace(old_composable, new_composable, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("NavGraph.kt patched: view_avatar route registered")
