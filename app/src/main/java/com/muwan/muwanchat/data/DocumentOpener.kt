package com.muwan.muwanchat.data

// SEC_FIX_V1
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.muwan.muwanchat.network.BackendHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object DocumentOpener {

    private val client = OkHttpClient()

    // fileName message se aata hai (sender ka diya hua). "../../databases/x" jaisa naam
    // app ki apni files overwrite kar sakta tha -- isliye sirf aakhri naam rakhte hain.
    private fun safeFileName(raw: String?): String {
        val base = (raw ?: "")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it >= ' ' && it != '\u007f' }
            .trim()
            .trimStart('.')
            .take(120)
        return base.ifBlank { "document" }
    }

    private fun docFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "documents").apply { mkdirs() }
        val f = File(dir, safeFileName(fileName))
        if (!f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            throw SecurityException("Invalid document name")
        }
        return f
    }

    suspend fun openDocument(
        context: Context,
        url: String,
        token: String,
        fileName: String,
        mimeType: String?
    ) {
        val success = withContext(Dispatchers.IO) {
            try {
                val shownName = safeFileName(fileName)
                val localDoc = LocalMedia.localFile(context, url)
                if (localDoc != null) {
                    val copy0 = docFile(context, fileName)
                    localDoc.copyTo(copy0, overwrite = true)
                    withContext(Dispatchers.Main) { openLocalFile(context, copy0, shownName, mimeType) }
                    return@withContext true
                }
                val builder = Request.Builder().url(url)
                // Token sirf apne backend ko -- URL sender ka diya hua ho sakta hai
                if (token.isNotEmpty() && BackendHost.isBackend(url)) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                val request = builder.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false

                    val file = docFile(context, fileName)

                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }

                    withContext(Dispatchers.Main) { openLocalFile(context, file, shownName, mimeType) }
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        if (!success) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Could not open document", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLocalFile(context: Context, file: File, fileName: String, mimeType: String?) {
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: run {
            val ext = MimeTypeMap.getFileExtensionFromUrl(fileName)
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } ?: "*/*"

        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
