path = "app/src/main/java/com/muwan/muwanchat/screens/AcceptedUsersScreen.kt"
with open(path) as f:
    content = f.read()

changes = 0

old_imports = "import com.muwan.muwanchat.data.AuthDataStore\n"
new_imports = "import com.muwan.muwanchat.data.AuthDataStore\nimport com.muwan.muwanchat.data.MuwanChatDb\n"
if old_imports in content:
    content = content.replace(old_imports, new_imports, 1)
    changes += 1
else:
    print("WARN: import anchor not found")

old_block = '''                if (res.isSuccessful) {
                    users = users.filter { it.uid != uid }
                    Toast.makeText(context, "User removed", Toast.LENGTH_SHORT).show()
                } else {'''
new_block = '''                if (res.isSuccessful) {
                    users = users.filter { it.uid != uid }
                    val myUid = AuthDataStore.getUidBlocking(context)
                    val roomId = listOf(myUid, uid).sorted().joinToString("_")
                    MuwanChatDb.get(context, myUid).conversationDao().deleteByRoom(roomId)
                    Toast.makeText(context, "User removed", Toast.LENGTH_SHORT).show()
                } else {'''
if old_block in content:
    content = content.replace(old_block, new_block, 1)
    changes += 1
else:
    print("WARN: removeUser block anchor not found")

with open(path, "w") as f:
    f.write(content)
print(f"AcceptedUsersScreen.kt changes: {changes}")
