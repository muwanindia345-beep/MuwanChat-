package com.muwan.muwanchat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object DocumentOpener {

    private val client = OkHttpClient()

    suspend fun openDocument(
        context: Context,
        url: String,
        token: String,
        fileName: String,
        mimeType: String?
    ) {
        val success = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false

                    val docsDir = File(context.cacheDir, "documents").apply { mkdirs() }
                    val safeName = fileName.ifBlank { "document" }
                    val file = File(docsDir, safeName)

                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }

                    withContext(Dispatchers.Main) { openLocalFile(context, file, fileName, mimeType) }
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
