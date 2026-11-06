package com.muwan.muwanchat.data

// GUEST_ACT -- guest ke liye Pin + Forward
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.muwan.muwanchat.network.PinnedMessageInfo
import com.muwan.muwanchat.screens.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

// Pins sirf is phone me (prefs) + "pin" E2EE envelope se contact ko bhi dikhte hain
object GuestPins {
    val changed = MutableStateFlow(0)

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("guest_pins", Context.MODE_PRIVATE)

    private fun key(c: Context, roomId: String) = GuestStore.activeGuestId(c) + "|" + roomId

    fun load(c: Context, roomId: String): List<PinnedMessageInfo> {
        val o = try { JSONObject(prefs(c).getString(key(c, roomId), "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val out = ArrayList<PinnedMessageInfo>()
        for (k in o.keys()) out.add(PinnedMessageInfo(k, o.optString(k)))
        return out.sortedBy { it.pinned_at }
    }

    fun set(c: Context, roomId: String, mid: String, on: Boolean) {
        val k = key(c, roomId)
        val o = try { JSONObject(prefs(c).getString(k, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        if (on) o.put(mid, nowIso()) else o.remove(mid)
        prefs(c).edit().putString(k, o.toString()).apply()
        changed.update { it + 1 }
    }

    suspend fun setAndSend(c: Context, roomId: String, toUid: String, mid: String, on: Boolean): Boolean {
        set(c, roomId, mid, on)
        return GuestChat.sendKind(
            c, toUid, "pn-" + UUID.randomUUID(), "pin",
            JSONObject().put("m", mid).put("on", if (on) 1 else 0).put("ts", System.currentTimeMillis())
        )
    }
}

object GuestForward {
    // Text: "Forwarded" label ke saath. Media: local copy se dobara encrypt karke bhejta hai.
    suspend fun forward(
        context: Context, myUid: String, targetUid: String, targetRoomId: String, targetName: String,
        type: String, text: String, mediaUrl: String?, fileName: String?, mime: String?
    ) {
        val app = context.applicationContext
        val db = MuwanChatDb.get(app, myUid)
        if (type == "text") {
            if (text.isBlank()) return
            val id = UUID.randomUUID().toString()
            ChatRepository.recordMessage(
                db = db, id = id, roomId = targetRoomId, senderUid = myUid, receiverUid = targetUid,
                content = text, type = "text", createdAt = nowIso(), myUid = myUid,
                otherUsername = targetName, status = "PENDING", isForwarded = true
            )
            val body = JSONObject().put("x", text).put("f", 1).put("ts", System.currentTimeMillis())
            val ok = GuestChat.sendKind(app, targetUid, id, "msg", body)
            db.messageDao().updateStatus(id, if (ok) "SENT" else "FAILED")
        } else {
            val f = LocalMedia.localFile(app, mediaUrl)
            if (f == null) {
                withContext(Dispatchers.Main) { Toast.makeText(app, "Media not downloaded yet", Toast.LENGTH_SHORT).show() }
                return
            }
            GuestMedia.send(
                app, Uri.fromFile(f), type, targetRoomId, myUid, targetUid, targetName,
                skipCompression = true, mimeOverride = mime, nameOverride = fileName
            )
        }
    }
}
