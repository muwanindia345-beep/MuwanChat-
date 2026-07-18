package com.muwan.muwanchat.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.muwan.muwanchat.data.MediaSaver
import com.muwan.muwanchat.data.VideoCacheProvider
import kotlinx.coroutines.launch

@Composable
fun FullscreenVideoPlayer(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // Player load/error state -- pehle koi feedback nahi tha, ab
    // corrupt/unsupported-codec/network-drop cases pe user ko pata chalega
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    fun buildPlayer(ctx: android.content.Context): ExoPlayer {
        // Cache-aware datasource — pehli baar network se video aata hai aur disk pe
        // cache ho jata hai, dobara wahi video play karo to seedha disk se milega
        // (offline bhi chalega, data bhi kam lagega)
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

    // Android 9 aur neeche ke liye hi runtime permission chahiye — 10+ pe scoped storage khud handle karta hai
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
        }
    }
}
