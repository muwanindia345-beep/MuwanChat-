# -*- coding: utf-8 -*-
# BroadcastChannelsScreen ke dropdown se "Create Channel" ka Coming Soon
# popup hata ke ab seedha naye CreateChannelScreen pe navigate karta hai
# (Coming Soon ab CreateChannelScreen ke "Confirm" button pe hai, pichle
# patch me). Requires: patch_broadcast_channel_menu.py aur
# patch_broadcast_channel_create_channel_coming_soon.py pehle se lage hue
# (yeh dono is patch se pehle wale conversation-turn ke patches hain).

path = "app/src/main/java/com/muwan/muwanchat/screens/BroadcastChannelsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_state = '''    var showMenu by remember { mutableStateOf(false) }
    var showCreateComingSoon by remember { mutableStateOf(false) }'''

new_state = '''    var showMenu by remember { mutableStateOf(false) }'''

n = src.count(old_state)
if n != 1:
    raise SystemExit(f"[FAIL] state declarations: found {n} matches (expected 1)")
src = src.replace(old_state, new_state, 1)

old_item = '''                        DropdownMenuItem(
                            text = { Text("Create Channel", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showCreateComingSoon = true
                            }
                        )'''

new_item = '''                        DropdownMenuItem(
                            text = { Text("Create Channel", color = Color.White) },
                            onClick = {
                                showMenu = false
                                navController.navigate(com.muwan.muwanchat.navigation.Screen.CreateChannel.route)
                            }
                        )'''

n2 = src.count(old_item)
if n2 != 1:
    raise SystemExit(f"[FAIL] dropdown item: found {n2} matches (expected 1)")
src = src.replace(old_item, new_item, 1)

old_dialog_block = '''            if (showCreateComingSoon) {
                ComingSoonDialog(
                    feature = "Create Channel",
                    onDismiss = { showCreateComingSoon = false }
                )
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {'''

new_dialog_block = '''            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {'''

n3 = src.count(old_dialog_block)
if n3 != 1:
    raise SystemExit(f"[FAIL] coming-soon dialog block removal: found {n3} matches (expected 1)")
src = src.replace(old_dialog_block, new_dialog_block, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Dropdown 'Create Channel' now navigates to CreateChannelScreen (popup removed from here)")
