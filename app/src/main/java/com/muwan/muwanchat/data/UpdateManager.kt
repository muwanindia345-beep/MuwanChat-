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

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // Real-time progress ke saath APK download + install intent fire
    // karta hai. onProgress Dispatchers.Main pe safe call hai.
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        // SEC_FIX_V1: sirf https se APK download
        val parsedUrl = apkUrl.toHttpUrlOrNull()
        if (parsedUrl == null || !parsedUrl.isHttps) {
            throw Exception("Update link secure (https) nahi hai — update roka gaya.")
        }
        val response = downloadClient.newCall(Request.Builder().url(apkUrl).addHeader("Accept-Encoding", "identity").build()).execute()
        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

        val body = response.body ?: throw Exception("Empty response body")
        val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }

        // Install (download se alag) me extra space lagta hai — dexopt compile ke liye
        // aur purana version rollback-safety ke liye disk pe rakha jaata hai jab tak
        // naya verify na ho. Isliye kam se kam APK size ka 3x ya 150MB, jo bhi zyada ho.
        val minRequired = 150L * 1024 * 1024
        val requiredSpace = if (totalBytes > 0) maxOf(totalBytes * 3, minRequired) else minRequired
        val availableSpace = updatesDir.usableSpace
        if (availableSpace < requiredSpace) {
            val requiredMb = requiredSpace / (1024 * 1024)
            val availableMb = availableSpace / (1024 * 1024)
            throw InsufficientStorageException(
                "Storage kam hai — update install karne ke liye kam se kam ${requiredMb}MB free space chahiye, sirf ${availableMb}MB available hai. Kuch jagah free karke try karo."
            )
        }

        val apkFile = File(updatesDir, "MuwanChat-update.apk")

        body.byteStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val percent = ((totalRead * 100) / totalBytes).toInt()
                        withContext(Dispatchers.Main) { onProgress(percent) }
                    }
                }
            }
        }

        val verifyError = verifyApk(context, apkFile)
        if (verifyError != null) {
            apkFile.delete()
            throw Exception(verifyError)
        }

        withContext(Dispatchers.Main) {
            onProgress(100)
            installApk(context, apkFile)
        }
    }

    // SEC_FIX_V1: download hui APK hamare hi app ki honi chahiye -- same package + same signing key.
    // (Android install ke waqt bhi signature check karta hai; ye uske pehle hi galat file rok deta hai.)
    private fun verifyApk(context: Context, apkFile: File): String? {
        return try {
            val pm = context.packageManager
            val archive = archiveInfo(pm, apkFile.absolutePath)
                ?: return "Download hui update file kharab hai — dobara try karo."
            if (archive.packageName != context.packageName) {
                return "Update file is app ki nahi hai — update roka gaya."
            }
            val installed = signerHashes(installedInfo(pm, context.packageName))
            val incoming = signerHashes(archive)
            // Dono jagah signer padh paaye aur ek bhi match nahi -> galat key se bani APK
            if (installed.isNotEmpty() && incoming.isNotEmpty() && installed.intersect(incoming).isEmpty()) {
                return "Update ka signature match nahi hua — update roka gaya."
            }
            null
        } catch (e: Exception) {
            "Update verify nahi ho paya — dobara try karo."
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        else
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        else
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo
            when {
                si == null -> null
                si.hasMultipleSigners() -> si.apkContentsSigners
                else -> si.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        return sigs?.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet() ?: emptySet()
    }

    private fun installApk(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        // Yahi pe Android khud "Install unknown apps" / Play Protect scan
        // popup dikhayega — same signature hone se existing APK replace
        // ho jayega, data safe rahega.
    }
}
