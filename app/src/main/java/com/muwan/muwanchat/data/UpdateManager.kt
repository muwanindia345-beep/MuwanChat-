package com.muwan.muwanchat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.muwan.muwanchat.BuildConfig
import com.muwan.muwanchat.MainActivity
import com.muwan.muwanchat.R
import com.muwan.muwanchat.network.AppVersionInfo
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class InsufficientStorageException(message: String) : Exception(message)

object UpdateManager {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_SEEN_CODE = "last_seen_version_code"
    private const val CHANNEL_ID = "update_channel"
    const val EXTRA_OPEN_UPDATE_SCREEN = "open_update_screen"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSeenVersionCode(context: Context): Int =
        prefs(context).getInt(KEY_LAST_SEEN_CODE, BuildConfig.VERSION_CODE)

    fun markVersionSeen(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_LAST_SEEN_CODE, versionCode).apply()
    }

    // null = no update ya check fail hua (offline/backend down)
    suspend fun checkForUpdate(context: Context): AppVersionInfo? {
        // BETA_NO_INSTALLER: beta GitHub se browser ke zariye update hota hai, in-app installer nahi
        if (BuildConfig.FLAVOR == "beta") return null
        return try {
            val info = RetrofitClient.appApi.getVersion()
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        } catch (_: Exception) {
            null
        }
    }

    fun hasUnseenUpdate(context: Context, info: AppVersionInfo): Boolean =
        info.versionCode > getLastSeenVersionCode(context)

    // Offline/background user ko yeh notification milega
    fun showUpdateNotification(context: Context, info: AppVersionInfo) {
        if (BuildConfig.FLAVOR == "beta") return // BETA_NO_INSTALLER
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_UPDATE_SCREEN, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update Available")
            .setContentText("Please update it for exciting new features, improvements & more.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1001, notification)
    }

    // LANDING_PAGE_UPDATE: in-app APK download/install hata diya. Update button
    // official landing page kholta hai, wahan se browser APK download karta hai.
    // URL sirf BuildConfig se aata hai (backend/push se aaya link use nahi hota).
    fun openUpdatePage(context: Context) {
        val url = BuildConfig.UPDATE_PAGE_URL
        if (url.isBlank()) return
        try {
            // LANDING_VERSION_V1: tell the landing page which version is installed
            val uri = Uri.parse(url).buildUpon()
                .appendQueryParameter("v", BuildConfig.VERSION_NAME)
                .appendQueryParameter("c", BuildConfig.VERSION_CODE.toString())
                .build()
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
}
