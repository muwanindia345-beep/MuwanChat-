path = "src/main/java/com/muwan/muwanchat/data/AppSocketManager.kt"
with open(path, "r") as f:
    content = f.read()

old_sealed_tail = """    data class RequestAccepted(
        val roomId: String,
        val uid: String,
        val username: String,
        val avatar: String?
    ) : SocketEvent()
}"""

new_sealed_tail = """    data class RequestAccepted(
        val roomId: String,
        val uid: String,
        val username: String,
        val avatar: String?
    ) : SocketEvent()

    // Naya pending join request (link se ya kisi member ke add karne se) --
    // sirf admins/owner ko emit hota hai (backend side filter). GroupInfoScreen
    // ka red dot isi se live update hota hai, poori list ke liye REST call.
    data class JoinRequest(
        val roomId: String,
        val uid: String,
        val username: String,
        val source: String
    ) : SocketEvent()

    // Kicked ya khud-leave -- selfLeave se client decide karta hai banner
    // dikhana hai ya nahi ("You were removed from group by @Admin").
    data class GroupRemoved(
        val roomId: String,
        val selfLeave: Boolean,
        val removedByUsername: String?
    ) : SocketEvent()
}"""

assert old_sealed_tail in content
content = content.replace(old_sealed_tail, new_sealed_tail)

old_listener_tail = """            s.on("request_accepted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.RequestAccepted(
                        roomId = json.optString("room_id"),
                        uid = json.optString("uid"),
                        username = json.optString("username"),
                        avatar = if (json.isNull("avatar")) null else json.optString("avatar")
                    )
                )
            }

            s.connect()"""

new_listener_tail = """            s.on("request_accepted") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.RequestAccepted(
                        roomId = json.optString("room_id"),
                        uid = json.optString("uid"),
                        username = json.optString("username"),
                        avatar = if (json.isNull("avatar")) null else json.optString("avatar")
                    )
                )
            }

            s.on("join_request") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.JoinRequest(
                        roomId = json.optString("roomId"),
                        uid = json.optString("uid"),
                        username = json.optString("username"),
                        source = json.optString("source")
                    )
                )
            }

            s.on("group_removed") { args ->
                val json = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(
                    SocketEvent.GroupRemoved(
                        roomId = json.optString("roomId"),
                        selfLeave = json.optBoolean("selfLeave", true),
                        removedByUsername = if (json.isNull("removedByUsername")) null else json.optString("removedByUsername")
                    )
                )
            }

            s.connect()"""

assert old_listener_tail in content
content = content.replace(old_listener_tail, new_listener_tail)

with open(path, "w") as f:
    f.write(content)
print("AppSocketManager.kt patched successfully")
