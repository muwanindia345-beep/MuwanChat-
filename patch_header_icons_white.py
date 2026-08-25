path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = """                    Text("MuwanChat", color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Row {
                        IconButton(onClick = {
                            navController.navigate(Screen.Profile.createRoute("edit"))
                        }) {
                            Icon(Icons.Filled.Person, contentDescription = "Profile", tint = DarkAccent)
                        }
                        BadgedBox(badge = {
                            if (incomingCount > 0) Badge { Text("$incomingCount") }
                        }) {
                            IconButton(onClick = {
                                incomingCount = 0
                                navController.navigate(Screen.Requests.route)
                            }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Requests", tint = DarkAccent)
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = DarkAccent)
                            }"""

new = """                    Text("MuwanChat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Row {
                        IconButton(onClick = {
                            navController.navigate(Screen.Profile.createRoute("edit"))
                        }) {
                            Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White)
                        }
                        BadgedBox(badge = {
                            if (incomingCount > 0) Badge { Text("$incomingCount") }
                        }) {
                            IconButton(onClick = {
                                incomingCount = 0
                                navController.navigate(Screen.Requests.route)
                            }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Requests", tint = Color.White)
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = Color.White)
                            }"""

if old not in content:
    print("WARN: anchor not found — file already patched or changed manually?")
else:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: Header title + Profile/Notifications/MoreVert icons -> white")
