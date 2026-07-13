package com.muwan.muwanchat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.network.JoinPreviewData
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Deep link entry point -- "muwanchat://join/{code}" isi screen pe land karta
// hai. Yahan koi background content nahi hai, seedha ek ModalBottomSheet
// scrim ke sath khulta hai jaise WhatsApp/Telegram invite preview karte hain.

private sealed class JoinSheetState {
    object Loading : JoinSheetState()
    data class Error(val message: String) : JoinSheetState()
    data class Preview(
        val data: JoinPreviewData,
        val alreadyMember: Boolean,
        val alreadyRequested: Boolean
    ) : JoinSheetState()
    data class Joined(val groupId: String, val groupName: String) : JoinSheetState()
    object Pending : JoinSheetState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(navController: NavController, code: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var state by remember { mutableStateOf<JoinSheetState>(JoinSheetState.Loading) }
    var isBusy by remember { mutableStateOf(false) }

    fun dismissAndBack() {
        if (!navController.popBackStack()) {
            navController.navigate(Screen.ConversationList.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(code) {
        try {
            val token = AuthDataStore.getToken(context).first()
            if (token == null) {
                state = JoinSheetState.Error("Login required")
                return@LaunchedEffect
            }
            val res = RetrofitClient.chatApi.getJoinPreview("Bearer $token", code)
            val body = res.body()
            if (res.isSuccessful && body?.preview != null) {
                state = JoinSheetState.Preview(
                    data = body.preview,
                    alreadyMember = body.alreadyMember,
                    alreadyRequested = body.alreadyRequested
                )
            } else if (res.code() == 404) {
                state = JoinSheetState.Error("Ye invite link invalid ya expire ho chuka hai")
            } else {
                state = JoinSheetState.Error("Link load nahi ho paya")
            }
        } catch (_: Exception) {
            state = JoinSheetState.Error("Network error")
        }
    }

    fun requestJoin() {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.joinViaCode("Bearer $token", code)
                val body = res.body()
                if (res.isSuccessful && body?.success == true) {
                    if (body.joined && body.group != null) {
                        state = JoinSheetState.Joined(body.group.id, body.group.name)
                    } else if (body.pending) {
                        state = JoinSheetState.Pending
                    } else {
                        state = JoinSheetState.Error("Kuch gadbad ho gayi")
                    }
                } else {
                    state = JoinSheetState.Error("Join request bhejne me error aayi")
                }
            } catch (_: Exception) {
                state = JoinSheetState.Error("Network error")
            }
            isBusy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissAndBack() },
        sheetState = sheetState,
        containerColor = DarkHeader
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                is JoinSheetState.Loading -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(color = DarkAccent)
                    Spacer(Modifier.height(32.dp))
                }

                is JoinSheetState.Error -> {
                    Spacer(Modifier.height(16.dp))
                    Text(s.message, color = Color(0xFFFF6B6B), fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { dismissAndBack() }) {
                        Text("Close", color = DarkAccent)
                    }
                }

                is JoinSheetState.Preview -> {
                    Spacer(Modifier.height(8.dp))
                    AvatarView(
                        avatarBase64 = s.data.avatar,
                        fallbackText = s.data.name.take(1).uppercase(),
                        size = 80.dp,
                        fontSize = 28.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        s.data.name, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, textAlign = TextAlign.Center
                    )
                    if (!s.data.description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(s.data.description, color = Color(0xFFAAAAAA), fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${s.data.memberCount} members", color = Color(0xFF888888), fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))

                    when {
                        s.alreadyMember -> {
                            Button(
                                onClick = {
                                    navController.navigate(Screen.GroupChat.createRoute(s.data.id, s.data.name)) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open Chat", color = Color.White) }
                        }
                        s.alreadyRequested -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444466)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Request Pending", color = Color(0xFFAAAAAA)) }
                        }
                        else -> {
                            Button(
                                onClick = { requestJoin() },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                } else {
                                    Text(
                                        if (s.data.joinApprovalRequired) "Request to Join" else "Join",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                is JoinSheetState.Joined -> {
                    Spacer(Modifier.height(16.dp))
                    Text("Group join ho gaya! \uD83C\uDF89", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            navController.navigate(Screen.GroupChat.createRoute(s.groupId, s.groupName)) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Chat", color = Color.White) }
                }

                is JoinSheetState.Pending -> {
                    Spacer(Modifier.height(16.dp))
                    Text("Request bhej di gayi \u2705", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Text("Admin approval ka wait karo", color = Color(0xFF888888), fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { dismissAndBack() }) {
                        Text("Close", color = DarkAccent)
                    }
                }
            }
        }
    }
}
