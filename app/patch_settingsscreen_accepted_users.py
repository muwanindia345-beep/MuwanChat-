path = "src/main/java/com/muwan/muwanchat/screens/SettingsScreen.kt"
with open(path, "r") as f:
    content = f.read()

old_block = '''        Spacer(modifier = Modifier.height(8.dp))

        // 3. Logout'''

new_block = '''        Spacer(modifier = Modifier.height(8.dp))

        // 3. Accepted Users
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.AcceptedUsers.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PeopleAlt, contentDescription = "Accepted Users", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Accepted Users", color = Color.White, fontSize = 16.sp)
                Text(
                    "Manage connections, remove permanently",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Logout'''

assert old_block in content, "anchor not found"
content = content.replace(old_block, new_block, 1)

with open(path, "w") as f:
    f.write(content)
print("SettingsScreen.kt patched: Accepted Users row added above Logout")
