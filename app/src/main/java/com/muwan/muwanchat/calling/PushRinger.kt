package com.muwan.muwanchat.calling

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
