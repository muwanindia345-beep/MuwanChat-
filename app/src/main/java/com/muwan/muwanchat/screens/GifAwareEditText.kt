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
