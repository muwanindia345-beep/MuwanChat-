path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path) as f:
    content = f.read()

old = "                if (isAdmin) {\n                    // Add Members"
new = "                // Admin hamesha add kar sakta hai; regular member sirf\n                // tab jab group owner ne \"Allow members to add members\" ON kiya ho.\n                val canAddMembers = isAdmin || group?.membersCanAdd == true\n                if (canAddMembers) {\n                    // Add Members"

assert old in content, "Add Members block marker not found"
content = content.replace(old, new, 1)

with open(path, "w") as f:
    f.write(content)

print("GroupInfoScreen.kt patched: Add Members button ab membersCanAdd setting bhi respect karega")
