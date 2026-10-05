package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.ChannelsCacheEntity
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.util.isNetworkAvailable
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.ConversationItem
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first

@Composable
fun BroadcastChannelsScreen(navController: NavController) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val gson = remember { Gson() }
    val listType = remember { object : TypeToken<List<ConversationItem>>() {}.type }
    var channels by remember { mutableStateOf<List<ConversationItem>>(emptyList()) }
    // Cache read hone tak (kuch ms) kuch render nahi — warna ek frame ke liye
    // galat "No broadcast channel yet" flash ho sakta hai.
    var cacheChecked by remember { mutableStateOf(false) }
    // Spinner SIRF tab jab cache kabhi bana hi nahi (first-ever load) aur net hai.
    // Cache hai to turant list, offline + cache nahi to seedha empty-state.
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cached = try { db.channelsCacheDao().get() } catch (_: Exception) { null }
        if (cached != null) {
            try { channels = gson.fromJson(cached.json, listType) } catch (_: Exception) {}
        }
        cacheChecked = true
        if (cached == null && isNetworkAvailable(context)) isLoading = true

        // Background refresh — fail ho to cache wali list jaisi hai waisi rahegi
        try {
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val res = RetrofitClient.chatApi.getChannels("Bearer $token")
                if (res.isSuccessful) {
                    val fresh = res.body()?.conversations ?: emptyList()
                    channels = fresh
                    db.channelsCacheDao().upsert(ChannelsCacheEntity(json = gson.toJson(fresh)))
                    // CHANNEL_PREFETCH_PATCH: channels ke messages bhi background mein local kar lo
                    ChatRepository.prefetchMessagesInBackground(db, token, fresh)
                }
            }
        } catch (_: Exception) {
            // Offline/error: cache (ya empty-state) hi dikhta rahega.
        }
        isLoading = false
    }

    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            // Same header style as the rest of the app, minus a back arrow —
            // this is a top-level tab, not a screen you navigate "into".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Broadcast Channels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
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
                            text = { Text("Create Channel", color = Color.White) },
                            onClick = {
                                showMenu = false
                                navController.navigate(com.muwan.muwanchat.navigation.Screen.CreateChannel.route)
                            }
                        )
                    }
                }
            }

            if (!cacheChecked) {
                // cache read ho raha hai (ms) — kuch draw nahi karna
            } else if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkAccent)
                }
            } else if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = Color(0xFF444466),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No broadcast channel yet",
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(channels, key = { it.room_id }) { channel ->
                        ConversationRow(
                            conv = channel,
                            showOnlineStatus = false,
                            onClick = {
                                navController.navigate(
                                    Screen.GroupChat.createRoute(channel.room_id, channel.username)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
