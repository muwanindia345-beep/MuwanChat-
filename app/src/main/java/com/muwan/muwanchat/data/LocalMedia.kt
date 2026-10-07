package com.muwan.muwanchat.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// Chat / group media ki local copy (phone ke internal storage mein).
// File ka naam server URL ke hash se banta hai, isliye Room DB mein koi naya
// column nahi chahiye, aur server sync message row replace kare to bhi mapping
// nahi tootti. noBackupFilesDir: internal hai, lekin Auto Backup mein nahi jaata.
object LocalMedia {
    private const val DIR = "chat_media"
    // Sirf pichhle 7 din ke messages auto-download hote hain (purane history pe data/storage na jaaye)
    private const val AUTO_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
    private val MEDIA_TYPES = setOf("image", "gif", "video", "audio", "music", "document")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)                       // ek saath max 2 downloads
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var appContext: Context? = null

    // Koi bhi download / copy poori hote hi badhta hai -> bubbles local file par switch ho jaate hain
    val version = MutableStateFlow(0)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun isHttp(s: String?): Boolean =
        s != null && (s.startsWith("http://") || s.startsWith("https://"))

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // Extension URL se (Coil ko video thumbnail ke liye .mp4 jaisa extension chahiye)
    private fun extOf(url: String): String {
        val last = try { Uri.parse(url).lastPathSegment ?: "" } catch (_: Exception) { "" }
        val dot = last.lastIndexOf('.')
        if (dot < 0) return "bin"
        val e = last.substring(dot + 1).lowercase(Locale.ROOT)
        return if (e.length in 1..5 && e.all { it.isLetterOrDigit() }) e else "bin"
    }

    private fun dir(context: Context): File =
        File(context.noBackupFilesDir, DIR).apply { mkdirs() }

    fun fileFor(context: Context, url: String): File =
        File(dir(context), sha1(url) + "." + extOf(url))

    // Local copy poori hai to File, warna null
    fun localFile(context: Context, url: String?): File? {
        if (url == null || !isHttp(url)) return null
        val f = fileFor(context, url)
        return if (f.isFile && f.length() > 0L) f else null
    }

    fun exists(context: Context, url: String?): Boolean = localFile(context, url) != null

    fun uri(context: Context, url: String): Uri =
        localFile(context, url)?.let { Uri.fromFile(it) } ?: Uri.parse(url)

    // Coil ke liye: local file ho to File, warna URL. Download poora hote hi khud update.
    @Composable
    fun rememberModel(url: String): Any {
        val context = LocalContext.current
        val ver by version.collectAsState()
        return remember(url, ver) {
            val m: Any = localFile(context, url) ?: url
            m
        }
    }

    private fun writeAtomically(dest: File, input: InputStream, expected: Long = -1L): Boolean {
        val part = File(dest.parentFile, dest.name + "." + System.nanoTime() + ".part")
        return try {
            var written = 0L
            input.use { ins ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                    }
                }
            }
            if (written <= 0L || (expected >= 0L && written != expected)) {
                part.delete()
                false
            } else {
                if (dest.exists()) dest.delete()
                part.renameTo(dest)
            }
        } catch (_: Exception) {
            part.delete()
            false
        }
    }

    private fun download(context: Context, url: String, dest: File): Boolean {
        return try {
            val b = Request.Builder().url(url)
            // Token sirf apne server ke media ko; Cloudinary jaise third party ko nahi
            if (url.contains("/chat/media/")) {
                val token = AuthDataStore.getTokenBlocking(context)
                if (token.isNotEmpty()) b.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                writeAtomically(dest, body.byteStream(), body.contentLength())
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isRecent(createdAt: String): Boolean {
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            val t = f.parse(createdAt.take(19))?.time ?: return true
            System.currentTimeMillis() - t <= AUTO_WINDOW_MS
        } catch (_: Exception) {
            true
        }
    }

    // Naya ya sync hua media message -> background mein local copy bana lo
    fun autoDownload(type: String, content: String, createdAt: String, deleted: Boolean = false) {
        val ctx = appContext ?: return
        if (deleted || type !in MEDIA_TYPES || !isHttp(content)) return
        if (!isRecent(createdAt)) return
        val dest = fileFor(ctx, content)
        if (dest.isFile && dest.length() > 0L) return
        if (!inFlight.add(content)) return
        scope.launch {
            try {
                gate.withPermit {
                    if (download(ctx, content, dest)) version.update { it + 1 }
                }
            } finally {
                inFlight.remove(content)
            }
        }
    }

    fun autoDownloadAll(list: List<MessageEntity>) {
        for (m in list) autoDownload(m.type, m.content, m.createdAt, m.deleted)
    }

    // Bhejne wale ka apna copy: upload hone ke baad (row mein abhi local uri hai) file ko
    // server URL ke naam se rakh lo. Fail ho to bhejna kabhi nahi rukta.
    suspend fun saveSenderCopy(context: Context, db: MuwanChatDb, messageId: String, serverUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                if (!isHttp(serverUrl)) return@withContext
                val dest = fileFor(context, serverUrl)
                if (dest.isFile && dest.length() > 0L) return@withContext
                val src = db.messageDao().getById(messageId)?.content ?: return@withContext
                if (isHttp(src)) return@withContext
                val input = context.contentResolver.openInputStream(Uri.parse(src)) ?: return@withContext
                if (writeAtomically(dest, input)) version.update { it + 1 }
            } catch (_: Exception) {
            }
        }
    }
}
