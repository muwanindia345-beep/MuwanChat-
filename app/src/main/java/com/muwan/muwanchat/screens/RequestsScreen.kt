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
import com.muwan.muwanchat.network.ChatRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun RequestsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<ChatRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val handled = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        try {
            val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect
            val res = RetrofitClient.requestsApi.getIncoming("Bearer $token")
            if (res.isSuccessful) requests = res.body()?.requests ?: emptyList()
        } catch (_: Exception) {}
        isLoading = false
    }

    fun accept(req: ChatRequest) {
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.requestsApi.acceptRequest("Bearer $token", req.id)
                if (res.isSuccessful) {
                    handled.add(req.id)
                    navController.popBackStack()
                }
            } catch (_: Exception) {}
        }
    }

    fun reject(req: ChatRequest) {
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.requestsApi.rejectRequest("Bearer $token", req.id)
                if (res.isSuccessful) handled.add(req.id)
            } catch (_: Exception) {}
        }
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
            Text("Chat Requests", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null,
                        tint = Color(0xFF444466), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Koi pending request nahi", color = Color(0xFF666688), fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn {
                items(requests.filter { !handled.contains(it.id) }) { req ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                req.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.username, color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Chat karna chahta/chahti hai",
                                color = Color(0xFF888888), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { accept(req) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Accept", color = Color.White, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { reject(req) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888))
                            ) {
                                Text("Reject", fontSize = 13.sp)
                            }
                        }
                    }
                    Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                }
            }
        }
    }
}
