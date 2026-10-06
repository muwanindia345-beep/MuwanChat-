#!/usr/bin/env python3
# Call push ringing (app band ho tab bhi ring + Answer/Decline) -- Project root se run karo (jahan app/ folder hai).
import sys, os

BASE = "app/src/main/java/com/muwan/muwanchat/"
MARK = "CALL_PUSH_PATCH"

def patch(rel, pairs, base=BASE):
    path = base + rel
    if not os.path.exists(path):
        sys.exit("ERROR: %s nahi mila -- project root se run karo" % path)
    s = open(path, encoding="utf-8").read()
    if MARK in s:
        print("SKIP  %s (already patched)" % rel)
        return
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            sys.exit("ERROR: %s mein anchor %d baar mila (1 chahiye):\n%s" % (rel, n, old[:140]))
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)
    print("OK    %s" % rel)

# ── 1) PushRinger.kt (naya): service ke liye ring + 45s auto-stop ──
RINGER = BASE + "calling/PushRinger.kt"
RINGER_SRC = '''package com.muwan.muwanchat.calling

import android.content.Context
import android.os.Handler
import android.os.Looper

// CALL_PUSH_PATCH
// App band/background ho aur FCM se call aaye, tab ringtone+vibration yahi bajata hai
// (CallScreen tab tak khuli nahi hoti). CallScreen khulte hi ise stop kar deti hai aur
// apni ring khud sambhal leti hai, isliye double ring nahi hoti.
object PushRinger {
    private const val MAX_RING_MS = 45_000L
    private val handler = Handler(Looper.getMainLooper())
    private var manager: CallRingtoneManager? = null
    private var timeout: Runnable? = null

    fun start(context: Context) {
        val appCtx = context.applicationContext
        handler.post {
            stopInternal()
            manager = CallRingtoneManager(appCtx).also { it.startIncomingRing() }
            val r = Runnable {
                stopInternal()
                try { CallForegroundService.dismiss(appCtx) } catch (_: Exception) {}
            }
            timeout = r
            handler.postDelayed(r, MAX_RING_MS)
        }
    }

    fun stop() {
        handler.post { stopInternal() }
    }

    private fun stopInternal() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        manager?.stop()
        manager = null
    }
}
'''
if os.path.exists(RINGER) and MARK in open(RINGER, encoding="utf-8").read():
    print("SKIP  calling/PushRinger.kt (already exists)")
else:
    open(RINGER, "w", encoding="utf-8").write(RINGER_SRC)
    print("OK    calling/PushRinger.kt (new)")

# ── 2) CallControlEvents.kt ──
patch("calling/CallControlEvents.kt", [
("""    fun notifyDeclinedFromNotification(callId: String) {
        _declinedFromNotification.tryEmit(callId)
    }
""",
"""    fun notifyDeclinedFromNotification(callId: String) {
        _declinedFromNotification.tryEmit(callId)
    }

    // CALL_PUSH_PATCH: notification ke "Answer" ka signal. CallScreen abhi khuli na ho
    // (app killed tha) to callId yahan rukta hai, aur khulte hi CallScreen khud accept kar leti hai.
    @Volatile var answerRequestedCallId: String? = null
    private val _answeredFromNotification = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val answeredFromNotification = _answeredFromNotification.asSharedFlow()

    fun notifyAnsweredFromNotification(callId: String) {
        answerRequestedCallId = callId
        _answeredFromNotification.tryEmit(callId)
    }

    // Notification se decline ki hui calls -- offer replay aaye to NavGraph dobara screen na khole
    val declinedCallIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Jis incoming call ki CallScreen abhi khuli hai (push dobara ring na kare)
    @Volatile var screenCallId: String? = null

    // Notification ka "Answer" seedha MainActivity kholta hai (Android 12+ service se activity start block karta hai)
    const val ACTION_ANSWER_FROM_NOTIFICATION = "com.muwan.muwanchat.calling.ANSWER_FROM_NOTIFICATION"
"""),
])

