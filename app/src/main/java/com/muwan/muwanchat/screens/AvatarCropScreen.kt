package com.muwan.muwanchat.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// Temporary handoff for the picked image Uri between ProfileScreen and AvatarCropScreen.
// Consumed immediately on load, never held longer than one screen transition.
object AvatarTransfer {
    var pickedUri: Uri? = null
}

@Composable
fun AvatarCropScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }

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
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val size = minOf(bitmap.width, bitmap.height)
                        val x = (bitmap.width - size) / 2
                        val y = (bitmap.height - size) / 2
                        sourceBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
                    }
                }
                if (sourceBitmap == null) errorMsg = "Couldn't load image"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Couldn't load image"
            }
        }
        isLoading = false
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
                        scope.launch {
                            val base64 = withContext(Dispatchers.IO) {
                                val resized = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
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
                        Image(
                            bitmap = sourceBitmap!!.asImageBitmap(),
                            contentDescription = "Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(280.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            Text(
                "Photo circle ke andar jo dikh raha hai wahi save hoga",
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
