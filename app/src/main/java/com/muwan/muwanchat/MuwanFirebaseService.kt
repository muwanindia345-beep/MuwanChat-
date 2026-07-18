package com.muwan.muwanchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MuwanFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authToken = AuthDataStore.getToken(applicationContext).first() ?: return@launch
                RetrofitClient.usersApi.updateFcmToken(
                    "Bearer $authToken",
                    mapOf("fcm_token" to token)
                )
            } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (message.data["type"] == "app_update") {
            val versionCode = message.data["versionCode"]?.toIntOrNull() ?: return
            if (versionCode > com.muwan.muwanchat.data.UpdateManager.getLastSeenVersionCode(applicationContext)) {
                val info = com.muwan.muwanchat.network.AppVersionInfo(
                    versionCode = versionCode,
                    versionName = message.data["versionName"] ?: "",
                    changelog = message.data["changelog"] ?: "",
                    apkUrl = message.data["apkUrl"],
                    releaseDate = null
                )
                com.muwan.muwanchat.data.UpdateManager.showUpdateNotification(applicationContext, info)
            }
            return
        }

        val title = message.notification?.title ?: message.data["title"] ?: "MuwanChat"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"

        CoroutineScope(Dispatchers.IO).launch {
            val notificationsEnabled = try {
                AuthDataStore.getNotificationsEnabled(applicationContext).first()
            } catch (_: Exception) {
                true // read fail ho jaye to fail-open (notification na khoye)
            }
            if (!notificationsEnabled) return@launch

            showNotification(title, body)
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "muwan_messages"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "MuwanChat message notifications"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
