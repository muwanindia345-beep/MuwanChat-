# -*- coding: utf-8 -*-
# Naya route AddMembersForChannel (channelId + channelName path args) --
# yeh EXISTING Screen.AddFromContacts route ko chhuta nahi (jo bina args
# ke reuse hota hai CreateGroupScreen/GroupInfoScreen se) taaki koi purana
# navigate() call na toote. Same AddFromContactsScreen composable function
# ko dono routes call karte hain, bas dusra route args pass karta hai.

path = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_screen = '''    object CreateGroup     : Screen("create_group")
    object CreateChannel   : Screen("create_channel")
    object AddFromContacts : Screen("add_from_contacts")'''

new_screen = '''    object CreateGroup     : Screen("create_group")
    object CreateChannel   : Screen("create_channel")
    object AddFromContacts : Screen("add_from_contacts")
    object AddMembersForChannel : Screen("add_members_for_channel/{channelId}/{channelName}") {
        fun createRoute(channelId: String, channelName: String) =
            "add_members_for_channel/$channelId/${android.net.Uri.encode(channelName)}"
    }'''

n = src.count(old_screen)
if n != 1:
    raise SystemExit(f"[FAIL] Screen object block: found {n} matches (expected 1)")
src = src.replace(old_screen, new_screen, 1)

old_composable = '''        composable(Screen.CreateChannel.route) { CreateChannelScreen(navController) }
        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }'''

new_composable = '''        composable(Screen.CreateChannel.route) { CreateChannelScreen(navController) }
        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }
        composable(Screen.AddMembersForChannel.route) { back ->
            AddFromContactsScreen(
                navController = navController,
                channelId = back.arguments?.getString("channelId"),
                channelName = android.net.Uri.decode(back.arguments?.getString("channelName") ?: "")
            )
        }'''

n2 = src.count(old_composable)
if n2 != 1:
    raise SystemExit(f"[FAIL] composable registration block: found {n2} matches (expected 1)")
src = src.replace(old_composable, new_composable, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] AddMembersForChannel route registered (reuses AddFromContactsScreen)")
