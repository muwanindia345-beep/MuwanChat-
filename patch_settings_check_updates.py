import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/SettingsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

anchor = '''        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Logout'''

new_block = '''        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3.5 Check Updates
        var hasUpdate by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val info = com.muwan.muwanchat.data.UpdateManager.checkForUpdate(context)
            hasUpdate = info != null && com.muwan.muwanchat.data.UpdateManager.hasUnseenUpdate(context, info)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.CheckUpdates.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(Icons.Filled.SystemUpdate, contentDescription = "Check Updates", tint = DarkAccent)
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .background(Color(0xFFFF3B30), shape = androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Check Updates", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Logout'''

count = content.count(anchor)
if count == 0:
    print("ERROR: anchor not found — file already changed hui hogi, manual check karo.")
    sys.exit(1)
if count > 1:
    print(f"ERROR: anchor {count} baar mila, unique nahi hai — manual check karo.")
    sys.exit(1)

content = content.replace(anchor, new_block, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ SettingsScreen.kt me 'Check Updates' row add ho gaya.")
