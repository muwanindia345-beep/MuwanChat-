package com.muwan.muwanchat.data

// SEC_FIX_V1
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.muwan.muwanchat.network.BackendHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

// Chat mein aayi image/video ko URL se download karke seedha device ki
// Gallery (Pictures/MuwanChat, Movies/MuwanChat) mein save karta hai.
// Android 10+ (scoped storage) pe koi runtime permission nahi chahiye.
// Ab data stream hota hai (pehle poori video RAM mein load hoti thi -> bade video pe OOM).
object MediaSaver {

    private val client = OkHttpClient()

    suspend fun saveImage(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            withSource(context, url) { input, mime ->
                val (ext, outMime) = imageType(mime)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "MuwanChat_${System.currentTimeMillis()}.$ext")
                    put(MediaStore.MediaColumns.MIME_TYPE, outMime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MuwanChat")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                insertAndCopy(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, input)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveVideo(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            withSource(context, url) { input, _ ->
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "MuwanChat_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MuwanChat")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                insertAndCopy(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values, input)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // MediaStore mein entry banao, stream copy karo; beech mein fail ho to adhoori entry hata do.
    private fun insertAndCopy(
        resolver: ContentResolver,
        collection: Uri,
        values: ContentValues,
        input: InputStream
    ): Boolean {
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            val out = resolver.openOutputStream(uri)
            if (out == null) {
                resolver.delete(uri, null, null)
                false
            } else {
                out.use { input.copyTo(it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }
                true
            }
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            false
        }
    }

    private fun imageType(mime: String?): Pair<String, String> = when (mime?.lowercase()) {
        "image/png" -> "png" to "image/png"
        "image/webp" -> "webp" to "image/webp"
        "image/gif" -> "gif" to "image/gif"
        else -> "jpg" to "image/jpeg"
    }

    // Local copy ho to wahi, warna download stream. Token sirf apne backend ke URL par.
    private fun <T> withSource(context: Context, url: String, block: (InputStream, String?) -> T): T? {
        val local = LocalMedia.localFile(context, url)
        if (local != null) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(local.extension.lowercase())
            return local.inputStream().use { block(it, mime) }
        }
        val builder = Request.Builder().url(url)
        if (BackendHost.isBackend(url)) {
            val token = AuthDataStore.getTokenBlocking(context)
            if (token.isNotEmpty()) builder.addHeader("Authorization", "Bearer $token")
        }
        return client.newCall(builder.build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                null
            } else {
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                block(body.byteStream(), mime)
            }
        }
    }
}
