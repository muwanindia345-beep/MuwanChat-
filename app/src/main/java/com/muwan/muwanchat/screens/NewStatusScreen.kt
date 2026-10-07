package com.muwan.muwanchat.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.network.CreateStatusBody
import com.muwan.muwanchat.util.isNetworkAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// STATUS_V2 / STATUS_V4 / STATUS_V5 -- naya status: text (colour background) ya photo / video (30 sec, 25 MB).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewStatusScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StatusRepository(context) }
    val focusRequester = remember { FocusRequester() }

    var text by remember { mutableStateOf("") }
    var colorIdx by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<PickedStatusMedia?>(null) }
    var caption by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val m = media

    fun handlePicked(uri: Uri?, isVideo: Boolean) {
        if (uri == null) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { readStatusMedia(context, uri, isVideo) }
            r.onSuccess {
                media = it
                caption = ""
                error = ""
            }.onFailure {
                error = it.message ?: "Yeh file nahi chun sakte."
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePicked(uri, false)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePicked(uri, true)
    }

    LaunchedEffect(m == null) {
        if (m == null) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val post: () -> Unit = onPost@{
        if (posting) return@onPost
        if (m == null) {
            val t = text.trim()
            if (t.isEmpty()) {
                error = "Write something first"
                return@onPost
            }
            posting = true
            error = ""
            scope.launch {
                val result = repo.create(
                    CreateStatusBody(type = "text", text = t, bg_color = StatusBgColors[colorIdx])
                )
                posting = false
                if (result.isSuccess) {
                    navController.popBackStack()
                } else {
                    error = result.exceptionOrNull()?.message ?: "Couldn't post status"
                }
            }
        } else {
            posting = true
            error = ""
            scope.launch {
                val token = AuthDataStore.getToken(context).first()
                if (token == null) {
                    error = "Please log in again"
                    posting = false
                    return@launch
                }
                if (!isNetworkAvailable(context)) {
                    error = "No internet connection"
                    posting = false
                    return@launch
                }
                val up = uploadStatusMedia(context, token, m)
                val url = up.getOrNull()
                if (url == null) {
                    error = up.exceptionOrNull()?.message ?: "Upload nahi ho paya"
                    posting = false
                    return@launch
                }
                val res = repo.create(
                    CreateStatusBody(
                        type = m.type,
                        text = caption.trim().ifBlank { null },
                        media_url = url,
                        mime_type = if (m.type == "image") "image/jpeg" else m.mime,
                        file_name = m.fileName
                    )
                )
                posting = false
                if (res.isSuccess) {
                    navController.popBackStack()
                } else {
                    error = res.exceptionOrNull()?.message ?: "Couldn't post status"
                }
            }
        }
    }

    val bg = parseStatusColor(StatusBgColors[colorIdx])
    val barBg = if (m == null) {
        Color(bg.red * 0.55f, bg.green * 0.55f, bg.blue * 0.55f, 1f)
    } else {
        Color(0xFF1A1A1A)
    }

    // WhatsApp jaisa: colour sirf content mein, neeche ki navigation bar solid kali rehti hai.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (m == null) bg else Color.Black)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    if (m != null) {
                        media = null
                        caption = ""
                        error = ""
                    } else {
                        navController.popBackStack()
                    }
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                if (m == null) {
                    Row {
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Filled.Image, contentDescription = "Add photo or video", tint = Color.White)
                        }
                        IconButton(onClick = { colorIdx = (colorIdx + 1) % StatusBgColors.size }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Change colour", tint = Color.White)
                        }
                    }
                }
            }

            if (m == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            if (it.length <= STATUS_MAX_TEXT) {
                                text = it
                                error = ""
                            }
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (text.isEmpty()) {
                                    Text(
                                        "Type a status",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                if (text.length >= STATUS_MAX_TEXT - 100) {
                    Text(
                        "${text.length}/$STATUS_MAX_TEXT",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp, bottom = 2.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = m.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (m.type == "video") {
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
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = {
                            if (it.length <= STATUS_CAPTION_MAX) caption = it
                        },
                        maxLines = 3,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (caption.isEmpty()) {
                                    Text(
                                        "Add a caption...",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 16.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            if (error.isNotEmpty()) {
                Text(
                    error,
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (m == null) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBgColors.forEachIndexed { i, hex ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(parseStatusColor(hex))
                                .border(2.dp, if (i == colorIdx) Color.White else Color.Transparent, CircleShape)
                                .clickable { colorIdx = i }
                        )
                    }
                }
            } else {
                Text(
                    if (m.type == "video") "Video \u00B7 " + formatStatusDuration(m.durationMs) else "Photo",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        DarkAccent.copy(
                            alpha = if (posting || (m == null && text.isBlank())) 0.6f else 1f
                        )
                    )
                    .clickable(enabled = !posting) { post() }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (posting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (posting) "Posting" else "Post",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            containerColor = DarkSheet
        ) {
            PickRow(Icons.Filled.Image, "Photo", "Gallery se photo chuno") {
                showPicker = false
                imagePicker.launch("image/*")
            }
            PickRow(Icons.Filled.Videocam, "Video", "Max 30 second, 25 MB") {
                showPicker = false
                videoPicker.launch("video/*")
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun PickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DarkAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Color(0xFF999999), fontSize = 13.sp)
        }
    }
}
