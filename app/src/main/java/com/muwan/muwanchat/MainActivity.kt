package com.muwan.muwanchat

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.NavGraph
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        setContent {
            NavGraph()
        }
    }
}
