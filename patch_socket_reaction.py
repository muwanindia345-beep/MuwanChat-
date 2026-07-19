path = "app/src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
with open(path) as f:
    content = f.read()

changes = 0

old_class = '''    data class ReactionUpdate(val id: String, val roomId: String, val reactionsJson: String) : SocketEvent()'''
new_class = '''    data class ReactionUpdate(
        val id: String,
        val roomId: String,
        val reactionsJson: String,
        val reactorUid: String = "",
        val emoji: String = "",
        val added: Boolean = true
    ) : SocketEvent()'''
if old_class in content:
    content = content.replace(old_class, new_class, 1)
    changes += 1
else:
    print("WARN: ReactionUpdate class anchor not found")

old_listener = '''            s.on("reaction_update") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val reactionsJson = json.optJSONArray("reactions")?.toString() ?: "[]"
                _events.tryEmit(
                    SocketEvent.ReactionUpdate(json.optString("id"), json.optString("room_id"), reactionsJson)
                )
            }'''
new_listener = '''            s.on("reaction_update") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                val reactionsJson = json.optJSONArray("reactions")?.toString() ?: "[]"
                _events.tryEmit(
                    SocketEvent.ReactionUpdate(
                        id = json.optString("id"),
                        roomId = json.optString("room_id"),
                        reactionsJson = reactionsJson,
                        reactorUid = json.optString("uid"),
                        emoji = json.optString("emoji"),
                        added = json.optBoolean("added", true)
                    )
                )
            }'''
if old_listener in content:
    content = content.replace(old_listener, new_listener, 1)
    changes += 1
else:
    print("WARN: reaction_update listener anchor not found")

with open(path, "w") as f:
    f.write(content)
print(f"AppSocketManager.kt changes: {changes}")
