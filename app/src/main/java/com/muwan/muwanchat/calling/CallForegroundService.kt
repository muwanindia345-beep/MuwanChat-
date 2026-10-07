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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.muwan.muwanchat.MainActivity
import com.muwan.muwanchat.R
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        // STEP4A_ONGOING_NOTIF
        private const val ACTION_ONGOING = "com.muwan.muwanchat.calling.ACTION_ONGOING"
        private const val ACTION_ONGOING_UPDATE = "com.muwan.muwanchat.calling.ACTION_ONGOING_UPDATE"
        private const val ACTION_HANGUP = "com.muwan.muwanchat.calling.ACTION_HANGUP"
        private const val EXTRA_FORCE = "force"
        private const val CHANNEL_ONGOING = "muwan_calls_ongoing"

        /** Ongoing-call notification par tap karne se MainActivity ko yeh action milta hai. */
        const val ACTION_OPEN_CALL = "com.muwan.muwanchat.calling.OPEN_CALL"

        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_FROM_USERNAME = "fromUsername"
        private const val EXTRA_RING = "ring" // CALL_PUSH_PATCH

        /** Naya incoming call aaya -- notification dikhao. */
        fun showIncomingCall(context: Context, callId: String, fromUsername: String, ring: Boolean = false) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_FROM_USERNAME, fromUsername)
                putExtra(EXTRA_RING, ring) // CALL_PUSH_PATCH: push se aayi call (CallScreen nahi) to service ring bajaye
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // STEP4A_ONGOING_NOTIF: call chalte waqt (app open / background / band) ek hi notification:
        // naam, chalta timer, Hang up button. Tap karne par app khulti hai.

        /** Call shuru/accept hote hi (app foreground mein) service ko ongoing mode mein lao. */
        fun showOngoingCall(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_ONGOING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Call connect hui -- notification mein timer chalu karo (service pehle se chal rahi hai). */
        fun updateOngoing(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_ONGOING_UPDATE
            }
            context.startService(intent)
        }

        /**
         * Call kisi aur wajah se khatam ho gayi (caller ne hangup kar diya, busy, etc).
         * force=true sirf ActiveCall deta hai; baaki jagah se aaya "stale" dismiss live call
         * ke dauran ignore ho jaata hai.
         */
        fun dismiss(context: Context, force: Boolean = false) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_FORCE, force)
            }
            context.startService(intent)
        }

        private fun ensureOngoingChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ONGOING,
                "Ongoing call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a call is in progress"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        // Notification ActiveCall ki state se hi banti hai (single source of truth)
        private fun buildOngoingNotification(context: Context): Notification? {
            val info = ActiveCall.info.value ?: return null
            val phase = ActiveCall.phase.value
            ensureOngoingChannel(context)

            val hangUpIntent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_HANGUP
            }
            val hangUpPending = PendingIntent.getService(
                context, 4, hangUpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_CALL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, 5, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val person = Person.Builder().setName(info.otherUsername).build()
            val connectedAt = ActiveCall.connectedAt.value
            val connected = phase == CallPhase.ONGOING && connectedAt > 0L
            val text = when (phase) {
                CallPhase.ONGOING -> "Ongoing call"
                CallPhase.CONNECTING -> "Connecting..."
                else -> "Calling..."
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                NotificationCompat.Builder(context, CHANNEL_ONGOING)
                    .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUpPending))
            } else {
                NotificationCompat.Builder(context, CHANNEL_ONGOING)
                    .setContentTitle(info.otherUsername)
                    .addAction(0, "Hang up", hangUpPending)
            }
            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText(text)
                .setContentIntent(openPending)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
            if (connected) {
                // Chronometer: connect hone ke waqt se chalta hai (app band ho tab bhi system chalata hai)
                val startedAtWall = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - connectedAt)
                builder.setUsesChronometer(true).setShowWhen(true).setWhen(startedAtWall)
            }
            return builder.build()
        }

        // startForegroundService ke baad startForeground() zaroori hai, call pehle hi khatam ho
        // chuki ho tab bhi -- isliye khali notification se contract poora karke turant band karte hain.
        private fun placeholderNotification(context: Context): Notification {
            ensureOngoingChannel(context)
            return NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Call ended")
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return stopSelfImmediately(startId)
                val fromUsername = intent.getStringExtra(EXTRA_FROM_USERNAME) ?: "Unknown"
                val notification = buildIncomingCallNotification(callId, fromUsername)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    // CALL_PUSH_PATCH: foreground start fail ho to bhi notification zaroor dikhe
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {}
                }
                if (intent.getBooleanExtra(EXTRA_RING, false)) PushRinger.start(this)
            }
            ACTION_ANSWER -> {
                PushRinger.stop() // CALL_PUSH_PATCH
                intent.getStringExtra(EXTRA_CALL_ID)?.let { CallControlEvents.notifyAnsweredFromNotification(it) }
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
                PushRinger.stop() // CALL_PUSH_PATCH
                stopForegroundCompat()
                if (callId != null) {
                    CallControlEvents.declinedCallIds.add(callId)
                    CallControlEvents.notifyDeclinedFromNotification(callId)
                    // reject bhejne tak service zinda rakho (app killed ho to pehle socket connect hota hai)
                    rejectCall(callId) { stopSelf() }
                } else {
                    stopSelf()
                }
            }
            // STEP4A_ONGOING_NOTIF
            ACTION_ONGOING -> {
                val notification = buildOngoingNotification(this)
                if (notification == null) {
                    try {
                        startForeground(NOTIFICATION_ID, placeholderNotification(this))
                    } catch (_: Exception) {
                    }
                    stopForegroundCompat()
                    return stopSelfImmediately(startId)
                }
                var started = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        startForeground(
                            NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                        started = true
                    } catch (_: Exception) {
                    }
                }
                if (!started && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        startForeground(
                            NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                        )
                        started = true
                    } catch (_: Exception) {
                    }
                }
                if (!started && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                        started = true
                    } catch (_: Exception) {
                    }
                }
                if (!started) {
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {
                    }
                }
            }
            ACTION_ONGOING_UPDATE -> {
                if (!ActiveCall.isActive) {
                    stopSelf()
                } else {
                    val notification = buildOngoingNotification(this)
                    if (notification != null) {
                        try {
                            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                .notify(NOTIFICATION_ID, notification)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            ACTION_HANGUP -> {
                // Notification ke "Hang up" se -- app khuli ho ya band, ActiveCall hi call khatam karta hai
                if (ActiveCall.isActive) ActiveCall.end()
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_DISMISS -> {
                // Live call ke dauran stale dismiss (jaise kisi pichli call ka late call_end) ignore
                val force = intent.getBooleanExtra(EXTRA_FORCE, false)
                if (!force && ActiveCall.isActive && ActiveCall.phase.value != CallPhase.RINGING_INCOMING) {
                    return START_NOT_STICKY
                }
                PushRinger.stop() // CALL_PUSH_PATCH
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // CALL_PUSH_PATCH: app band hone par socket nahi hota -- pehle connect karo, phir reject bhejo
    private fun rejectCall(callId: String, onDone: () -> Unit) {
        if (AppSocketManager.isConnected) {
            AppSocketManager.sendCallReject(callId)
            onDone()
            return
        }
        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = AuthDataStore.getToken(appCtx).first()
                if (!token.isNullOrBlank()) {
                    AppSocketManager.connect(token)
                    var waited = 0
                    while (!AppSocketManager.isConnected && waited < 8000) {
                        delay(200)
                        waited += 200
                    }
                    if (AppSocketManager.isConnected) {
                        AppSocketManager.sendCallReject(callId)
                        delay(500) // emit flush hone do
                    }
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
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
        // CALL_PUSH_PATCH: Answer seedha MainActivity kholta hai (Android 12+ notification se service->activity block hai)
        val answerActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = CallControlEvents.ACTION_ANSWER_FROM_NOTIFICATION
            putExtra(EXTRA_CALL_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this, 1, answerActivityIntent,
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
