#!/usr/bin/env python3
# Call bubble -- APP patch PART 2/3. Project root se run karo (jahan app/ folder hai).
import sys, os

BASE = "app/src/main/java/com/muwan/muwanchat/"

def patch(rel, pairs):
    path = BASE + rel
    if not os.path.exists(path):
        sys.exit("ERROR: %s nahi mila -- project root se run karo" % path)
    s = open(path, encoding="utf-8").read()
    if "CALL_BUBBLE_PATCH" in s:
        print("SKIP  %s (already patched)" % rel)
        return
    for old, new, expected in pairs:
        n = s.count(old)
        if n != expected:
            sys.exit("ERROR: %s mein anchor %d baar mila (%d chahiye):\n%s" % (rel, n, expected, old[:140]))
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)
    print("OK    %s" % rel)

# ───────────────────────── 5) NavGraph.kt (global: kahin bhi ho, status Room mein update) ─────────────────────────
patch("navigation/NavGraph.kt", [
    ("                com.muwan.muwanchat.calling.CallForegroundService.dismiss(context)\n            }\n        }\n    }\n",
     "                com.muwan.muwanchat.calling.CallForegroundService.dismiss(context)\n"
     "            } else if (event is com.muwan.muwanchat.data.SocketEvent.CallMessageUpdate) {\n"
     "                // CALL_BUBBLE_PATCH: bubble ka status live update (ringing -> missed/declined/ended)\n"
     "                try {\n"
     "                    val myUid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {\n"
     "                        com.muwan.muwanchat.data.AuthDataStore.getUidBlocking(context)\n"
     "                    }\n"
     "                    if (myUid.isNotBlank()) {\n"
     "                        val db = com.muwan.muwanchat.data.MuwanChatDb.get(context, myUid)\n"
     "                        val old = db.messageDao().getById(event.id)\n"
     "                        db.messageDao().insert(\n"
     "                            com.muwan.muwanchat.data.MessageEntity(\n"
     "                                id = event.id,\n"
     "                                roomId = event.roomId,\n"
     "                                senderUid = event.senderUid,\n"
     "                                receiverUid = event.receiverUid,\n"
     "                                content = event.content,\n"
     "                                type = \"call\",\n"
     "                                seen = old?.seen ?: 0,\n"
     "                                createdAt = event.createdAt,\n"
     "                                status = old?.status ?: \"SENT\",\n"
     "                                deleted = old?.deleted ?: false\n"
     "                            )\n"
     "                        )\n"
     "                        com.muwan.muwanchat.data.ChatRepository.refreshLastMessagePreview(db, event.roomId)\n"
     "                    }\n"
     "                } catch (_: Exception) {}\n"
     "            }\n        }\n    }\n", 1),
])

# ───────────────────────── 6) ChatScreen.kt (tap -> wapas call) ─────────────────────────
patch("screens/ChatScreen.kt", [
    ("                        onSwipeReply = { replyTo = it },\n",
     "                        onSwipeReply = { replyTo = it },\n"
     "                        onCallTap = { // CALL_BUBBLE_PATCH\n"
     "                            navController.navigate(\n"
     "                                com.muwan.muwanchat.navigation.Screen.Call.createRoute(\n"
     "                                    uid = receiverUid,\n"
     "                                    username = receiverUsername,\n"
     "                                    callType = \"voice\",\n"
     "                                    isIncoming = false\n"
     "                                )\n"
     "                            )\n"
     "                        },\n", 1),
    ("                                if (!it.isDeleted) showReactionPicker = true\n",
     "                                if (!it.isDeleted && it.type != \"call\") showReactionPicker = true // CALL_BUBBLE_PATCH: call bubble pe reaction nahi\n", 1),
    ("val canPin = selectedMessageIds.isNotEmpty() &&\n                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.isDeleted == false }",
     "val canPin = selectedMessageIds.isNotEmpty() &&\n                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.let { m -> !m.isDeleted && m.type != \"call\" } == true }", 1),
    ("val canForward = selectedMessageIds.isNotEmpty() &&\n                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.isDeleted == false }",
     "val canForward = selectedMessageIds.isNotEmpty() &&\n                    selectedMessageIds.all { id -> messages.firstOrNull { it.id == id }?.let { m -> !m.isDeleted && m.type != \"call\" } == true }", 1),
])

print("Part 2 done. Ab Part 3 chalao.")
