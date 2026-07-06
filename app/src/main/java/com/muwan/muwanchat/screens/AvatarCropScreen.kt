package com.muwan.muwanchat.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun AvatarCropScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val containerPx = with(density) { CIRCLE_SIZE_DP.toPx() }

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }

    // Pinch-to-zoom + drag-to-pan state, WhatsApp-style
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

    // Clamp pan so the visible window never goes past the image edges
    fun clampOffset(newOffset: Offset, currentZoom: Float, bitmap: Bitmap): Offset {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val baseScale = containerPx / min(bw, bh)
        val totalScale = baseScale * currentZoom
        val maxOffsetX = max(0f, (bw * totalScale - containerPx) / 2f)
        val maxOffsetY = max(0f, (bh * totalScale - containerPx) / 2f)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Cancel", color = Color.White)
                }
                Text("Set Profile Photo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(
                    enabled = sourceBitmap != null,
                    onClick = {
                        val bitmap = sourceBitmap ?: return@TextButton
                        val currentZoom = zoom
                        val currentOffset = offset
                        scope.launch {
                            val base64 = withContext(Dispatchers.IO) {
                                val bw = bitmap.width.toFloat()
                                val bh = bitmap.height.toFloat()
                                val baseScale = containerPx / min(bw, bh)
                                val totalScale = baseScale * currentZoom
                                val visibleSide = (containerPx / totalScale).coerceAtMost(min(bw, bh))

                                val centerX = (bw / 2f - currentOffset.x / totalScale)
                                    .coerceIn(visibleSide / 2f, bw - visibleSide / 2f)
                                val centerY = (bh / 2f - currentOffset.y / totalScale)
                                    .coerceIn(visibleSide / 2f, bh - visibleSide / 2f)

                                val side = visibleSide.toInt().coerceAtLeast(1)
                                val left = (centerX - visibleSide / 2f).toInt()
                                    .coerceIn(0, (bw - side).toInt().coerceAtLeast(0))
                                val top = (centerY - visibleSide / 2f).toInt()
                                    .coerceIn(0, (bh - side).toInt().coerceAtLeast(0))

                                val cropped = Bitmap.createBitmap(bitmap, left, top, side, side)
                                val resized = Bitmap.createScaledBitmap(cropped, 512, 512, true)
                                val outputStream = ByteArrayOutputStream()
                                resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
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
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> CircularProgressIndicator(color = DarkAccent)
                    errorMsg.isNotEmpty() -> Text(errorMsg, color = Color.Red)
                    sourceBitmap != null -> {
                        val bitmap = sourceBitmap!!
                        Box(
                            modifier = Modifier
                                .size(CIRCLE_SIZE_DP)
                                .clip(CircleShape)
                                .background(Color(0xFF111111))
                                .pointerInput(bitmap) {
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        val newZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        zoom = newZoom
                                        offset = clampOffset(offset + pan, newZoom, bitmap)
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }
                    }
                }
            }

            Text(
                "Pinch to zoom, drag to move — circle ke andar jo dikhega wahi save hoga",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
