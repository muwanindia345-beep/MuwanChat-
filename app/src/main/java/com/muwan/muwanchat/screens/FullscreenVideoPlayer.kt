package com.muwan.muwanchat.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.data.MediaSaver
import com.muwan.muwanchat.data.VideoCacheProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FullscreenVideoPlayer(
    url: String,
    onDismiss: () -> Unit,
    onSendReply: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val replyFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun buildPlayer(ctx: android.content.Context): ExoPlayer {
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCacheProvider.get(ctx))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    playbackError = "Video load nahi ho paya. Network check karke retry karo."
                }
            })
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    var exoPlayer by remember { mutableStateOf(buildPlayer(context)) }

    fun retry() {
        exoPlayer.release()
        playbackError = null
        isBuffering = true
        exoPlayer = buildPlayer(context)
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    fun saveVideo() {
        isSaving = true
        scope.launch {
            val success = MediaSaver.saveVideo(context, url)
            isSaving = false
            Toast.makeText(
                context,
                if (success) "Video saved to gallery" else "Save failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveVideo() else Toast.makeText(context, "Storage permission needed to save", Toast.LENGTH_SHORT).show()
    }

    fun onSaveClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveVideo()
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                update = { view -> view.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering && playbackError == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (playbackError != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = playbackError ?: "",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { retry() }, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(" Retry", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

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

            if (onSendReply != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (isReplying) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .padding(bottom = 104.dp),
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
                                .wrapContentWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xCC2A2A2A))
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
