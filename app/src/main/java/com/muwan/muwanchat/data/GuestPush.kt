package com.muwan.muwanchat.data

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

    // GUEST_LOGOUT -- server se push token hatao (best effort; net na ho to chup-chaap aage badho)
    suspend fun unregister(app: Context) {
        try {
            if (GuestSession.ensure(app) is GuestSession.Result.Ok) {
                val t = GuestStore.token(app)
                if (t != null) RetrofitClient.guestApi.unregisterPush("Bearer " + t)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        app.getSharedPreferences("guest_push", Context.MODE_PRIVATE).edit().clear().apply()
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
