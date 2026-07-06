package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SendRequestBody
import com.muwan.muwanchat.network.UserItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Status colors — orange (none/default), purple (pending), green (accepted)
private val PendingPurple = Color(0xFF8E5CF5)
private val AcceptedGreen = Color(0xFF2ECC71)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var myUid by remember { mutableStateOf("") }

    // uid -> "none" | "sent" | "received" | "friends" — backend se aata hai, session-local nahi
    val statusMap = remember { mutableStateMapOf<String, String>() }
    var errorMsg by remember { mutableStateOf("") }

    suspend fun refreshStatuses(list: List<UserItem>) {
        if (list.isEmpty()) return
        try {
            val token = AuthDataStore.getToken(context).first() ?: return
            val uidsCsv = list.joinToString(",") { it.uid }
            val res = RetrofitClient.usersApi.getStatuses("Bearer $token", uidsCsv)
            if (res.isSuccessful) {
                res.body()?.statuses?.let { statusMap.putAll(it) }
            }
        } catch (_: Exception) {
            // Statuses fetch fail ho jaye to bhi list dikhti rahegi, bas colors default rahenge
        }
    }

    fun loadSuggestions() {
        isLoading = true
        errorMsg = ""
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.usersApi.getSuggestions("Bearer $token")
                if (res.isSuccessful) {
                    users = (res.body()?.users ?: emptyList()).filter { it.uid != myUid }
                    refreshStatuses(users)
                } else errorMsg = "Couldn't load users"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Error"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
        try {
            val res = RetrofitClient.authApi.me("Bearer $token")
            if (res.isSuccessful) myUid = res.body()?.user?.uid ?: ""
        } catch (_: Exception) {}
        loadSuggestions()
    }

    fun search() {
        if (query.isBlank()) {
            loadSuggestions()
            return
        }
        isLoading = true
        errorMsg = ""
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.usersApi.searchUsers("Bearer $token", query.trim())
                if (res.isSuccessful) {
                    users = (res.body()?.users ?: emptyList()).filter { it.uid != myUid }
                    refreshStatuses(users)
                } else errorMsg = "Search failed"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Error"
            }
            isLoading = false
        }
    }

    fun sendRequest(receiverUid: String) {
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.requestsApi.sendRequest(
                    "Bearer $token",
                    SendRequestBody(receiverUid)
                )
                if (res.isSuccessful) statusMap[receiverUid] = "sent"
                else errorMsg = res.body()?.error ?: "Failed"
            } catch (e: Exception) {
                errorMsg = e.message ?: "Error"
            }
        }
    }

    fun openChat(user: UserItem) {
        val roomId = listOf(myUid, user.uid).sorted().joinToString("_")
        navController.navigate(Screen.Chat.createRoute(user.uid, user.username, roomId))
    }

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
            Text("New Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Username ya email search karo...", color = Color(0xFF888888)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF333355),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Button(
                onClick = { search() },
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White)
            }
        }

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = Color.Red, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        }

        LazyColumn {
            items(users) { user ->
                val status = statusMap[user.uid] ?: "none"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(DarkAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(user.name ?: "", color = Color(0xFF888888), fontSize = 12.sp)
                    }

                    when (status) {
                        "friends" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(AcceptedGreen, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Accepted ✅", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { openChat(user) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF333355))
                                ) {
                                    Icon(
                                        Icons.Filled.Chat,
                                        contentDescription = "Open chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        "sent" -> {
                            Box(
                                modifier = Modifier
                                    .background(PendingPurple, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Pending", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            // "none" aur "received" dono ke liye search screen se sirf Request bhej sakte hain;
                            // accept/reject sirf Requests screen se hota hai
                            Button(
                                onClick = { sendRequest(user.uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Request", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
            }
        }
    }
}

