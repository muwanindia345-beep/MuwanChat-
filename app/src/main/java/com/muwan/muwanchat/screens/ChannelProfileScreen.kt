package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.GroupInfoCacheEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first

// Broadcast channel ka profile: avatar (tap -> full view), naam, description.
// Header ke end me 3-dot menu sirf owner/admin ko dikhta hai -> Edit Channel
// (EditChannelScreen). Data pehle local group_info_cache se (offline-first),
// phir getGroup se fresh -- GroupInfoScreen jaisa hi pattern.
@Composable
fun ChannelProfileScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val gson = remember { Gson() }
    val myUid = remember { AuthDataStore.getUidBlocking(context) }

    var group by remember { mutableStateOf<GroupData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    // EditChannelScreen se wapas aane par fresh data fetch ho
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val editedFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow("channel_edited", false)
    }
    val edited = editedFlow?.collectAsState()?.value == true
    LaunchedEffect(edited) {
        if (edited) {
            savedStateHandle?.remove<Boolean>("channel_edited")
            refreshKey++
        }
    }

    LaunchedEffect(groupId) {
        val cached = db.groupInfoCacheDao().get(groupId)
        if (cached != null) {
            try {
                group = gson.fromJson(cached.json, GroupData::class.java)
                isLoading = false
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(groupId, refreshKey) {
        try {
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
                val fresh = res.body()?.group
                if (res.isSuccessful && fresh != null) {
                    group = fresh
                    db.groupInfoCacheDao().upsert(
                        GroupInfoCacheEntity(groupId = groupId, json = gson.toJson(fresh))
                    )
                } else if (group == null) {
                    errorMsg = "Channel load nahi ho paya"
                }
            }
        } catch (e: Exception) {
            if (group == null) errorMsg = e.message ?: "Network error"
        }
        isLoading = false
    }

    val canEdit = group?.let { it.owner == myUid || it.admins.contains(myUid) } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "Channel Profile",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            if (canEdit) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DarkSheet)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Channel", color = Color.White) },
                            onClick = {
                                showMenu = false
                                navController.navigate(Screen.EditChannel.createRoute(groupId))
                            }
                        )
                    }
                }
            }
        }

        val g = group
        if (g == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(color = DarkAccent)
                } else {
                    Text(errorMsg.ifBlank { "Channel not found" }, color = Color(0xFF888888))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier.clickable {
                        AvatarViewerSelection.set(g.avatar, g.name)
                        navController.navigate(Screen.ViewAvatar.route)
                    }
                ) {
                    AvatarView(
                        avatarBase64 = g.avatar,
                        fallbackText = g.name,
                        size = 110.dp,
                        fontSize = 38.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    g.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                if (!g.description.isNullOrBlank()) {
                    // [center-desc]
                    Text(
                        g.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("No description", color = Color(0xFF666666), fontSize = 14.sp)
                }
            }
        }
    }
}
