import io
path = "app/src/main/java/com/muwan/muwanchat/screens/CreateGroupScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old = """                                    CreateGroupRequest(
                                        name = groupName.ifBlank { "New Group" },
                                        avatar = avatarBase64,
                                        memberUids = selectedMembers.map { it.uid }
                                    )"""
new = """                                    CreateGroupRequest(
                                        name = groupName.ifBlank { "New Group" },
                                        avatar = avatarBase64,
                                        description = description.ifBlank { null },
                                        memberUids = selectedMembers.map { it.uid }
                                    )"""

c = content.count(old)
if c != 1:
    print(f"MATCH FAILED: found {c}, expected 1")
    # Debug helper -- grep karke dikhao kahan CreateGroupRequest( use hua hai
    import re
    for m in re.finditer(r"CreateGroupRequest\(", content):
        line_no = content.count("\n", 0, m.start()) + 1
        print(f"  found CreateGroupRequest( at line {line_no}")
else:
    content = content.replace(old, new)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched CreateGroupScreen.kt with description field")
