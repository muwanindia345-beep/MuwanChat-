# -*- coding: utf-8 -*-
# Screen.CreateChannel route add karta hai (CreateGroup jaisa hi simple
# no-arg route) aur NavHost me CreateChannelScreen composable register
# karta hai, CreateGroup ke exact bagal me.

path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_screen = '''    object CreateGroup     : Screen("create_group")
    object AddFromContacts : Screen("add_from_contacts")'''

new_screen = '''    object CreateGroup     : Screen("create_group")
    object CreateChannel   : Screen("create_channel")
    object AddFromContacts : Screen("add_from_contacts")'''

n = src.count(old_screen)
if n != 1:
    raise SystemExit(f"[FAIL] Screen object block: found {n} matches (expected 1)")
src = src.replace(old_screen, new_screen, 1)

old_composable = '''        composable(Screen.CreateGroup.route) { CreateGroupScreen(navController) }
        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }'''

new_composable = '''        composable(Screen.CreateGroup.route) { CreateGroupScreen(navController) }
        composable(Screen.CreateChannel.route) { CreateChannelScreen(navController) }
        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }'''

n2 = src.count(old_composable)
if n2 != 1:
    raise SystemExit(f"[FAIL] composable registration block: found {n2} matches (expected 1)")
src = src.replace(old_composable, new_composable, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Screen.CreateChannel route + NavHost composable registered")
