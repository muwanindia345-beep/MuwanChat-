package com.muwan.muwanchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.NavGraph
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Handle Google deep link on cold start
        handleIntent(intent)

        setContent {
            NavGraph()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "muwanchat" && data.host == "auth") {
            val token = data.getQueryParameter("token") ?: return
            val username = data.getQueryParameter("username") ?: "User"
            val email = data.getQueryParameter("email") ?: ""
            val uid = data.getQueryParameter("uid") ?: ""

            lifecycleScope.launch {
                AuthDataStore.saveAuth(
                    this@MainActivity,
                    token, username, email, uid
                )
            }
        }
    }
}
