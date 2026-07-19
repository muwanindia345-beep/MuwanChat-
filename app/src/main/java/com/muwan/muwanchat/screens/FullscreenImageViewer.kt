package com.muwan.muwanchat.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.animateFloatAsState
import coil.compose.AsyncImage
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.data.MediaSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FullscreenImageViewer(
    model: Any,
    onDismiss: () -> Unit,
    onSendReply: ((String) -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // ── Reply-from-fullscreen state ──
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val replyFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun saveImage() {
        val url = model as? String ?: return
        isSaving = true
        scope.launch {
            val success = MediaSaver.saveImage(context, url)
            isSaving = false
            Toast.makeText(
                context,
                if (success) "Image saved to gallery" else "Save failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Android 9 aur neeche ke liye hi runtime permission chahiye — 10+ pe scoped storage khud handle karta hai
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveImage() else Toast.makeText(context, "Storage permission needed to save", Toast.LENGTH_SHORT).show()
    }

    fun onSaveClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImage()
        }
    }

    LaunchedEffect(isReplying) {
        if (isReplying) {
            delay(120)
            replyFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isReplying) {
                isReplying = false
                replyText = ""
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = model,
                contentDescription = "Full image",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offsetX = 0f
                            offsetY = 0f
                        })
                    }
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color(0x88000000), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            IconButton(
                onClick = { if (!isSaving) onSaveClick() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color(0x88000000), CircleShape)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Download, contentDescription = "Save to gallery", tint = Color.White)
                }
            }

            // ── Reply bar: photo ke niche hi tag + inline text field ──
            if (onSendReply != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReplying) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(replyFocusRequester),
                            placeholder = { Text("Reply...", color = Color(0xFF888888)) },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccent,
                                unfocusedBorderColor = Color(0xFF555555),
                                cursorColor = DarkAccent
                            ),
                            maxLines = 3
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = replyText.trim()
                                if (text.isNotBlank()) {
                                    onSendReply(text)
                                    replyText = ""
                                    isReplying = false
                                }
                            },
                            modifier = Modifier
                                .background(DarkAccent, CircleShape)
                                .size(42.dp)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send reply", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable { isReplying = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Reply, contentDescription = "Reply", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reply...", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

