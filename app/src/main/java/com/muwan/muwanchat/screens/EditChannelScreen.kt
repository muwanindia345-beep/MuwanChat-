package com.muwan.muwanchat.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.google.gson.Gson
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.GroupInfoCacheEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.EditGroupRequest
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// CreateChannelScreen jaisa hi UI (header / avatar / fields / button) -- bas
// edit mode me: pehle se bhari values, "Save" button. Sirf owner/admin.
// Backend: PUT groups/{id} (EditGroupRequest) -- avatar tabhi bhejta hai jab
// naya photo chuna ho (null = unchanged).
@Composable
fun EditChannelScreen(navController: NavController, groupId: String) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val myUid = remember { AuthDataStore.getUidBlocking(context) }

    var group by remember { mutableStateOf<GroupData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var filled by remember { mutableStateOf(false) }

    var channelName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currentAvatar by remember { mutableStateOf<String?>(null) }   // jo abhi dikh raha hai
    var newAvatar by remember { mutableStateOf<String?>(null) }       // sirf naya chuna hua
    var isSaving by remember { mutableStateOf(false) }

    fun fillOnce(g: GroupData) {
        group = g
        if (!filled) {
            channelName = g.name
            description = g.description ?: ""
            currentAvatar = g.avatar
            filled = true
        }
    }

    LaunchedEffect(groupId) {
        // cache se turant, phir fresh
        try {
            val cached = db.groupInfoCacheDao().get(groupId)
            if (cached != null) fillOnce(gson.fromJson(cached.json, GroupData::class.java))
        } catch (_: Exception) {}
        try {
            val token = AuthDataStore.getToken(context).first()
            if (token != null) {
                val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
                res.body()?.group?.let { if (res.isSuccessful) fillOnce(it) }
            }
        } catch (_: Exception) {}
        isLoading = false

        val g = group
        if (g != null && !(g.owner == myUid || g.admins.contains(myUid))) {
            Toast.makeText(context, "Sirf admin channel edit kar sakte hain", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    fun confirmSave() {
        val name = channelName.trim()
        if (name.isEmpty() || isSaving) return
        isSaving = true
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first()
                if (token == null) {
                    Toast.makeText(context, "Login required", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    return@launch
                }
                val res = RetrofitClient.chatApi.editGroup(
                    "Bearer $token",
                    groupId,
                    EditGroupRequest(name = name, avatar = newAvatar, description = description.trim())
                )
                if (res.isSuccessful) {
                    // local cache bhi update, taaki profile screen turant naya dikhaye
                    try {
                        val token2 = AuthDataStore.getToken(context).first()
                        if (token2 != null) {
                            val fresh = RetrofitClient.chatApi.getGroup("Bearer $token2", groupId).body()?.group
                            if (fresh != null) {
                                db.groupInfoCacheDao().upsert(
                                    GroupInfoCacheEntity(groupId = groupId, json = gson.toJson(fresh))
                                )
                            }
                        }
                    } catch (_: Exception) {}
                    navController.previousBackStackEntry?.savedStateHandle?.set("channel_edited", true)
                    navController.popBackStack()
                } else {
                    Toast.makeText(context, "Channel update nahi hua, dobara try karo", Toast.LENGTH_SHORT).show()
                    isSaving = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, friendlyErrorMessage(e), Toast.LENGTH_SHORT).show()
                isSaving = false
            }
        }
    }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val croppedAvatarFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("cropped_avatar", null)
    }
    val croppedAvatar = croppedAvatarFlow?.collectAsState()?.value
    LaunchedEffect(croppedAvatar) {
        if (croppedAvatar != null) {
            newAvatar = croppedAvatar
            currentAvatar = croppedAvatar
            savedStateHandle?.remove<String>("cropped_avatar")
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            AvatarTransfer.pickedUri = it
            navController.navigate(Screen.AvatarCrop.route)
        }
    }

    val previewName = channelName.ifBlank { "Channel" }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
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
                    "Edit Channel",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            if (isLoading && group == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkAccent)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .clickable { photoPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarView(
                            avatarBase64 = currentAvatar,
                            fallbackText = previewName,
                            size = 110.dp,
                            fontSize = 36.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tap to change channel photo",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        label = { Text("Channel name", color = Color(0xFF888888)) },
                        placeholder = { Text("Channel", color = Color(0xFF555555)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = editChannelFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)", color = Color(0xFF888888)) },
                        placeholder = {
                            Text(
                                "What is this channel about?",
                                color = Color(0xFF555555)
                            )
                        },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = editChannelFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { confirmSave() },
                        enabled = channelName.isNotBlank() && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkAccent,
                            disabledContainerColor = DarkAccent.copy(alpha = 0.45f),
                            disabledContentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun editChannelFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DarkAccent,
    unfocusedBorderColor = Color(0xFF444444),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = DarkAccent
)
