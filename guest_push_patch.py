import sys, os

# Usage (APK repo root se):  python guest_push_patch.py .
# PEHLE: backend pe FIREBASE_SERVICE_ACCOUNT env set ho (auth wale push ke liye jo hai wahi), aur APK mein
# presence patch (GUEST_P2E) lagi ho. Backend mein koi naya patch nahi chahiye -- guest push (guestPush.js) pehle se hai.
#
# GUEST PUSH + NOTIFICATION:
#  * App FCM token ko /guest/push-token par register karti hai (guest home khulte hi, token badalne par, har 7 din).
#  * App band/killed ho aur message aaye: server content-free "wake" push bhejta hai -> app jaagti hai ->
#    mailbox HTTP se laati hai -> decrypt -> Room mein save -> notification mein NAAM + MESSAGE (decrypted) dikhta hai.
#  * App background mein ho par socket zinda ho: socket se aaya message bhi decrypt hokar notification banata hai.
#  * App khuli ho (screen par) to notification nahi aata (list/chat live update hoti hai). Chat kholte hi us chat ki
#    notification hat jaati hai.
#  * Notifications band (Settings) ho to guest ke liye bhi nahi dikhega. Calls ka push abhi ignore (calls Coming Soon).
#  * Notification tap: app khulti hai (authentication notifications jaisa hi).
#
# Naya file : data/GuestPush.kt
# Patch     : GuestApi.kt, GuestChat.kt, MuwanFirebaseService.kt, MainActivity.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likha jata.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2F"

PUSH_KT = r'''package com.muwan.muwanchat.data

// GUEST_P2F
// Guest push: FCM token register + decrypt hone ke baad ki notification.
// Server push mein message ka koi content nahi bhejta (sirf "jaag jao") -- naam/text yahin, decrypt ke baad, banta hai.
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.muwan.muwanchat.MainActivity
import com.muwan.muwanchat.R
import com.muwan.muwanchat.network.GuestPushTokenRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object GuestPush {

    // MainActivity onStart/onStop se set hota hai: app screen par hai ya nahi
    @Volatile var visible: Boolean = false

    private const val CHANNEL = "muwan_messages"
    private const val REREGISTER_MS = 7L * 24 * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureRegistered(context: Context) {
        val app = context.applicationContext
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch {
                    try {
                        register(app, token)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun register(app: Context, token: String): Boolean {
        val gid = GuestStore.activeGuestId(app)
        if (gid.isEmpty() || token.isBlank()) return false
        val sp = app.getSharedPreferences("guest_push", Context.MODE_PRIVATE)
        val sig = gid + "|" + token
        val now = System.currentTimeMillis()
        if (sp.getString("sig", null) == sig && now - sp.getLong("at", 0L) < REREGISTER_MS) return true
        if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return false
        val t = GuestStore.token(app) ?: return false
        val res = RetrofitClient.guestApi.registerPush("Bearer " + t, GuestPushTokenRequest(token))
        if (!res.isSuccessful) return false
        sp.edit().putString("sig", sig).putLong("at", now).apply()
        return true
    }

    private fun notifId(roomId: String): Int = roomId.hashCode()

    fun cancel(context: Context, roomId: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId(roomId))
        } catch (_: Exception) {
        }
    }

    // Decrypt ho chuka message: naam + text notification mein. Settings mein notifications band ho to kuch nahi.
    suspend fun showMessage(app: Context, roomId: String, title: String, text: String) {
        try {
            val enabled = try {
                AuthDataStore.getNotificationsEnabled(app).first()
            } catch (_: Exception) {
                true
            }
            if (!enabled) return

            val myId = GuestStore.activeGuestId(app)
            val unread = if (myId.isEmpty()) 1 else
                (MuwanChatDb.get(app, myId).conversationDao().getByRoomId(roomId)?.unreadCount ?: 1)

            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "MuwanChat message notifications"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
            val intent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                app, notifId(roomId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val preview = text.take(300)
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(if (unread > 1) preview + "  (" + unread + " new)" else preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setNumber(unread)
                .setContentIntent(pi)
                .build()
            nm.notify(notifId(roomId), n)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
'''

API_DATA = '''// GUEST_P2F -- push token + HTTP mailbox (push se jaagne par)
data class GuestPushTokenRequest(
    val token: String
)

data class GuestAckRequest(
    val ids: List<String>
)

'''

