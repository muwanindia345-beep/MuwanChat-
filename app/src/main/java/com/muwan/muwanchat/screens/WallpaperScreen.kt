package com.muwan.muwanchat.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.ChatWallpaperEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.WallpaperPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ─── Gallery image ko internal storage me copy karo (resized + compressed) ───
private fun saveWallpaperImage(context: Context, uri: Uri, roomId: String): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input)
        input.close()
        val maxDim = 1080
        val bitmap = if (original.width > maxDim || original.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
            Bitmap.createScaledBitmap(original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true)
        } else original

        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "wallpaper_$roomId.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

@Composable
fun WallpaperScreen(navController: NavController, roomId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context) }

    val current by db.chatWallpaperDao().observeByRoomId(roomId).collectAsState(initial = null)

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val path = withContext(Dispatchers.IO) { saveWallpaperImage(context, it, roomId) }
                if (path != null) {
                    db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, "image", path))
                }
            }
        }
    }

    fun applyPreset(type: String, id: String) {
        scope.launch { db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, type, id)) }
    }

    fun removeWallpaper() {
        scope.launch { db.chatWallpaperDao().deleteByRoomId(roomId) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Chat Wallpaper", color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // ── Preview ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            WallpaperPreviewBackground(current)
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            // ── Gallery + Remove row ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { galleryPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccent)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = { removeWallpaper() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05555))
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }

            Text("Solid Colors", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(WallpaperPresets.colors) { preset ->
                    val selected = current?.type == "color" && current?.value == preset.id
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(android.graphics.Color.parseColor(preset.hex)))
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) DarkAccent else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { applyPreset("color", preset.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Text("Gradients", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(WallpaperPresets.gradients) { preset ->
                    val selected = current?.type == "gradient" && current?.value == preset.id
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(android.graphics.Color.parseColor(preset.hexFrom)),
                                        Color(android.graphics.Color.parseColor(preset.hexTo))
                                    )
                                )
                            )
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) DarkAccent else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { applyPreset("gradient", preset.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Text("Patterns", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(100.dp).padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(WallpaperPresets.patterns) { preset ->
                    val selected = current?.type == "pattern" && current?.value == preset.id
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) DarkAccent else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { applyPreset("pattern", preset.id) }
                    ) {
                        PatternThumbnail(preset)
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White,
                                modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}

// ─── Preview box: current wallpaper render (color/gradient/pattern/image/default) ───
@Composable
fun WallpaperPreviewBackground(entity: ChatWallpaperEntity?) {
    when (entity?.type) {
        "color" -> {
            val preset = WallpaperPresets.colorById(entity.value)
            Box(Modifier.fillMaxSize().background(
                preset?.let { Color(android.graphics.Color.parseColor(it.hex)) } ?: DarkBg
            ))
        }
        "gradient" -> {
            val preset = WallpaperPresets.gradientById(entity.value)
            Box(
                Modifier.fillMaxSize().background(
                    if (preset != null) Brush.linearGradient(
                        listOf(
                            Color(android.graphics.Color.parseColor(preset.hexFrom)),
                            Color(android.graphics.Color.parseColor(preset.hexTo))
                        )
                    ) else Brush.linearGradient(listOf(DarkBg, DarkBg))
                )
            )
        }
        "pattern" -> {
            val preset = WallpaperPresets.patternById(entity.value)
            if (preset != null) PatternThumbnail(preset) else Box(Modifier.fillMaxSize().background(DarkBg))
        }
        "image" -> {
            AsyncImage(
                model = File(entity.value),
                contentDescription = "Wallpaper preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        else -> {
            Box(Modifier.fillMaxSize().background(DarkBg))
        }
    }
}

// ─── Pattern render: dots tiled on base color (light Canvas draw, no assets) ───
@Composable
fun PatternThumbnail(preset: com.muwan.muwanchat.data.PatternPreset) {
    val base = Color(android.graphics.Color.parseColor(preset.baseHex))
    val dot = Color(android.graphics.Color.parseColor(preset.dotHex))
    Canvas(modifier = Modifier.fillMaxSize().background(base)) {
        val spacing = 28.dp.toPx()
        val radius = 2.5.dp.toPx()
        var y = spacing / 2
        while (y < size.height) {
            var x = spacing / 2
            while (x < size.width) {
                drawCircle(color = dot, radius = radius, center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}
