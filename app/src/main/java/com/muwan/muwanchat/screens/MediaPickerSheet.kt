package com.muwan.muwanchat.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(
    onDismiss: () -> Unit,
    onSelectPhoto: () -> Unit,
    onSelectVideo: () -> Unit,
    onSelectDocument: () -> Unit,
    onSelectMusic: () -> Unit,
    onSelectCamera: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkHeader) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            MediaPickerOption(Icons.Filled.CameraAlt, "Camera") { onSelectCamera(); onDismiss() }
            MediaPickerOption(Icons.Filled.Image, "Photo") { onSelectPhoto(); onDismiss() }
            MediaPickerOption(Icons.Filled.Videocam, "Video") { onSelectVideo(); onDismiss() }
            MediaPickerOption(Icons.Filled.Description, "Document") { onSelectDocument(); onDismiss() }
            MediaPickerOption(Icons.Filled.MusicNote, "Music") { onSelectMusic(); onDismiss() }
        }
    }
}

@Composable
private fun MediaPickerOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = DarkAccent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}
