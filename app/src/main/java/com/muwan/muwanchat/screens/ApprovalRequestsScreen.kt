package com.muwan.muwanchat.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.muwan.muwanchat.network.JoinRequestEntry
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ApprovalRequestsScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<JoinRequestEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    // Ek waqt me ek hi request pe action process ho -- accidental double-tap
    // se do baar approve/reject call jaane se bachne ke liye per-uid busy flag.
    var busyUid by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getJoinRequests("Bearer $token", groupId)
        if (res.isSuccessful) {
            requests = res.body()?.requests ?: emptyList()
        } else {
            errorMsg = "Requests load nahi ho paaye"
        }
    }

    LaunchedEffect(groupId) {
        isLoading = true
        try {
            refresh()
        } catch (e: Exception) {
            errorMsg = friendlyErrorMessage(e)
        }
        isLoading = false
    }

    fun approve(uid: String) {
        scope.launch {
            busyUid = uid
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.approveJoinRequest("Bearer $token", groupId, uid)
                if (res.isSuccessful) {
                    requests = requests.filter { it.uid != uid }
                } else {
                    Toast.makeText(context, "Approve nahi ho paya", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            busyUid = null
        }
    }

    fun reject(uid: String) {
        scope.launch {
            busyUid = uid
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.rejectJoinRequest("Bearer $token", groupId, uid)
                if (res.isSuccessful) {
                    requests = requests.filter { it.uid != uid }
                } else {
                    Toast.makeText(context, "Reject nahi ho paya", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            busyUid = null
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
            Text("Join Requests", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkAccent)
                }
            }
            requests.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.HowToReg, contentDescription = null,
                            tint = Color(0xFF555577), modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            errorMsg.ifBlank { "No pending join requests" },
                            color = Color(0xFF888888), fontSize = 14.sp
                        )
                    }
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    items(requests, key = { it.uid }) { req ->
                        JoinRequestRow(
                            request = req,
                            isBusy = busyUid == req.uid,
                            onApprove = { approve(req.uid) },
                            onReject = { reject(req.uid) }
                        )
                        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinRequestRow(
    request: JoinRequestEntry,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            avatarBase64 = request.avatar,
            fallbackText = request.username,
            size = 44.dp,
            fontSize = 16.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(request.username, color = Color.White, fontSize = 15.sp)
            Text(
                when (request.source) {
                    "link" -> "Requested via invite link"
                    "invited" -> "Invited by @${request.invitedByUsername ?: "someone"}"
                    else -> "Join request"
                },
                color = Color(0xFF888888), fontSize = 12.sp
            )
        }

        if (isBusy) {
            CircularProgressIndicator(color = DarkAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onReject) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30).copy(alpha = 0.15f))
                        .padding(6.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Reject", tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onApprove) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkAccent.copy(alpha = 0.18f))
                        .padding(6.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Approve", tint = DarkAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
