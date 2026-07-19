path = "app/src/main/java/com/muwan/muwanchat/screens/UserSearchScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

changes = 0

old_import = 'import com.muwan.muwanchat.network.UserItem\n'
new_import = 'import com.muwan.muwanchat.network.UserItem\nimport com.muwan.muwanchat.util.friendlyErrorMessage\n'
if old_import in content:
    content = content.replace(old_import, new_import, 1)
    changes += 1
else:
    print("WARN: import anchor not found")

old_err = 'errorMsg = e.message ?: "Error"'
new_err = 'errorMsg = friendlyErrorMessage(e)'
count = content.count(old_err)
if count == 0:
    print("WARN: error pattern not found")
else:
    content = content.replace(old_err, new_err)
    changes += count

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print(f"Done. {changes} replacements made in {path}")
