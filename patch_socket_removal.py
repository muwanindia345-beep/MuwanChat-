path = "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
with open(path) as f:
    content = f.read()

changes = 0

old = '''    data class GroupRemoved(
        val roomId: String,
        val selfLeave: Boolean,
        val removedByUsername: String?
    ) : SocketEvent()
}'''
new = '''    data class GroupRemoved(
        val roomId: String,
        val selfLeave: Boolean,
        val removedByUsername: String?
    ) : SocketEvent()

    // Dusre user ne "Accepted Users" screen se humein remove kiya --
    // uid us user ka hai jisne remove kiya. roomId consumer khud banata
    // hai (sorted myUid+uid), backend sirf uid bhejta hai.
    data class ConnectionRemoved(val uid: String) : SocketEvent()
}'''
if old in content:
    content = content.replace(old, new, 1)
    changes += 1
else:
    print("WARN: GroupRemoved class anchor not found")

old_listener = '''            s.on("group_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.GroupRemoved(
                        roomId = json.optString("roomId"),
                        selfLeave = json.optBoolean("selfLeave", true),
                        removedByUsername = if (json.isNull("removedByUsername")) null else json.optString("removedByUsername")
                    )
                )
            }

            s.connect()'''
new_listener = '''            s.on("group_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.GroupRemoved(
                        roomId = json.optString("roomId"),
                        selfLeave = json.optBoolean("selfLeave", true),
                        removedByUsername = if (json.isNull("removedByUsername")) null else json.optString("removedByUsername")
                    )
                )
            }

            s.on("connection_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.ConnectionRemoved(uid = json.optString("uid"))
                )
            }

            s.connect()'''
if old_listener in content:
    content = content.replace(old_listener, new_listener, 1)
    changes += 1
else:
    print("WARN: group_removed listener anchor not found")

with open(path, "w") as f:
    f.write(content)
print(f"AppSocketManager.kt changes: {changes}")
