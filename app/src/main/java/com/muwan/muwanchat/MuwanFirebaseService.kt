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

        // CALL_PUSH_PATCH: call push (data-only, high priority) -- app band ho tab bhi ring + Accept/Decline
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
        val body = message.notification?.body ?: message.data["body"] ?: "New message"

        CoroutineScope(Dispatchers.IO).launch {
            // Pehle message local DB mein (notification band ho tab bhi save hona chahiye)
            storePushedMessage(message.data)

            val notificationsEnabled = try {
                AuthDataStore.getNotificationsEnabled(applicationContext).first()
            } catch (_: Exception) {
                true // read fail ho jaye to fail-open (notification na khoye)
            }
            if (!notificationsEnabled) return@launch

            showNotification(title, body)
        }
    }

    // CALL_PUSH_PATCH
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

    // Backend (FCM_DATA_ONLY=true) push mein poora message bhejta hai -- use seedha Room mein
    // save kar do, taaki app kholte hi chat mein pehle se maujood ho. Koi bhi gadbad ho
    // to chupchap skip (notification phir bhi dikhega, message sync se aa jaayega).
    private suspend fun storePushedMessage(data: Map<String, String>) {
        try {
            val msgId = data["msg_id"] ?: return
            val roomId = data["room_id"] ?: return
            val senderUid = data["sender_uid"] ?: return
            val content = data["content"] ?: return // bada text: sirf notification, baaki sync se
            val myUid = AuthDataStore.getUid(applicationContext).first() ?: return
            if (myUid.isBlank() || senderUid == myUid) return
            val db = com.muwan.muwanchat.data.MuwanChatDb.get(applicationContext, myUid)
            com.muwan.muwanchat.data.ChatRepository.recordPushMessage(
                db = db,
                id = msgId,
                roomId = roomId,
                senderUid = senderUid,
                content = content,
                type = data["msg_type"] ?: "text",
                createdAt = data["created_at"]?.takeIf { it.isNotBlank() }
                    ?: com.muwan.muwanchat.screens.nowIso(),
                myUid = myUid,
                fileName = data["file_name"],
                mimeType = data["mime_type"],
                replyToId = data["reply_to_id"],
                isForwarded = data["is_forwarded"] == "1",
                mentions = data["mentions"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            )
        } catch (_: Exception) {
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
