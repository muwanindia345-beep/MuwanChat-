package com.muwan.muwanchat.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWallpaperSheet(
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetWallpaper() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Wallpaper, contentDescription = "Set Wallpaper",
                    tint = DarkAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text("Set Wallpaper", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}
