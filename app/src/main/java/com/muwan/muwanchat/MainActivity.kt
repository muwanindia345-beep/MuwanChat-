package com.muwan.muwanchat

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.muwan.muwanchat.data.AppLockState
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.UpdateManager
import com.muwan.muwanchat.navigation.NavGraph
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.screens.PatternUnlockScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // App lock: first onCreate of a fresh process locks (if a lock is set).
        AppLockState.onActivityCreated(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val authToken = AuthDataStore.getToken(applicationContext).first() ?: return@launch
                    RetrofitClient.usersApi.updateFcmToken("Bearer $authToken", mapOf("fcm_token" to token))
                } catch (_: Exception) {}
            }
        }

        // "Update Available" notification pe tap kiya hai to seedha
        // Check Updates screen khulni chahiye — is extra se pata chalta hai.
        handleCallAnswerIntent(intent) // CALL_PUSH_PATCH
        handleOpenCallIntent(intent) // STEP4B_OPEN_CALL
        val openUpdateScreen = intent?.getBooleanExtra(UpdateManager.EXTRA_OPEN_UPDATE_SCREEN, false) ?: false

        setContent {
            val locked by AppLockState.locked.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                NavGraph(openUpdateScreen = openUpdateScreen)
                if (locked) {
                    // Opaque gate drawn above the whole app until unlocked.
                    PatternUnlockScreen(
                        onUnlocked = { AppLockState.unlock() },
                        onLogoutRequired = { forceLogout() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLockState.onActivityStarted(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        AppLockState.onActivityStopped(isChangingConfigurations)
    }

    // Too many wrong pattern attempts: log the account out (same as Settings ->
    // Logout; local chat data stays on the device) and restart at the login screen.
    private fun forceLogout() {
        lifecycleScope.launch(Dispatchers.IO) {
            try { AppSocketManager.disconnect() } catch (_: Exception) {}
            AuthDataStore.clearAuth(applicationContext) // also removes the app lock
            withContext(Dispatchers.Main) {
                AppLockState.unlock()
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
    }

    // STEP4B_OPEN_CALL: ongoing-call notification pe tap -- NavGraph chalti call ki screen khol dega
    private fun handleOpenCallIntent(intent: android.content.Intent?) {
        val i = intent ?: return
        if (i.action != com.muwan.muwanchat.calling.CallForegroundService.ACTION_OPEN_CALL) return
        // Action saaf: screen rotate / activity recreate par dobara call screen na khule
        i.action = null
        com.muwan.muwanchat.calling.CallControlEvents.openCallRequest.value = true
    }

    // CALL_PUSH_PATCH: app already khuli thi to naya intent yahan aata hai
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallAnswerIntent(intent)
        handleOpenCallIntent(intent) // STEP4B_OPEN_CALL
    }

    // Notification ke "Answer" se aaye to ring band karo, notification hatao, aur CallScreen ko auto-accept ka signal do
    private fun handleCallAnswerIntent(intent: android.content.Intent?) {
        if (intent?.action != com.muwan.muwanchat.calling.CallControlEvents.ACTION_ANSWER_FROM_NOTIFICATION) return
        val callId = intent?.getStringExtra("callId") ?: return
        com.muwan.muwanchat.calling.PushRinger.stop()
        com.muwan.muwanchat.calling.CallForegroundService.dismiss(applicationContext)
        com.muwan.muwanchat.calling.CallControlEvents.notifyAnsweredFromNotification(callId)
    }
}