SYNC_FN = '''    // GUEST_P2F ───────── push wake: mailbox HTTP se ─────────
    fun onPushWake(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                syncMailbox(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "push sync failed: " + e.message)
            }
        }
    }

    // App wapas screen par aayi aur koi chat khuli hai: background mein aaye messages read mark + notification hatao
    fun onAppVisible(context: Context) {
        val room = openRoomId
        val peer = openPeerId
        if (room != null && peer != null) onChatOpened(context, room, peer)
    }

    // Socket ke bina: pending envelopes HTTP se (page-wise), wahi decrypt/save/notify, phir ack.
    suspend fun syncMailbox(app: Context) {
        processLock.withLock {
            if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return
            val token = GuestStore.token(app) ?: return
            val myId = GuestStore.activeGuestId(app)
            if (myId.isEmpty()) return
            var after: String? = null
            for (page in 0 until 10) {
                val res = RetrofitClient.guestApi.mailbox("Bearer " + token, after, PAGE)
                if (!res.isSuccessful) return
                val bodyText = res.body()?.string() ?: return
                val arr = JSONObject(bodyText).optJSONArray("envelopes") ?: return
                if (arr.length() == 0) return
                val list = ArrayList<JSONObject>()
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
                list.sortBy { it.optString("id") }
                val ackIds = ArrayList<String>()
                for (e in list) {
                    val id = e.optString("id")
                    val d = decode(app, e, myId)
                    try {
                        if (d != null) applyEnvelope(app, myId, d)
                        if (id.isNotEmpty()) ackIds.add(id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Log.w(TAG, "apply failed (will retry): " + ex.message)
                    }
                }
                if (ackIds.isNotEmpty()) {
                    RetrofitClient.guestApi.ackMailbox("Bearer " + token, GuestAckRequest(ackIds))
                }
                after = list.last().optString("id")
                if (list.size < PAGE) return
            }
        }
    }

'''

def rd(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: anchor %d baar mila (1 hona chahiye). Kuch nahi likha." % (name, n))
        sys.exit(1)
    return s.replace(old, new)

writes = {}
skipped = []

def plan_new(rel, content):
    p = os.path.join(BASE, rel)
    if os.path.exists(p):
        if MARK in rd(p):
            skipped.append(rel); return
        print("FAIL: %s pehle se hai par GUEST_P2F wala nahi. Kuch nahi likha." % rel); sys.exit(1)
    writes[p] = content

def plan(path, label, fn, need):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao)" % path); sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label); return
    if need and need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle purane guest patches lagao. Kuch nahi likha." % (label, need)); sys.exit(1)
    writes[path] = fn(s)

plan_new("data/GuestPush.kt", PUSH_KT)

# --- GuestApi.kt
def p_api(s):
    s = rep(s, 'interface GuestApi {\n', API_DATA + 'interface GuestApi {\n', "api data")
    s = rep(s,
        '    @GET("guest/link/{code}")\n    suspend fun getLink(@retrofit2.http.Path("code") code: String): Response<GuestLinkResponse>\n',
        '    @GET("guest/link/{code}")\n    suspend fun getLink(@retrofit2.http.Path("code") code: String): Response<GuestLinkResponse>\n'
        '\n'
        '    // GUEST_P2F\n'
        '    @POST("guest/push-token")\n'
        '    suspend fun registerPush(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @Body request: GuestPushTokenRequest\n'
        '    ): Response<okhttp3.ResponseBody>\n'
        '\n'
        '    @GET("guest/mailbox")\n'
        '    suspend fun mailbox(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @retrofit2.http.Query("after") after: String?,\n'
        '        @retrofit2.http.Query("limit") limit: Int\n'
        '    ): Response<okhttp3.ResponseBody>\n'
        '\n'
        '    @POST("guest/mailbox/ack")\n'
        '    suspend fun ackMailbox(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @Body request: GuestAckRequest\n'
        '    ): Response<okhttp3.ResponseBody>\n',
        "api methods")
    return s
plan(os.path.join(BASE, "network/GuestApi.kt"), "GuestApi.kt", p_api, "GUEST_P2D")

