package com.muwan.muwanchat.calling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.muwan.muwanchat.MainActivity
import com.muwan.muwanchat.R
import com.muwan.muwanchat.data.AppSocketManager

/**
 * CallScreen ki UI/UX bilkul waisi hi rehti hai jaisi thi -- ye service uske
 * UPAR ek naya, independent layer hai. NavGraph pehle jaisa hi turant
 * CallScreen pe navigate karta hai (unchanged); ye service sirf ek system
 * notification (CallStyle) dikhata hai taaki:
 *  - screen off / app background ho tab bhi call system-level dikhe
 *  - Accept/Decline seedha notification se hi ho sake, app khole bina
 *
 * Actual call logic (SDP, PeerConnection, ringtone) CallManager/CallScreen
 * mein hi rehta hai -- yeh service sirf notification ki "jaankari" wala
 * hissa hai, call ka data/state ise duplicate nahi karna padta.
 */
class CallForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "muwan_calls"
        private const val NOTIFICATION_ID = 5501

        private const val ACTION_SHOW = "com.muwan.muwanchat.calling.ACTION_SHOW"
        private const val ACTION_ANSWER = "com.muwan.muwanchat.calling.ACTION_ANSWER"
        private const val ACTION_DECLINE = "com.muwan.muwanchat.calling.ACTION_DECLINE"
        private const val ACTION_DISMISS = "com.muwan.muwanchat.calling.ACTION_DISMISS"

        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_FROM_USERNAME = "fromUsername"

        /** Naya incoming call aaya -- notification dikhao. */
        fun showIncomingCall(context: Context, callId: String, fromUsername: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_FROM_USERNAME, fromUsername)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Call kisi aur wajah se khatam ho gayi (caller ne hangup kar diya, busy, etc). */
        fun dismiss(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_DISMISS
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return stopSelfImmediately(startId)
                val fromUsername = intent.getStringExtra(EXTRA_FROM_USERNAME) ?: "Unknown"
                val notification = buildIncomingCallNotification(callId, fromUsername)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_ANSWER -> {
                // Navigation pehle se hi ho chuki hai (NavGraph ne call_offer
                // pe hi CallScreen khol diya tha) -- yahan sirf app ko front
                // pe laana hai, bilkul waisa hi jaisa message notification
                // tap karne pe hota hai (MuwanFirebaseService dekho).
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(launchIntent)
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_DECLINE -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    AppSocketManager.sendCallReject(callId)
                    CallControlEvents.notifyDeclinedFromNotification(callId)
                }
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_DISMISS -> {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSelfImmediately(startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildIncomingCallNotification(callId: String, fromUsername: String): Notification {
        ensureChannel()

        val answerIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_ANSWER
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declineIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, callId)
        }
        val answerPendingIntent = PendingIntent.getService(
            this, 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePendingIntent = PendingIntent.getService(
            this, 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fullscreen intent -- lock screen pe bhi call turant dikhe (real
        // dialer jaisa), tap karne pe app answer wale flow me hi khulta hai.
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 3, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder().setName(fromUsername).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        caller, declinePendingIntent, answerPendingIntent
                    )
                )
        } else {
            // Pre-Android 12: CallStyle exist nahi karta, normal actions se
            // wahi Accept/Decline experience mimic karte hain.
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(fromUsername)
                .setContentText("Incoming call")
                .addAction(0, "Decline", declinePendingIntent)
                .addAction(0, "Accept", answerPendingIntent)
        }

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming voice/video calls"
            // Ringtone/vibration CallRingtoneManager already CallScreen ke
            // andar handle karta hai -- channel ko khud silent rakha hai
            // taaki double sound/vibration na ho.
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
