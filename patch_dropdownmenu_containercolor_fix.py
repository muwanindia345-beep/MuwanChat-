path = "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = """                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = DarkHeader
                            ) {"""

new = """                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(DarkHeader)
                            ) {"""

if old not in content:
    print("WARN: anchor not found — file already patched or changed manually?")
else:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: containerColor -> Modifier.background(DarkHeader)")
