package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.DocumentOpener
import com.muwan.muwanchat.data.MessageEntity
import com.muwan.muwanchat.data.MuwanChatDb
import kotlinx.coroutines.launch

private val mediaTabs = listOf("Photos", "Videos", "Documents")
private val mediaTypes = listOf("image", "video", "document")

@Composable
fun MediaScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val myToken = remember { AuthDataStore.getTokenBlocking(context) }

    var roomId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        roomId = db.conversationDao().getByUid(uid)?.roomId
    }

    val pagerState = rememberPagerState(pageCount = { mediaTabs.size })

    var fullscreenImage by remember { mutableStateOf<MessageEntity?>(null) }
    var fullscreenVideo by remember { mutableStateOf<MessageEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // ── Header — ChatHeader/GroupInfoScreen jaisa hi look ──
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
            Text(
                "Shared Media",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Tabs — orange sliding indicator, tab-click aur swipe dono sync ──
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = DarkHeader,
            contentColor = DarkAccent,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[pagerState.currentPage]),
                    color = DarkAccent
                )
            }
        ) {
            mediaTabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            title,
                            color = if (pagerState.currentPage == index) DarkAccent else Color(0xFF888888),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        if (roomId == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val type = mediaTypes[page]
                val messages by db.messageDao().observeMediaMessages(roomId!!, type)
                    .collectAsState(initial = emptyList())

                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Koi ${mediaTabs[page].lowercase()} nahi mila", color = Color(0xFF888888), fontSize = 14.sp)
                    }
                } else {
                    when (type) {
                        "image" -> PhotosGrid(messages) { fullscreenImage = it }
                        "video" -> VideosList(messages) { fullscreenVideo = it }
                        "document" -> DocumentsList(messages) { msg ->
                            scope.launch {
                                DocumentOpener.openDocument(
                                    context, msg.content, myToken, msg.fileName ?: "document", msg.mimeType
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Fullscreen viewers — chat wale jaise hi reuse, bas reply option nahi ──
    fullscreenImage?.let { msg ->
        FullscreenImageViewer(
            model = msg.content,
            onDismiss = { fullscreenImage = null }
        )
    }
    fullscreenVideo?.let { msg ->
        FullscreenVideoPlayer(
            url = msg.content,
            onDismiss = { fullscreenVideo = null }
        )
    }
}

@Composable
private fun PhotosGrid(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            AsyncImage(
                model = msg.content,
                contentDescription = "Photo",
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onTap(msg) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun VideosList(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
                    .clickable { onTap(msg) },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = msg.content,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun DocumentsList(messages: List<MessageEntity>, onTap: (MessageEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1A1A))
                    .clickable { onTap(msg) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    msg.fileName ?: "Document",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