# ── 3) CallForegroundService.kt ──
patch("calling/CallForegroundService.kt", [
("import com.muwan.muwanchat.data.AppSocketManager\n",
 "import com.muwan.muwanchat.data.AppSocketManager\nimport com.muwan.muwanchat.data.AuthDataStore\nimport kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n"),
("""        private const val EXTRA_FROM_USERNAME = "fromUsername"
""",
"""        private const val EXTRA_FROM_USERNAME = "fromUsername"
        private const val EXTRA_RING = "ring" // CALL_PUSH_PATCH
"""),
("""        fun showIncomingCall(context: Context, callId: String, fromUsername: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_FROM_USERNAME, fromUsername)
            }""",
"""        fun showIncomingCall(context: Context, callId: String, fromUsername: String, ring: Boolean = false) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_FROM_USERNAME, fromUsername)
                putExtra(EXTRA_RING, ring) // CALL_PUSH_PATCH: push se aayi call (CallScreen nahi) to service ring bajaye
            }"""),
("""                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_ANSWER -> {
""",
"""                try {
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
"""),
("""                if (callId != null) {
                    AppSocketManager.sendCallReject(callId)
                    CallControlEvents.notifyDeclinedFromNotification(callId)
                }
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_DISMISS -> {
""",
"""                PushRinger.stop() // CALL_PUSH_PATCH
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
            ACTION_DISMISS -> {
                PushRinger.stop() // CALL_PUSH_PATCH
"""),
("""        val answerPendingIntent = PendingIntent.getService(
            this, 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
""",
"""        // CALL_PUSH_PATCH: Answer seedha MainActivity kholta hai (Android 12+ notification se service->activity block hai)
        val answerActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = CallControlEvents.ACTION_ANSWER_FROM_NOTIFICATION
            putExtra(EXTRA_CALL_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this, 1, answerActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
"""),
("""    private fun stopSelfImmediately(startId: Int): Int {""",
"""    // CALL_PUSH_PATCH: app band hone par socket nahi hota -- pehle connect karo, phir reject bhejo
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

    private fun stopSelfImmediately(startId: Int): Int {"""),
])

# ── 4) MuwanFirebaseService.kt ──
patch("MuwanFirebaseService.kt", [
("""        val title = message.notification?.title ?: message.data["title"] ?: "MuwanChat"
""",
"""        // CALL_PUSH_PATCH: call push (data-only, high priority) -- app band ho tab bhi ring + Accept/Decline
        when (message.data["notifType"]) {
            "call" -> {
                handleCallPush(message.data)
                return
            }
            "missed_call" -> {
                // Call kat gayi/miss ho gayi -- ring band karo, neeche normal "Missed call" notification dikhegi
                com.muwan.muwanchat.calling.PushRinger.stop()
                com.muwan.muwanchat.calling.CallForegroundService.dismiss(applicationContext)
            }
        }

        val title = message.notification?.title ?: message.data["title"] ?: "MuwanChat"
"""),
("""    // Backend (FCM_DATA_ONLY=true) push mein poora message bhejta hai""",
"""    // CALL_PUSH_PATCH
    private fun handleCallPush(data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val fromUsername = data["fromUsername"] ?: "Unknown"
        // Is call ki CallScreen pehle se khuli hai (socket se aa gayi) -- double ring mat karo
        if (com.muwan.muwanchat.calling.CallControlEvents.screenCallId == callId) return
        if (com.muwan.muwanchat.calling.CallControlEvents.declinedCallIds.contains(callId)) return
        try {
            com.muwan.muwanchat.calling.CallForegroundService.showIncomingCall(
                applicationContext, callId, fromUsername, ring = true
            )
        } catch (_: Exception) {
            // Foreground service start na ho paye to kam se kam normal notification dikhe
            showNotification(
                data["title"] ?: "Incoming call",
                data["body"] ?: "$fromUsername is calling you"
            )
        }
    }

    // Backend (FCM_DATA_ONLY=true) push mein poora message bhejta hai"""),
])

# ── 5) NavGraph.kt (guards) ──
patch("navigation/NavGraph.kt", [
("            if (event is com.muwan.muwanchat.data.SocketEvent.CallOfferReceived) {\n",
 "            if (event is com.muwan.muwanchat.data.SocketEvent.CallOfferReceived &&\n                !com.muwan.muwanchat.calling.CallControlEvents.declinedCallIds.contains(event.callId) // CALL_PUSH_PATCH\n            ) {\n"),
("""                com.muwan.muwanchat.calling.CallForegroundService.showIncomingCall(
                    context = context,
                    callId = event.callId,
                    fromUsername = event.fromUsername
                )
""",
"""                // CALL_PUSH_PATCH: notification se Answer ho chuka ho to dobara ring-notification mat dikhao
                if (com.muwan.muwanchat.calling.CallControlEvents.answerRequestedCallId != event.callId) {
                    com.muwan.muwanchat.calling.CallForegroundService.showIncomingCall(
                        context = context,
                        callId = event.callId,
                        fromUsername = event.fromUsername
                    )
                }
"""),
])

