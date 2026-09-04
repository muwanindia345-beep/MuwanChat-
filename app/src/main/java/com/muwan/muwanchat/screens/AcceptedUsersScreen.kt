package com.muwan.muwanchat.screens

import android.widget.Toast
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
import com.muwan.muwanchat.data.AcceptedUsersCacheEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import com.muwan.muwanchat.network.UserItem
import com.muwan.muwanchat.DarkSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AcceptedUsersScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val gson = remember { Gson() }
    val listType = remember { object : TypeToken<List<UserItem>>() {}.type }

    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    // Ek waqt me ek hi user pe remove action process ho -- double-tap se
    // do baar remove call jaane se bachne ke liye per-uid busy flag.
    var busyUid by remember { mutableStateOf<String?>(null) }
    // Confirm dialog ke liye kaunsa user remove hone wala hai
    var pendingRemove by remember { mutableStateOf<UserItem?>(null) }

    suspend fun refresh() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.requestsApi.getAccepted("Bearer $token")
        if (res.isSuccessful) {
            val fresh = res.body()?.users ?: emptyList()
            users = fresh
            db.acceptedUsersCacheDao().upsert(AcceptedUsersCacheEntity(json = gson.toJson(fresh)))
        } else if (users.isEmpty()) {
            errorMsg = "List load nahi ho paayi"
        }
    }

    // Local cache se turant dikhao (offline-first) -- background me refresh()
    // fresh data laata hai aur cache ko bhi update kar deta hai.
    LaunchedEffect(Unit) {
        val cached = db.acceptedUsersCacheDao().get()
        if (cached != null) {
            try {
                users = gson.fromJson(cached.json, listType)
                isLoading = false
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (users.isEmpty()) isLoading = true
        try {
            refresh()
        } catch (e: Exception) {
            if (users.isEmpty()) errorMsg = friendlyErrorMessage(e)
        }
        isLoading = false
    }

    fun removeUser(uid: String) {
        // Optimistic: turant UI aur cache se hata do, backend call background
        // me chalta hai. Fail hone pe wapas list me daal dete hain.
        val previousUsers = users
        users = users.filter { it.uid != uid }
        scope.launch { db.acceptedUsersCacheDao().upsert(AcceptedUsersCacheEntity(json = gson.toJson(users))) }

        scope.launch {
            busyUid = uid
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.requestsApi.removeConnection("Bearer $token", uid)
                if (res.isSuccessful) {
                    val myUid = AuthDataStore.getUidBlocking(context)
                    val roomId = listOf(myUid, uid).sorted().joinToString("_")
                    MuwanChatDb.get(context, myUid).conversationDao().deleteByRoom(roomId)
                    Toast.makeText(context, "User removed", Toast.LENGTH_SHORT).show()
                } else {
                    // Revert
                    users = previousUsers
                    db.acceptedUsersCacheDao().upsert(AcceptedUsersCacheEntity(json = gson.toJson(previousUsers)))
                    Toast.makeText(context, "Remove nahi ho paya", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                users = previousUsers
                db.acceptedUsersCacheDao().upsert(AcceptedUsersCacheEntity(json = gson.toJson(previousUsers)))
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            busyUid = null
        }
    }

    pendingRemove?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = DarkSheet,
            title = { Text("Remove ${user.username}?", color = Color.White) },
            text = {
                Text(
                    "Yeh permanent hai — connection hat jaayega aur chat history dono taraf se saaf ho jaayegi. " +
                        "${user.username} search screen me wapas as a new request dikhega.",
                    color = Color(0xFFAAAAAA)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeUser(user.uid)
                    pendingRemove = null
                }) {
                    Text("Remove", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
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
            Text("Accepted Users", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkAccent)
                }
            }
            users.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PeopleAlt, contentDescription = null,
                            tint = Color(0xFF555577), modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            errorMsg.ifBlank { "Koi accepted connection nahi hai" },
                            color = Color(0xFF888888), fontSize = 14.sp
                        )
                    }
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    items(users, key = { it.uid }) { user ->
                        AcceptedUserRow(
                            user = user,
                            isBusy = busyUid == user.uid,
                            onRemove = { pendingRemove = user }
                        )
                        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AcceptedUserRow(
    user: UserItem,
    isBusy: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            avatarBase64 = user.avatar,
            fallbackText = user.username,
            size = 44.dp,
            fontSize = 16.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, color = Color.White, fontSize = 15.sp)
            Text(user.name ?: "", color = Color(0xFF888888), fontSize = 12.sp)
        }

        if (isBusy) {
            CircularProgressIndicator(color = DarkAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.18f)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Remove", color = Color(0xFFFF3B30), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
