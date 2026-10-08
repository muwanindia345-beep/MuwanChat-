package com.muwan.muwanchat.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.muwan.muwanchat.data.VideoCacheProvider
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.UploadMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream

// STATUS_V5 -- photo / video status ke helpers: file check, image compress, upload, video player.

const val STATUS_VIDEO_MAX_MS = 120_000L                      // video status max 2 minutes
const val STATUS_VIDEO_MAX_BYTES = 25L * 1024L * 1024L        // server (multer) bhi 25 MB par rokta hai
const val STATUS_CAPTION_MAX = 300                            // backend caption limit
private const val STATUS_IMAGE_MAX_BASE64 = 3_900_000         // server image limit 4,000,000 chars

data class PickedStatusMedia(
    val uri: Uri,
    val type: String,        // image | video
    val durationMs: Long,
    val fileName: String,
    val mime: String,
    val sizeBytes: Long
)

fun formatStatusDuration(ms: Long): String {
    val s = (ms + 500L) / 1000L
    return "%d:%02d".format(s / 60L, s % 60L)
}

// Gallery se chuni file ki jaanch. Video: 2 minute aur 25 MB se zyada nahi.
// IO thread par chalao (MediaMetadataRetriever).
fun readStatusMedia(context: Context, uri: Uri, isVideo: Boolean): Result<PickedStatusMedia> {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
    var name = if (isVideo) "status.mp4" else "status.jpg"
    var size = -1L
    try {
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) c.getString(ni)?.let { name = it }
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    } catch (_: Exception) {
    }

    if (!isVideo) {
        return Result.success(PickedStatusMedia(uri, "image", 0L, name, mime, size))
    }

    if (size > STATUS_VIDEO_MAX_BYTES) {
        return Result.failure(Exception("Video is larger than 25 MB. Please choose a smaller video."))
    }
    var duration = 0L
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        duration = 0L
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
    if (duration <= 0L) {
        return Result.failure(Exception("This video could not be opened."))
    }
    if (duration > STATUS_VIDEO_MAX_MS + 500L) {
        return Result.failure(Exception("Video cannot be longer than 2 minutes."))
    }
    return Result.success(PickedStatusMedia(uri, "video", duration, name, mime, size))
}

// Photo: 1280px tak chhoti, EXIF rotation theek, JPEG; server limit (~3 MB) ke andar laane tak quality ghatati hai.
fun compressStatusImage(context: Context, uri: Uri): Pair<String, String>? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val maxDim = 1280
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

    val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
    var bmp: Bitmap = if (ratio < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    } else {
        decoded
    }

    val degrees = try {
        resolver.openInputStream(uri)?.use { s ->
            when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }
    if (degrees != 0) {
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    var quality = 80
    while (true) {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        if (b64.length <= STATUS_IMAGE_MAX_BASE64 || quality <= 40) {
            return b64 to "image/jpeg"
        }
        quality -= 15
    }
}

// Video ko poori file memory mein padhe bina stream karta hai.
class StatusUriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mime: String,
    private val length: Long
) : RequestBody() {
    override fun contentType() = mime.toMediaTypeOrNull()

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sink.write(buf, 0, n)
            }
        }
    }
}

// Maujooda chat upload endpoints hi use hote hain (image: /chat/upload, video: /chat/upload-video).
// Success par media ka URL milta hai.
suspend fun uploadStatusMedia(context: Context, token: String, media: PickedStatusMedia): Result<String> =
    withContext(Dispatchers.IO) {
        try {
            val res = if (media.type == "image") {
                val packed = compressStatusImage(context, media.uri)
                    ?: return@withContext Result.failure<String>(Exception("Photo khul nahi payi."))
                RetrofitClient.chatApi.uploadMedia(
                    "Bearer $token",
                    UploadMediaRequest(
                        filename = "status_" + System.currentTimeMillis() + ".jpg",
                        mime_type = packed.second,
                        data = packed.first,
                        category = "image"
                    )
                )
            } else {
                val bytes = context.contentResolver.openInputStream(media.uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure<String>(Exception("Video khul nahi payi."))
                if (bytes.isEmpty()) {
                    return@withContext Result.failure<String>(Exception("Video is empty."))
                }
                if (bytes.size > STATUS_VIDEO_MAX_BYTES) {
                    return@withContext Result.failure<String>(Exception("Video is larger than 25 MB."))
                }
                val body = bytes.toRequestBody(media.mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("video", media.fileName, body)
                RetrofitClient.chatApi.uploadVideo("Bearer $token", part)
            }
            val url = res.body()?.url
            if (res.isSuccessful && !url.isNullOrBlank()) {
                Result.success(url)
            } else {
                val msg = if (res.code() == 413) "File bahut badi hai." else "Upload nahi ho paya. Dobara try karo."
                Result.failure<String>(Exception(msg))
            }
        } catch (e: Exception) {
            android.util.Log.e("StatusUpload", "status upload failed", e)
            Result.failure<String>(Exception("Upload nahi ho paya. Net check karo."))
        }
    }

// Viewer ka video: khud chalta hai, progress bar video ki length (max 2 min) ke hisaab se, khatam hote hi onEnded.
@Composable
fun StatusVideoPlayer(
    url: String,
    paused: Boolean,
    onProgress: (Float) -> Unit,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    val progressCb by rememberUpdatedState(onProgress)
    val endedCb by rememberUpdatedState(onEnded)
    var buffering by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    var appPaused by remember { mutableStateOf(false) }
    val finished = remember(url) { booleanArrayOf(false) }

    val exo = remember(url) {
        val cacheFactory = CacheDataSource.Factory()
            .setCache(VideoCacheProvider.get(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !finished[0]) {
                    finished[0] = true
                    endedCb()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                failed = true
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // App background mein jaye to video ruk jaye
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) appPaused = true
            if (event == Lifecycle.Event.ON_RESUME) appPaused = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exo, paused, appPaused) {
        exo.playWhenReady = !(paused || appPaused)
    }

    LaunchedEffect(exo) {
        while (true) {
            delay(50)
            val d = exo.duration
            if (d > 0L) {
                val cap = minOf(d, STATUS_VIDEO_MAX_MS)
                val pos = exo.currentPosition
                progressCb((pos.toFloat() / cap.toFloat()).coerceIn(0f, 1f))
                if (pos >= cap && !finished[0]) {
                    finished[0] = true
                    endedCb()
                    break
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exo
                }
            },
            update = { it.player = exo },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering && !failed) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (failed) {
            Text(
                "Video load nahi ho payi",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// Preview: video ka pehla frame + beech mein sirf ek play button. Koi controller nahi.
// Tap: play / pause. Khatam hone par ya app background jaane par wapas play button.
@Composable
fun StatusVideoPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember(uri) { mutableStateOf(false) }

    val exo = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    exo.seekTo(0L)
                    exo.playWhenReady = false
                    playing = false
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) playing = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exo, playing) { exo.playWhenReady = playing }

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { playing = !playing }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exo
                }
            },
            update = { it.player = exo },
            modifier = Modifier.fillMaxSize()
        )
        if (!playing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}