# --- GuestChat.kt
def p_chat(s):
    s = rep(s, 'import com.muwan.muwanchat.network.GuestEnvelopeOut\n',
            'import com.muwan.muwanchat.network.GuestAckRequest  // GUEST_P2F\nimport com.muwan.muwanchat.network.GuestEnvelopeOut\n', "import")
    s = rep(s, '    fun start(context: Context) {\n        val app = context.applicationContext\n',
            '    fun start(context: Context) {\n        val app = context.applicationContext\n        GuestPush.ensureRegistered(app)  // GUEST_P2F: FCM token server ko\n',
            "start")
    s = rep(s,
        '        db.conversationDao().clearUnread(roomId)\n        db.messageDao().markSeen(roomId, myId)\n        sendSeen(app, peerUid)\n',
        '        db.conversationDao().clearUnread(roomId)\n        db.messageDao().markSeen(roomId, myId)\n        GuestPush.cancel(app, roomId)  // GUEST_P2F\n        sendSeen(app, peerUid)\n',
        "mark read")
    s = rep(s,
        '                if (openRoomId == roomId) markOpenRead(app, roomId, d.from)\n',
        '                if (!GuestPush.visible) GuestPush.showMessage(app, roomId, shown, text)  // GUEST_P2F: decrypted notification\n'
        '                if (openRoomId == roomId && GuestPush.visible) markOpenRead(app, roomId, d.from)\n',
        "notify")
    s = rep(s, '    // ───────── send ─────────\n', SYNC_FN + '    // ───────── send ─────────\n', "sync fn")
    return s
plan(os.path.join(BASE, "data/GuestChat.kt"), "GuestChat.kt", p_chat, "GUEST_P2E")

# --- MuwanFirebaseService.kt
def p_fcm(s):
    s = rep(s,
        '                val authToken = AuthDataStore.getToken(applicationContext).first() ?: return@launch\n                RetrofitClient.usersApi.updateFcmToken(\n                    "Bearer $authToken",\n                    mapOf("fcm_token" to token)\n',
        '                if (com.muwan.muwanchat.data.GuestStore.isGuestActive(applicationContext)) {  // GUEST_P2F\n'
        '                    com.muwan.muwanchat.data.GuestPush.register(applicationContext, token)\n'
        '                    return@launch\n'
        '                }\n'
        '                val authToken = AuthDataStore.getToken(applicationContext).first() ?: return@launch\n                RetrofitClient.usersApi.updateFcmToken(\n                    "Bearer $authToken",\n                    mapOf("fcm_token" to token)\n',
        "fcm token")
    s = rep(s,
        '        // CALL_PUSH_PATCH: call push (data-only, high priority) -- app band ho tab bhi ring + Accept/Decline\n',
        '        // GUEST_P2F: guest push (content-free wake) -> mailbox laao, decrypt, notification. Calls abhi Coming Soon.\n'
        '        if (message.data["guest"] == "1") {\n'
        '            if (message.data["gkind"] != "call") com.muwan.muwanchat.data.GuestChat.onPushWake(applicationContext)\n'
        '            return\n'
        '        }\n'
        '\n'
        '        // CALL_PUSH_PATCH: call push (data-only, high priority) -- app band ho tab bhi ring + Accept/Decline\n',
        "fcm message")
    return s
plan(os.path.join(BASE, "MuwanFirebaseService.kt"), "MuwanFirebaseService.kt", p_fcm, None)

# --- MainActivity.kt : app screen par hai ya nahi
def p_main(s):
    s = rep(s, '        AppLockState.onActivityStarted(applicationContext)\n',
            '        AppLockState.onActivityStarted(applicationContext)\n        com.muwan.muwanchat.data.GuestPush.visible = true  // GUEST_P2F\n        com.muwan.muwanchat.data.GuestChat.onAppVisible(applicationContext)\n', "main start")
    s = rep(s, '        AppLockState.onActivityStopped(isChangingConfigurations)\n',
            '        AppLockState.onActivityStopped(isChangingConfigurations)\n        com.muwan.muwanchat.data.GuestPush.visible = false  // GUEST_P2F\n', "main stop")
    return s
plan(os.path.join(BASE, "MainActivity.kt"), "MainActivity.kt", p_main, None)

if not writes:
    print("Already patched, kuch nahi kiya."); sys.exit(0)
for path, content in writes.items():
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK  " + os.path.relpath(path, ROOT))
for x in skipped:
    print("skip (pehle se hai) " + x)
print("\nGuest push patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
