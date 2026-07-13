path = "src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path, "r") as f:
    content = f.read()

old = "showRedDot = g.pendingRequests.isNotEmpty(),"
new = "showRedDot = (g.pendingRequests ?: emptyList()).isNotEmpty(),"

assert old in content, "anchor not found"
content = content.replace(old, new)

with open(path, "w") as f:
    f.write(content)
print("GroupInfoScreen.kt null-safety patch applied")
