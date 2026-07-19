path = "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
with open(path) as f:
    content = f.read()

changes = 0

# 1. Add groupDeleted flag to GroupRemoved
old_class = '''    data class GroupRemoved(
        val roomId: String,
        val selfLeave: Boolean,
        val removedByUsername: String?
    ) : SocketEvent()'''
new_class = '''    data class GroupRemoved(
        val roomId: String,
        val selfLeave: Boolean,
        val removedByUsername: String?,
        val groupDeleted: Boolean = false
    ) : SocketEvent()'''
if old_class in content:
    content = content.replace(old_class, new_class, 1); changes += 1
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
                )'''
new_listener = '''            s.on("group_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.GroupRemoved(
                        roomId = json.optString("roomId"),
                        selfLeave = json.optBoolean("selfLeave", true),
                        removedByUsername = if (json.isNull("removedByUsername")) null else json.optString("removedByUsername"),
                        groupDeleted = json.optBoolean("groupDeleted", false)
                    )
                )'''
if old_listener in content:
    content = content.replace(old_listener, new_listener, 1); changes += 1
else:
    print("WARN: group_removed listener anchor not found")

# 2. Add ConnectionRemoved-adjacent GroupUpdated event (only add if not already present from earlier session)
if "data class GroupUpdated" not in content:
    old_end = '''    data class ConnectionRemoved(val uid: String) : SocketEvent()
}'''
    new_end = '''    data class ConnectionRemoved(val uid: String) : SocketEvent()

    // Group settings (onlyAdminsCanSend, membersCanAdd, etc.) admin ne change
    // kiye -- poora group object backend bhejta hai lekin yahan sirf roomId
    // nikalte hain, consumer REST se hi fresh GroupData fetch karta hai.
    data class GroupUpdated(val roomId: String) : SocketEvent()
}'''
    if old_end in content:
        content = content.replace(old_end, new_end, 1); changes += 1
    else:
        print("WARN: SocketEvent class end anchor not found (ConnectionRemoved)")
else:
    print("INFO: GroupUpdated already present, skipping")

if "connection_removed" in content and 'group_updated' not in content:
    old_listener2 = '''            s.on("connection_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.ConnectionRemoved(uid = json.optString("uid"))
                )
            }

            s.connect()'''
    new_listener2 = '''            s.on("connection_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.ConnectionRemoved(uid = json.optString("uid"))
                )
            }

            s.on("group_updated") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val groupId = json.optJSONObject("group")?.optString("id")
                if (!groupId.isNullOrBlank()) {
                    _events.tryEmit(SocketEvent.GroupUpdated(groupId))
                }
            }

            s.connect()'''
    if old_listener2 in content:
        content = content.replace(old_listener2, new_listener2, 1); changes += 1
    else:
        print("WARN: connection_removed listener anchor not found")

with open(path, "w") as f:
    f.write(content)
print(f"AppSocketManager.kt changes: {changes}")
