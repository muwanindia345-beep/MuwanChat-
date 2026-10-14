package com.muwan.muwanchat.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

class GifAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {

    var onContentReceived: ((Uri, String, () -> Unit) -> Unit)? = null

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(editorInfo) ?: return null

        EditorInfoCompat.setContentMimeTypes(
            editorInfo,
            arrayOf("image/gif", "image/png", "image/webp", "image/jpeg")
        )

        return InputConnectionCompat.createWrapper(ic, editorInfo) { inputContentInfo, flags, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
            ) {
                try {
                    inputContentInfo.requestPermission()
                } catch (e: Exception) {
                    return@createWrapper false
                }
            }
            val mime = inputContentInfo.description.getMimeType(0) ?: "image/gif"
            onContentReceived?.invoke(inputContentInfo.contentUri, mime) {
                inputContentInfo.releasePermission()
            }
            true
        }
    }
}


// Gboard suggestion strip se aayi image: screenshot/photo ya asli GIF/sticker/meme?
// true  = normal photo ki tarah bhejo (gallery jaisa "image", compression ke saath)
// false = GIF/sticker ki tarah bhejo (purana behaviour, koi badlaw nahi)
// Rule: image/gif hamesha GIF; jpeg hamesha photo; png/webp tab photo jab badi ho
// (screenshot/camera). Chhoti png/webp (sticker, emoji kitchen) sticker hi rehti hain.
private const val PHOTO_MIN_SIDE_PX = 900

internal fun shouldSendAsPhoto(context: Context, uri: Uri, mime: String): Boolean {
    val m = mime.lowercase()
    if (m == "image/gif") return false
    if (m == "image/jpeg" || m == "image/jpg") return true
    return try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
        maxOf(opts.outWidth, opts.outHeight) >= PHOTO_MIN_SIDE_PX
    } catch (e: Exception) {
        false
    }
}