# ── 6) CallScreen.kt ──
patch("screens/CallScreen.kt", [
("            CallState.RINGING_INCOMING -> ringtoneManager.startIncomingRing()\n",
"""            CallState.RINGING_INCOMING -> {
                // CALL_PUSH_PATCH: push wali ring band karo; Answer notification se ho chuka ho to ring bajao hi mat
                com.muwan.muwanchat.calling.PushRinger.stop()
                if (com.muwan.muwanchat.calling.CallControlEvents.answerRequestedCallId != callId) {
                    ringtoneManager.startIncomingRing()
                }
            }
"""),
("""    fun declineCall() {
        AppSocketManager.sendCallReject(callId)
""",
"""    // CALL_PUSH_PATCH: push dobara ring na kare jab tak ye screen khuli hai
    DisposableEffect(Unit) {
        if (isIncoming) com.muwan.muwanchat.calling.CallControlEvents.screenCallId = callId
        onDispose {
            if (com.muwan.muwanchat.calling.CallControlEvents.screenCallId == callId) {
                com.muwan.muwanchat.calling.CallControlEvents.screenCallId = null
            }
        }
    }

    // CALL_PUSH_PATCH: notification se "Answer" dabaya -- dobara Accept dabane ki zaroorat nahi
    LaunchedEffect(hasMicPermission) {
        if (isIncoming && hasMicPermission &&
            com.muwan.muwanchat.calling.CallControlEvents.answerRequestedCallId == callId &&
            callState == CallState.RINGING_INCOMING
        ) {
            delay(250) // callManager.init() poora hone do
            if (callState == CallState.RINGING_INCOMING) {
                com.muwan.muwanchat.calling.CallControlEvents.answerRequestedCallId = null
                acceptCall()
            }
        }
    }
    LaunchedEffect(Unit) {
        com.muwan.muwanchat.calling.CallControlEvents.answeredFromNotification.collect { answeredId ->
            if (answeredId == callId && hasMicPermission && callState == CallState.RINGING_INCOMING) {
                com.muwan.muwanchat.calling.CallControlEvents.answerRequestedCallId = null
                acceptCall()
            }
        }
    }

    fun declineCall() {
        AppSocketManager.sendCallReject(callId)
"""),
])

# ── 6b) MainActivity.kt (notification Answer -> auto accept) ──
patch("MainActivity.kt", [
("""        val openUpdateScreen = intent?.getBooleanExtra(""",
"""        handleCallAnswerIntent(intent) // CALL_PUSH_PATCH
        val openUpdateScreen = intent?.getBooleanExtra("""),
("""        setContent {
            NavGraph(openUpdateScreen = openUpdateScreen)
        }
    }
""",
"""        setContent {
            NavGraph(openUpdateScreen = openUpdateScreen)
        }
    }

    // CALL_PUSH_PATCH: app already khuli thi to naya intent yahan aata hai
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallAnswerIntent(intent)
    }

    // Notification ke "Answer" se aaye to ring band karo, notification hatao, aur CallScreen ko auto-accept ka signal do
    private fun handleCallAnswerIntent(intent: android.content.Intent?) {
        if (intent?.action != com.muwan.muwanchat.calling.CallControlEvents.ACTION_ANSWER_FROM_NOTIFICATION) return
        val callId = intent?.getStringExtra("callId") ?: return
        com.muwan.muwanchat.calling.PushRinger.stop()
        com.muwan.muwanchat.calling.CallForegroundService.dismiss(applicationContext)
        com.muwan.muwanchat.calling.CallControlEvents.notifyAnsweredFromNotification(callId)
    }
"""),
])

# ── 7) AndroidManifest.xml ──
patch("AndroidManifest.xml", [
('    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n',
 '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n    <!-- CALL_PUSH_PATCH --><uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />\n'),
], base="app/src/main/")

print("Done. Push app + Actions beta build.")
