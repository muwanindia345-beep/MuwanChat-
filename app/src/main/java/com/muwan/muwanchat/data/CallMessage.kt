package com.muwan.muwanchat.data

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
