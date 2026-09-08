package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
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
import com.muwan.muwanchat.network.AddMembersRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import com.muwan.muwanchat.network.UserItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Room/conversations wale data se poora hata diya — yaha sirf uid/username/
// avatar chahiye (koi online-status, lastMessage, unreadCount waghera nahi).
// Backend se seedha /requests/accepted list aati hai, isliye duplicate-key
// jaisa koi risk bhi nahi bachta.
@Composable
fun AddFromContactsScreen(
    navController: NavController,
    channelId: String? = null,
    channelName: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var isFinishing by remember { mutableStateOf(false) }

    fun goToChannel() {
        navController.navigate(Screen.GroupChat.createRoute(channelId ?: "", channelName)) {
            popUpTo(Screen.BroadcastChannels.route) { inclusive = false }
        }
    }

    // Back arrow ka "done" behavior channel-mode me: jo bhi select kiya
    // hai use add karo, phir channel screen khol do. Kuch select nahi
    // kiya ho to bhi seedha channel khul jaata hai (FAB "Skip" bhi
    // yahi karta hai, bas explicit "kuch add nahi karna" ke liye hai).
    fun finishChannelSetup() {
        if (isFinishing) return
        isFinishing = true
        val toAdd = GroupMemberSelection.selected.map { it.uid }
        if (channelId == null || toAdd.isEmpty()) {
            GroupMemberSelection.clear()
            goToChannel()
            return
        }
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first()
                if (token != null) {
                    RetrofitClient.chatApi.addGroupMembers(
                        "Bearer $token", channelId, AddMembersRequest(memberUids = toAdd)
                    )
                }
            } catch (_: Exception) {
                // Channel already ban chuka hai -- member add fail bhi ho
                // jaaye to bhi channel screen khulni chahiye, baad me
                // Add from Contacts se dobara add kiya ja sakta hai.
            }
            GroupMemberSelection.clear()
            goToChannel()
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        errorMsg = ""
        try {
            val token = AuthDataStore.getToken(context).first()
            if (token == null) {
                errorMsg = "Login required"
            } else {
                val res = RetrofitClient.requestsApi.getAccepted("Bearer $token")
                if (res.isSuccessful) {
                    users = res.body()?.users ?: emptyList()
                } else {
                    errorMsg = "Couldn't load connections"
                }
            }
        } catch (e: Exception) {
            errorMsg = friendlyErrorMessage(e)
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            IconButton(onClick = {
                if (channelId != null) finishChannelSetup() else navController.popBackStack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Add from contacts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else if (users.isEmpty() && errorMsg.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Koi accepted connection nahi mila", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            LazyColumn {
                items(users, key = { it.uid }) { user ->
                    val isExistingMember = GroupMemberSelection.isExistingMember(user.uid)
                    val isAdded = GroupMemberSelection.isSelected(user.uid)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            AvatarView(
                                avatarBase64 = user.avatar,
                                fallbackText = user.username,
                                size = 46.dp,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        if (isExistingMember) {
                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF222222),
                                    disabledContainerColor = Color(0xFF222222)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("In Group", color = Color(0xFF888888), fontSize = 13.sp)
                            }
                        } else if (isAdded) {
                            Button(
                                onClick = { GroupMemberSelection.toggle(user) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333355)),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Added", color = Color.White, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = { GroupMemberSelection.toggle(user) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Add", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                }
            }
        }
    }

    if (channelId != null) {
        ExtendedFloatingActionButton(
            onClick = { GroupMemberSelection.clear(); goToChannel() },
            containerColor = DarkAccent,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.ArrowForward, contentDescription = null) },
            text = { Text("Skip") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        )
    }
    }
}
