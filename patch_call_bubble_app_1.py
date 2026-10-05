#!/usr/bin/env python3
# Call bubble -- APP patch PART 1/3. Project root se run karo (jahan app/ folder hai).
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

# ───────────────────────── 1) naya helper file ─────────────────────────
NEW_FILE = BASE + "data/CallMessage.kt"
NEW_SRC = r'''package com.muwan.muwanchat.data

import org.json.JSONObject

// CALL_BUBBLE_PATCH
// Chat mein "call" type message ka content JSON hota hai:
// { callId, kind: "voice"|"video", status: ringing|answered|ended|declined|missed, duration: seconds }
data class CallInfo(
    val callId: String,
    val kind: String,
    val status: String,
    val duration: Int
)

fun parseCallInfo(raw: String?): CallInfo {
    return try {
        val j = JSONObject(raw ?: "")
        CallInfo(
            callId = j.optString("callId"),
            kind = j.optString("kind", "voice").ifBlank { "voice" },
            status = j.optString("status", "ended").ifBlank { "ended" },
            duration = j.optInt("duration", 0)
        )
    } catch (_: Exception) {
        CallInfo(callId = "", kind = "voice", status = "ended", duration = 0)
    }
}

// Chat list preview / reply preview ke liye chhota friendly text
fun callPreviewText(raw: String?): String {
    val info = parseCallInfo(raw)
    return when (info.status) {
        "missed" -> "📞 Missed call"
        "declined" -> "📞 Call declined"
        else -> if (info.kind == "video") "📹 Video call" else "📞 Voice call"
    }
}
'''
if os.path.exists(NEW_FILE) and "CALL_BUBBLE_PATCH" in open(NEW_FILE, encoding="utf-8").read():
    print("SKIP  data/CallMessage.kt (already exists)")
else:
    os.makedirs(os.path.dirname(NEW_FILE), exist_ok=True)
    open(NEW_FILE, "w", encoding="utf-8").write(NEW_SRC)
    print("OK    data/CallMessage.kt (new)")

# ───────────────────────── 2) ChatMessage.kt ─────────────────────────
patch("screens/ChatMessage.kt", [
    ("    val mentions: List<String> = emptyList()\n)\n\nfun formatMessageTime",
     "    val mentions: List<String> = emptyList(),\n    val callInfo: String? = null // CALL_BUBBLE_PATCH: type == \"call\" ka raw JSON\n)\n\nfun formatMessageTime", 1),
    ('    text = if (type == "text" || type == "system") content else "",\n',
     '    text = when (type) {\n        "text", "system" -> content\n        "call" -> com.muwan.muwanchat.data.callPreviewText(content)\n        else -> ""\n    },\n', 2),
    ('    mediaUrl = if (type != "text" && type != "system") content else null,\n',
     '    mediaUrl = if (type != "text" && type != "system" && type != "call") content else null,\n', 2),
    ("    isForwarded = is_forwarded,\n    mentions = mentions ?: emptyList()\n)",
     "    isForwarded = is_forwarded,\n    mentions = mentions ?: emptyList(),\n    callInfo = if (type == \"call\") content else null\n)", 1),
    ('    mentions = mentions?.split(",")?.filter { it.isNotBlank() } ?: emptyList()\n)',
     '    mentions = mentions?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),\n    callInfo = if (type == "call") content else null\n)', 1),
])

# ───────────────────────── 3) ChatRepository.kt (preview text) ─────────────────────────
patch("data/ChatRepository.kt", [
    ('            "document" -> "📄 ${fileName ?: "Document"}"\n            else -> content\n',
     '            "document" -> "📄 ${fileName ?: "Document"}"\n            "call" -> callPreviewText(content) // CALL_BUBBLE_PATCH\n            else -> content\n', 1),
    ('            "document" -> "📄 ${latest.fileName ?: "Document"}"\n            else -> latest.content\n',
     '            "document" -> "📄 ${latest.fileName ?: "Document"}"\n            "call" -> callPreviewText(latest.content) // CALL_BUBBLE_PATCH\n            else -> latest.content\n', 1),
])

# ───────────────────────── 4) AppSocketManager.kt ─────────────────────────
patch("data/AppSocketManager.kt", [
    ("    data class CallBusyReceived(val callId: String) : SocketEvent()\n",
     "    data class CallBusyReceived(val callId: String) : SocketEvent()\n\n"
     "    // CALL_BUBBLE_PATCH: call bubble ka naya status (ringing -> missed/declined/ended + duration)\n"
     "    data class CallMessageUpdate(\n"
     "        val id: String,\n"
     "        val roomId: String,\n"
     "        val senderUid: String,\n"
     "        val receiverUid: String,\n"
     "        val content: String,\n"
     "        val createdAt: String\n"
     "    ) : SocketEvent()\n", 1),
    ('            s.on("ice_candidate") { args ->\n',
     '            s.on("call_msg_update") { args ->\n'
     '                val json = args.getOrNull(0) as? JSONObject ?: return@on\n'
     '                _events.tryEmit(\n'
     '                    SocketEvent.CallMessageUpdate(\n'
     '                        id = json.optString("id"),\n'
     '                        roomId = json.optString("room_id"),\n'
     '                        senderUid = json.optString("sender_uid"),\n'
     '                        receiverUid = json.optString("receiver_uid"),\n'
     '                        content = json.optString("content"),\n'
     '                        createdAt = json.optString("created_at")\n'
     '                    )\n'
     '                )\n'
     '            }\n\n'
     '            s.on("ice_candidate") { args ->\n', 1),
])

print("Part 1 done. Ab Part 2 chalao.")
