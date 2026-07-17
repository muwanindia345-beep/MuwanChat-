package com.muwan.muwanchat.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

// Temporary handoff for the picked image Uri between ProfileScreen and AvatarCropScreen.
// Consumed immediately on load, never held longer than one screen transition.
object AvatarTransfer {
    var pickedUri: Uri? = null
}

private val CIRCLE_SIZE_DP = 280.dp
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val OUTPUT_SIZE = 512

@Composable
fun AvatarCropScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val containerPx = with(density) { CIRCLE_SIZE_DP.toPx() }

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }

    // zoom = 1f means "cover" (circle fully filled, no gaps), zoom > 1 zooms in further.
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        val uri = AvatarTransfer.pickedUri
        AvatarTransfer.pickedUri = null
        if (uri == null) {
            errorMsg = "No image selected"
            isLoading = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    sourceBitmap = BitmapFactory.decodeStream(stream)
                }
                if (sourceBitmap == null) errorMsg = "Couldn't load image"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Couldn't load image"
            }
        }
        isLoading = false
    }

    // "Cover" base scale: shorter dimension container size se match hoti hai, taaki circle
    // hamesha poori tarah image se covered rahe (no gaps/margin andar kabhi nahi aayega).
    // Longer dimension container se bada rehta hai -- wo hissa circle ke bahar, dimmed
    // overlay ke peeche visible rehta hai (Instagram jaisa), aur user use pan/zoom kar sakta hai.
    fun coverScale(bitmap: Bitmap): Float = containerPx / min(bitmap.width, bitmap.height).toFloat()

    // Clamp pan so you can't drag the image completely away; at zoom=1 (cover) circle ke
    // andar hamesha image rehti hai, sirf non-square image ka extra bahar wala hissa hi pan hota hai.
    fun clampOffset(newOffset: Offset, currentZoom: Float, bitmap: Bitmap): Offset {
        val totalScale = coverScale(bitmap) * currentZoom
        val maxOffsetX = max(0f, (bitmap.width * totalScale - containerPx) / 2f)
        val maxOffsetY = max(0f, (bitmap.height * totalScale - containerPx) / 2f)
        return Offset(
            newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
            newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Cancel", color = Color.White, maxLines = 1)
                }
                Text(
                    "Set Profile Photo",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                TextButton(
                    enabled = sourceBitmap != null,
                    onClick = {
                        val bitmap = sourceBitmap ?: return@TextButton
                        val currentZoom = zoom
                        val currentOffset = offset
                        scope.launch {
                            val base64 = withContext(Dispatchers.IO) {
                                val totalScale = coverScale(bitmap) * currentZoom
                                val outputScale = totalScale * (OUTPUT_SIZE / containerPx)
                                val panOutputX = currentOffset.x * (OUTPUT_SIZE / containerPx)
                                val panOutputY = currentOffset.y * (OUTPUT_SIZE / containerPx)

                                // Map source bitmap onto the OUTPUT_SIZE x OUTPUT_SIZE square exactly
                                // as it's shown inside the crop circle - cover scale guarantees the
                                // circle area is always fully covered by image pixels, so this dark
                                // fill is just a safety fallback and should never actually show.
                                val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(output)
                                canvas.drawColor(android.graphics.Color.rgb(0x11, 0x11, 0x11))

                                val matrix = Matrix()
                                matrix.postScale(outputScale, outputScale)
                                val dx = OUTPUT_SIZE / 2f + panOutputX - (bitmap.width * outputScale) / 2f
                                val dy = OUTPUT_SIZE / 2f + panOutputY - (bitmap.height * outputScale) / 2f
                                matrix.postTranslate(dx, dy)

                                canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

                                val outputStream = ByteArrayOutputStream()
                                output.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                            }
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("cropped_avatar", base64)
                            navController.popBackStack()
                        }
                    }
                ) {
                    Text(
                        "Done",
                        color = if (sourceBitmap != null) DarkAccent else Color(0xFF555555),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = DarkAccent)
                    errorMsg.isNotEmpty() -> Text(errorMsg, color = Color.Red)
                    sourceBitmap != null -> {
                        val bitmap = sourceBitmap!!
                        val baseScale = coverScale(bitmap)
                        val wDp = with(density) { (bitmap.width * baseScale).toDp() }
                        val hDp = with(density) { (bitmap.height * baseScale).toDp() }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bitmap) {
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        val newZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        zoom = newZoom
                                        offset = clampOffset(offset + pan, newZoom, bitmap)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Poori image hamesha visible rehti hai (no clip) - sirf circle ke
                            // bahar wala hissa neeche wale dim overlay se dhak jaata hai.
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.None,
                                modifier = Modifier
                                    .size(wDp, hDp)
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )

                            // Dim overlay with a circular "hole" punched out (BlendMode.Clear)
                            // so only the crop area stays fully bright - Instagram-style.
                            // Offscreen compositing zaroori hai warna Clear puri screen clear kar dega.
                            ComposeCanvas(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            ) {
                                val circleRadius = containerPx / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                drawRect(color = Color.Black.copy(alpha = 0.65f))
                                drawCircle(
                                    color = Color.Transparent,
                                    radius = circleRadius,
                                    center = center,
                                    blendMode = BlendMode.Clear
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    radius = circleRadius,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Pinch se zoom, drag se move karo",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
