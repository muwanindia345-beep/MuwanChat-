import sys, os

# Usage: python keepalive_patch.py <repo_root>
# App khulte hi backend /health ko ping karta hai (Render free sleep se jagane ke liye)
# aur app foreground mein rehne tak har 4 minute mein dobara ping karta hai.
# Ye UptimeRobot ka BACKUP hai, replacement nahi.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
APP = os.path.join(BASE, "MuwanChatApp.kt")
NEW = os.path.join(BASE, "network", "BackendKeepAlive.kt")

s = open(APP, encoding="utf-8").read()
if "KEEPALIVE_V1" in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

KT = '''package com.muwan.muwanchat.network

// KEEPALIVE_V1
// Render free plan 15 min idle par server sula deta hai. Jab tak app foreground mein hai,
// ye /health ko ping karta rehta hai taaki server jaga rahe. App khulte hi pehla ping
// turant jaata hai (cold start ho to chat load hone tak server uth jaata hai).
// Background mein ping nahi karta (battery + Android restrictions) -- wahan UptimeRobot kaam karega.
import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object BackendKeepAlive {

    private const val INTERVAL_MS = 4 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS) // cold start 30-60s tak le sakta hai
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var startedCount = 0
    private var registered = false

    private val healthUrl: String =
        com.muwan.muwanchat.BuildConfig.BACKEND_URL.trimEnd('/') + "/health"

    fun start(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                synchronized(this@BackendKeepAlive) {
                    startedCount++
                    if (startedCount == 1) startLoop()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                synchronized(this@BackendKeepAlive) {
                    if (startedCount > 0) startedCount--
                    if (startedCount == 0) stopLoop()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                ping()
                delay(INTERVAL_MS)
            }
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
    }

    private fun ping() {
        try {
            val req = Request.Builder()
                .url(healthUrl)
                .header("ngrok-skip-browser-warning", "1")
                .get()
                .build()
            client.newCall(req).execute().use { /* response ignore */ }
        } catch (_: Exception) {
            // Network na ho ya server down ho -- chup-chaap ignore, agli baar dobara try
        }
    }
}
'''

old = '''        com.muwan.muwanchat.calling.IceServerProvider.prefetch(applicationContext)
'''
new = '''        com.muwan.muwanchat.calling.IceServerProvider.prefetch(applicationContext)
        com.muwan.muwanchat.network.BackendKeepAlive.start(this) // KEEPALIVE_V1
'''
n = s.count(old)
if n != 1:
    print("FAIL MuwanChatApp.kt: matched %d times" % n)
    sys.exit(1)
s = s.replace(old, new)

os.makedirs(os.path.dirname(NEW), exist_ok=True)
open(NEW, "w", encoding="utf-8").write(KT)
open(APP, "w", encoding="utf-8").write(s)
print("OK: network/BackendKeepAlive.kt banaya + MuwanChatApp.kt patched")
