import os
import sys

target_file = "ConversationListScreen.kt"
file_path = None

for root, dirs, files in os.walk("."):
    if target_file in files:
        file_path = os.path.join(root, target_file)
        break

if not file_path:
    print(f"[-] Error: {target_file} nahi mili! ~/MuwanChat ke andar se run karo.")
    sys.exit(1)

print(f"[+] File mili: {file_path}")

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

old_block = """                        IconButton(onClick = {
                            navController.navigate(Screen.Settings.route)
                        }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings", tint = DarkAccent)
                        }"""

new_block = """                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = DarkAccent)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = DarkHeader
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        navController.navigate(Screen.Settings.route)
                                    }
                                )
                            }
                        }"""

if old_block in content:
    content = content.replace(old_block, new_block)
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("[+] Dropdown menu successfully wire ho gaya!")
elif "showMenu = true" in content:
    print("[*] Patch pehle se hi applied lag raha hai, kuch nahi kiya.")
else:
    print("[-] Old block match nahi hua — file already modified/different hai.")
    print("    Manually check karo ya mujhe current content bhejo.")
    sys.exit(1)
