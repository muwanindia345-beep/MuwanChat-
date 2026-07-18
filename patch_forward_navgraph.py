import sys
path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()
old_screen = '    object CheckUpdates    : Screen("check_updates")\n}'
new_screen = '    object CheckUpdates    : Screen("check_updates")\n    object Forward          : Screen("forward")\n}'
if old_screen not in content:
    print("Screen sealed class anchor not found!"); sys.exit(1)
content = content.replace(old_screen, new_screen, 1)
old_composable = '        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }'
new_composable = '        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }\n        composable(Screen.Forward.route) { ForwardScreen(navController) }'
if old_composable not in content:
    print("Composable anchor not found!"); sys.exit(1)
content = content.replace(old_composable, new_composable, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("NavGraph.kt patched: 'forward' route added")
