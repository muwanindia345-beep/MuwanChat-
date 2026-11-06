package com.muwan.muwanchat.data

// GUEST_MEDIA
// Guest E2EE media: file phone par encrypt -> /guest/media (temporary blob) -> key + blob id E2EE "media" envelope se.
// Local copy LocalMedia ke folder me ek nakli URL (HOST + mid + ext) ke naam se rehti hai, isliye saare purane
// bubbles/viewers/players bina badlaav chalte hain.
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import com.muwan.muwanchat.screens.nowIso
import com.muwan.muwanchat.util.isNetworkAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GuestMedia {

    const val HOST = "https://guestmedia.invalid/"
    private const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_ATTEMPTS = 40
    private val TYPES = setOf("image", "gif", "video", "audio", "music", "document")
    private val HEX32 = Regex("^[0-9a-f]{32}$")

    private val rng = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val baseUrl: String get() = com.muwan.muwanchat.BuildConfig.BACKEND_URL.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun isGuestUrl(url: String?): Boolean = url != null && url.startsWith(HOST)

    fun label(type: String): String = when (type) {
        "image" -> "📷 Photo"
        "gif" -> "GIF"
        "video" -> "🎥 Video"
        "audio" -> "🎤 Voice message"
        "music" -> "🎵 Music"
        else -> "📄 Document"
    }

    private fun extFor(fileName: String?, mime: String?): String {
        val dot = (fileName ?: "").lastIndexOf('.')
        if (dot >= 0) {
            val e = fileName!!.substring(dot + 1).lowercase()
            if (e.length in 1..5 && e.all { it.isLetterOrDigit() }) return e
        }
        return when ((mime ?: "").lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "audio/mp4" -> "m4a"
            "audio/mpeg" -> "mp3"
            "application/pdf" -> "pdf"
            else -> "bin"
        }
    }

    fun urlFor(mid: String, ext: String): String = HOST + mid + "." + ext

    private fun fileOf(app: Context, url: String): File = LocalMedia.fileFor(app, url)

    private fun toast(app: Context, msg: String) {
        scope.launch(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
    }

    // ───────── crypto (blob) ─────────
    private fun aead(encrypt: Boolean, key: ByteArray, nonce: ByteArray, mid: String, input: ByteArray): ByteArray {
        val c = ChaCha20Poly1305()
        c.init(
            encrypt,
            AEADParameters(KeyParameter(key), 128, nonce, ("muwan-guest-media-v1|" + mid).toByteArray(Charsets.UTF_8))
        )
        val out = ByteArray(c.getOutputSize(input.size))
        var n = c.processBytes(input, 0, input.size, out, 0)
        n += c.doFinal(out, n)
        return out.copyOf(n)
    }

    // ───────── send ─────────
    private fun queryName(app: Context, uri: Uri): String {
        var name = "file"
        try {
            app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
            }
        } catch (_: Exception) {
        }
        return name
    }

    private fun readLimited(app: Context, uri: Uri): ByteArray? {
        val ins = app.contentResolver.openInputStream(uri) ?: return null
        ins.use {
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                bos.write(buf, 0, n)
            }
            return bos.toByteArray()
        }
    }

    private fun compressImage(app: Context, uri: Uri): ByteArray? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 2560 || o.outHeight / sample > 2560) sample *= 2
            val src = app.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val maxDim = 1280
            val bmp = if (src.width > maxDim || src.height > maxDim) {
                val r = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
                Bitmap.createScaledBitmap(src, (src.width * r).toInt().coerceAtLeast(1), (src.height * r).toInt().coerceAtLeast(1), true)
            } else src
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            bos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    // Picker/camera/voice/GIF se aayi file bhejo. Bubble turant dikhta hai, upload background me.
    suspend fun send(
        context: Context, uri: Uri, displayType: String, roomId: String, myUid: String,
        receiverUid: String, receiverUsername: String,
        skipCompression: Boolean = false, mimeOverride: String? = null, nameOverride: String? = null
    ) {
        val app = context.applicationContext
        if (displayType !in TYPES) return
        val db = MuwanChatDb.get(app, myUid)
        val (plain, mime, fileName) = withContext(Dispatchers.IO) {
            val name = nameOverride ?: queryName(app, uri)
            val guessed = mimeOverride ?: app.contentResolver.getType(uri) ?: "application/octet-stream"
            val compress = displayType == "image" && !skipCompression && guessed != "image/gif"
            if (compress) {
                val b = compressImage(app, uri)
                if (b != null) Triple(b, "image/jpeg", name.substringBeforeLast('.', name) + ".jpg")
                else Triple(readLimited(app, uri), guessed, name)
            } else Triple(readLimited(app, uri), guessed, name)
        }
        if (plain == null || plain.isEmpty()) {
            toast(app, "File too large (max 20 MB) or unreadable")
            return
        }
        val id = UUID.randomUUID().toString()
        val url = urlFor(id, extFor(fileName, mime))
        val saved = withContext(Dispatchers.IO) {
            try { fileOf(app, url).writeBytes(plain); true } catch (_: Exception) { false }
        }
        if (!saved) { toast(app, "Storage error"); return }
        LocalMedia.version.update { it + 1 }

        val online = isNetworkAvailable(app)
        ChatRepository.recordMessage(
            db = db, id = id, roomId = roomId, senderUid = myUid, receiverUid = receiverUid,
            content = url, type = displayType, createdAt = nowIso(), myUid = myUid,
            otherUsername = receiverUsername, status = if (online) "UPLOADING" else "FAILED",
            fileName = fileName, mimeType = mime
        )
        if (!online) return
        UploadProgressTracker.start(id)
        try {
            val err = transmit(app, id, receiverUid, plain, displayType, fileName, mime, null)
            db.messageDao().updateStatus(id, if (err == null) "SENT" else "FAILED")
            if (err != null) toast(app, err)
        } catch (e: CancellationException) {
            db.messageDao().updateStatus(id, "FAILED")
            throw e
        } catch (e: Exception) {
            db.messageDao().updateStatus(id, "FAILED")
            toast(app, "Media not sent: " + (e.message ?: "error"))
        } finally {
            UploadProgressTracker.clear(id)
        }
    }

    // FAILED bubble par tap: local copy se dobara bhejo (same message id).
    suspend fun retry(
        context: Context, myUid: String, id: String, toUid: String, type: String,
        fileName: String?, mime: String?, replyTo: String?
    ): Boolean {
        val app = context.applicationContext
        val db = MuwanChatDb.get(app, myUid)
        val m = db.messageDao().getById(id) ?: return false
        if (!isGuestUrl(m.content)) return false
        val f = fileOf(app, m.content)
        val plain = withContext(Dispatchers.IO) { if (f.isFile) f.readBytes() else null }
        if (plain == null || plain.isEmpty()) return false
        UploadProgressTracker.start(id)
        return try {
            val err = transmit(app, id, toUid, plain, type, fileName ?: m.fileName ?: "file", mime ?: m.mimeType ?: "application/octet-stream", replyTo)
            if (err != null) toast(app, err)
            err == null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            UploadProgressTracker.clear(id)
        }
    }

    // null = ho gaya, warna user ko dikhane layak error
    private suspend fun transmit(
        app: Context, mid: String, toUid: String, plain: ByteArray, type: String,
        fileName: String, mime: String, replyTo: String?
    ): String? {
        if (plain.size > MAX_BYTES) return "File too large (max 20 MB)"
        val key = ByteArray(32).also { rng.nextBytes(it) }
        val nonce = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = withContext(Dispatchers.Default) { aead(true, key, nonce, mid, plain) }
        val (blobId, upErr) = withContext(Dispatchers.IO) { upload(app, cipher) }
        if (blobId == null) return upErr ?: "Upload failed"
        UploadProgressTracker.update(mid, 80)
        val body = JSONObject()
            .put("u", blobId)
            .put("k", Base64.encodeToString(key, Base64.NO_WRAP))
            .put("n", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .put("t", type)
            .put("f", fileName.take(120))
            .put("m", mime.take(80))
            .put("z", plain.size)
            .put("ts", System.currentTimeMillis())
        if (!replyTo.isNullOrBlank()) body.put("r", replyTo)
        val ok = GuestChat.sendMedia(app, toUid, mid, body)
        if (!ok) {
            withContext(Dispatchers.IO) { release(app, blobId) }   // envelope nahi gaya: blob server par na pade
            return "Media not sent (check connection)"
        }
        return null
    }

    private suspend fun token(app: Context): String? {
        if (GuestSession.ensure(app) !is GuestSession.Result.Ok) return null
        return GuestStore.token(app)
    }

    private suspend fun upload(app: Context, cipher: ByteArray): Pair<String?, String?> {
        return try {
            val tk = token(app) ?: return null to "Guest session expired, reopen the app"
            val req = Request.Builder()
                .url(baseUrl + "/guest/media?readers=1")
                .header("Authorization", "Bearer " + tk)
                .post(cipher.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string() ?: ""
                if (!r.isSuccessful) {
                    val msg = try { JSONObject(txt).optString("error") } catch (_: Exception) { "" }
                    return null to (if (msg.isNotBlank()) msg else "Upload failed (HTTP " + r.code + ")")
                }
                val id = try { JSONObject(txt).optString("id") } catch (_: Exception) { "" }
                if (HEX32.matches(id)) id to null else null to "Upload failed"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null to "Upload failed (check connection)"
        }
    }

    private suspend fun release(app: Context, blobId: String) {
        try {
            val tk = token(app) ?: return
            val req = Request.Builder()
                .url(baseUrl + "/guest/media/" + blobId)
                .header("Authorization", "Bearer " + tk)
                .delete()
                .build()
            http.newCall(req).execute().close()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // ───────── receive ─────────
    private fun prefs(app: Context) = app.getSharedPreferences("guest_media_pending", Context.MODE_PRIVATE)

    // media envelope ka body validate karke message row banane layak (type, url, name, mime) deta hai
    class Incoming(val type: String, val url: String, val fileName: String, val mime: String, val replyTo: String?)

    fun parseIncoming(mid: String, body: JSONObject): Incoming? {
        val type = body.optString("t")
        if (type !in TYPES) return null
        if (!HEX32.matches(body.optString("u"))) return null
        val key = try { Base64.decode(body.optString("k"), Base64.NO_WRAP) } catch (_: Exception) { return null }
        val nonce = try { Base64.decode(body.optString("n"), Base64.NO_WRAP) } catch (_: Exception) { return null }
        if (key.size != 32 || nonce.size != 12) return null
        val name = body.optString("f").trim().take(120).ifBlank { "file" }
        val mime = body.optString("m").trim().take(80).ifBlank { "application/octet-stream" }
        return Incoming(type, urlFor(mid, extFor(name, mime)), name, mime, body.optString("r").ifBlank { null })
    }

    fun enqueue(context: Context, mid: String, body: JSONObject) {
        val app = context.applicationContext
        val rec = JSONObject(body.toString()).put("a", 0)
        prefs(app).edit().putString(mid, rec.toString()).apply()
        resume(app)
    }

    // Pending downloads (naya envelope, ya app band hone se adhoore) -- connect par bhi chalta hai
    fun resume(context: Context) {
        val app = context.applicationContext
        val all = prefs(app).all
        for ((mid, v) in all) {
            val json = (v as? String) ?: continue
            if (!running.add(mid)) continue
            scope.launch {
                try {
                    gate.withPermit { fetch(app, mid, json) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                } finally {
                    running.remove(mid)
                }
            }
        }
    }

    private fun drop(app: Context, mid: String) { prefs(app).edit().remove(mid).apply() }

    private fun bump(app: Context, mid: String, rec: JSONObject) {
        val a = rec.optInt("a") + 1
        if (a >= MAX_ATTEMPTS) drop(app, mid)
        else prefs(app).edit().putString(mid, rec.put("a", a).toString()).apply()
    }

    private suspend fun fetch(app: Context, mid: String, json: String) {
        val rec = JSONObject(json)
        val inc = parseIncoming(mid, rec) ?: run { drop(app, mid); return }
        val dest = fileOf(app, inc.url)
        val blobId = rec.optString("u")
        if (dest.isFile && dest.length() > 0L) {
            drop(app, mid); release(app, blobId); return
        }
        val tk = token(app) ?: return
        try {
            val req = Request.Builder()
                .url(baseUrl + "/guest/media/" + blobId)
                .header("Authorization", "Bearer " + tk)
                .build()
            val cipher: ByteArray? = http.newCall(req).execute().use { r ->
                if (r.code == 404) { drop(app, mid); return@use null }      // expire / delete ho chuka
                if (!r.isSuccessful) { bump(app, mid, rec); return@use null }
                val len = r.body?.contentLength() ?: -1L
                if (len > MAX_BYTES + 64) { drop(app, mid); return@use null }
                r.body?.bytes()
            }
            if (cipher == null) return
            val key = Base64.decode(rec.getString("k"), Base64.NO_WRAP)
            val nonce = Base64.decode(rec.getString("n"), Base64.NO_WRAP)
            val plain = try {
                withContext(Dispatchers.Default) { aead(false, key, nonce, mid, cipher) }
            } catch (_: Exception) {
                drop(app, mid); return          // tamper / kharab: dobara try bekaar
            }
            if (plain.size > MAX_BYTES) { drop(app, mid); return }
            val part = File(dest.parentFile, dest.name + "." + System.nanoTime() + ".part")
            part.writeBytes(plain)
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) { part.delete(); bump(app, mid, rec); return }
            LocalMedia.version.update { it + 1 }
            drop(app, mid)
            release(app, blobId)               // "maine le liya": blob server se saaf
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            bump(app, mid, rec)
        }
    }
}
