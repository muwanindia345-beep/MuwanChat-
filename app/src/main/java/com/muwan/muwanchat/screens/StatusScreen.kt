package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.StatusFeed
import com.muwan.muwanchat.data.StatusItem
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.data.StatusTime
import com.muwan.muwanchat.data.StatusUser
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// STATUS_V2 / STATUS_V3 -- offline-first: memory se turant, phir cache, phir (zaroorat par) server se refresh.
@Composable
fun StatusScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StatusRepository(context) }

    var feed by remember { mutableStateOf<StatusFeed?>(StatusMemory.feed) }
    var loaded by remember { mutableStateOf(StatusMemory.feed != null) }
    var myAvatar by remember { mutableStateOf<String?>(StatusMemory.myAvatar) }
    var myName by remember { mutableStateOf("Me") }

    LaunchedEffect(Unit) {
        try {
            loadMyAvatar(context) { myAvatar = it }
        } catch (_: Exception) {
        }
        try {
            AuthDataStore.getUsername(context).first()?.let { myName = it }
        } catch (_: Exception) {
        }
    }

    // Screen jab bhi saamne aaye (viewer / new-status se lautne par bhi): purana feed turant, phir chup-chaap update
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    try {
                        repo.loadWithMemory { feed = it }
                    } finally {
                        loaded = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mine = feed?.mine ?: emptyList()
    val recent = feed?.recent ?: emptyList()
    val viewed = feed?.viewed ?: emptyList()

    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }

            // Bottom padding: floating nav bar list ko dhak na le
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                item {
                    MyStatusRow(
                        name = myName,
                        avatar = myAvatar,
                        mine = mine,
                        onOpen = {
                            if (mine.isEmpty()) navController.navigate(Screen.NewStatus.route)
                            else navController.navigate(Screen.StatusViewer.createRoute("me"))
                        },
                        onAdd = { navController.navigate(Screen.NewStatus.route) }
                    )
                }

                // Kisi friend ne kuch dala hi nahi -> sections dikhte hi nahi
                if (loaded && recent.isEmpty() && viewed.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No status updates yet", color = Color(0xFF888888), fontSize = 14.sp)
                        }
                    }
                }

                if (recent.isNotEmpty()) {
                    item { StatusSectionLabel("Recent updates") }
                    items(recent, key = { "r_" + it.uid }) { u ->
                        FriendStatusRow(u) {
                            navController.navigate(Screen.StatusViewer.createRoute(u.uid))
                        }
                    }
                }

                if (viewed.isNotEmpty()) {
                    item { StatusSectionLabel("Viewed updates") }
                    items(viewed, key = { "v_" + it.uid }) { u ->
                        FriendStatusRow(u) {
                            navController.navigate(Screen.StatusViewer.createRoute(u.uid))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF888888),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun MyStatusRow(
    name: String,
    avatar: String?,
    mine: List<StatusItem>,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            if (mine.isEmpty()) {
                AvatarView(avatarBase64 = avatar, fallbackText = name, size = 56.dp, fontSize = 22.sp)
            } else {
                StatusRing(seen = mine.map { false }, diameter = 56.dp) {
                    AvatarView(avatarBase64 = avatar, fallbackText = name, size = 47.dp, fontSize = 20.sp)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(DarkAccent)
                    .border(2.dp, DarkBg, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add status",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text("My status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (mine.isEmpty()) "Tap to add status"
                else "${mine.size} update${if (mine.size > 1) "s" else ""} \u00B7 ${StatusTime.label(mine.last().createdAtMs)}",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FriendStatusRow(user: StatusUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusRing(seen = user.items.map { it.seen }, diameter = 56.dp) {
            AvatarView(avatarBase64 = user.avatar, fallbackText = user.username, size = 47.dp, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(StatusTime.label(user.latestAtMs), color = Color(0xFF888888), fontSize = 13.sp)
        }
    }
}
