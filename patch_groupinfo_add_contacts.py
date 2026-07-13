import io
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """                    InfoActionRow(
                        icon = Icons.Filled.PersonAdd,
                        label = "Add Members",
                        onClick = {
                            navController.navigate(Screen.SearchMembersForGroup.route)
                        }
                    )"""
new = """                    InfoActionRow(
                        icon = Icons.Filled.Person,
                        label = "Add from Contacts",
                        onClick = {
                            navController.navigate(Screen.AddFromContacts.route)
                        }
                    )

                    InfoActionRow(
                        icon = Icons.Filled.Search,
                        label = "Search Members",
                        onClick = {
                            navController.navigate(Screen.SearchMembersForGroup.route)
                        }
                    )"""

c = content.count(old)
if c != 1:
    print(f"MATCH FAILED: found {c}, expected 1")
else:
    content = content.replace(old, new)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched GroupInfoScreen.kt: added 'Add from Contacts' option")
