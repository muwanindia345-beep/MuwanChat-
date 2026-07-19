package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.muwan.muwanchat.util.friendlyErrorMessage
import com.muwan.muwanchat.network.SendRequestBody
import com.muwan.muwanchat.network.UserItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UserProfileScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<UserItem?>(null) }
    var status by remember { mutableStateOf("none") }
    var isLoading by remember { mutableStateOf(true) }
    var isSendingRequest by remember { mutableStateOf(false) }
    var myUid by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        try {
            val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect

            val meRes = RetrofitClient.authApi.me("Bearer $token")
            if (meRes.isSuccessful) myUid = meRes.body()?.user?.uid ?: ""

            val userRes = RetrofitClient.usersApi.getUserByUid("Bearer $token", uid)
            if (userRes.isSuccessful) user = userRes.body()?.user
            else errorMsg = "User not found"

            val statusRes = RetrofitClient.usersApi.getStatuses("Bearer $token", uid)
            if (statusRes.isSuccessful) status = statusRes.body()?.statuses?.get(uid) ?: "none"
        } catch (e: Exception) {
            errorMsg = friendlyErrorMessage(e)
        }
        isLoading = false
    }

    fun sendRequest() {
        scope.launch {
            isSendingRequest = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.requestsApi.sendRequest("Bearer $token", SendRequestBody(uid))
                if (res.isSuccessful) status = "sent"
                else errorMsg = res.body()?.error ?: "Failed"
            } catch (e: Exception) {
                errorMsg = friendlyErrorMessage(e)
            }
            isSendingRequest = false
        }
    }

    fun openChat() {
        val u = user ?: return
        val roomId = listOf(myUid, uid).sorted().joinToString("_")
        navController.navigate(Screen.Chat.createRoute(uid, u.username, roomId))
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
            Text("Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else if (user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg.ifBlank { "User not found" }, color = Color(0xFF888888))
            }
        } else {
            val u = user!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                AvatarView(
                    avatarBase64 = u.avatar,
                    fallbackText = u.name ?: u.username,
                    size = 120.dp,
                    fontSize = 40.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    u.name?.ifBlank { u.username } ?: u.username,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text("@${u.username}", color = Color(0xFF888888), fontSize = 14.sp)

                if (!u.bio.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        u.bio,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                if (!u.city.isNullOrBlank() || !u.country.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null,
                            tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            listOfNotNull(u.city, u.country).joinToString(", "),
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        )
                    }
                }

                if (!u.gender.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(u.gender, color = Color(0xFF888888), fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                when (status) {
                    "friends" -> {
                        Button(
                            onClick = { openChat() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Message", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "sent" -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF333355),
                                disabledContainerColor = Color(0xFF333355)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Request Pending", fontSize = 15.sp, color = Color(0xFFAAAAAA))
                        }
                    }
                    "received" -> {
                        Button(
                            onClick = { navController.navigate(Screen.Requests.route) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Respond in Requests", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { sendRequest() },
                            enabled = !isSendingRequest,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSendingRequest) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Request", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
