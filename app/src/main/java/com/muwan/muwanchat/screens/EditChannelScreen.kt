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
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.ChatRepository
import androidx.compose.material.icons.filled.Delete
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
    var showDeleteConfirm by remember { mutableStateOf(false) }  // CHANNEL_DELETE
    var isDeleting by remember { mutableStateOf(false) }

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

    // CHANNEL_DELETE: owner-only. Server sabko 'group_removed' (groupDeleted=true) bhejta hai,
    // offline users ko removal-status se milta hai. Yahan sirf apni local copy saaf.
    fun deleteChannelPermanently() {
        if (isDeleting) return
        isDeleting = true
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first()
                if (token == null) {
                    Toast.makeText(context, "Login required", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val res = RetrofitClient.chatApi.deleteGroup("Bearer $token", groupId)
                if (res.isSuccessful) {
                    ChatRepository.deleteChatsLocally(db, setOf(groupId))
                    showDeleteConfirm = false
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.ConversationList.route) { inclusive = true }
                    }
                } else {
                    Toast.makeText(context, "Could not delete channel. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, friendlyErrorMessage(e), Toast.LENGTH_SHORT).show()
            } finally {
                isDeleting = false
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

                    // CHANNEL_DELETE: sirf owner ko dikhta hai
                    if (group?.owner == myUid) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isSaving && !isDeleting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF3B30),
                                disabledContainerColor = Color(0xFFFF3B30).copy(alpha = 0.45f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Channel", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            containerColor = DarkSheet,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("\u26A0\uFE0F Delete Channel?") },
            text = {
                Text(
                    "\"${group?.name ?: channelName}\" and all its messages and media will be " +
                        "permanently deleted for everyone. All subscribers will be notified that " +
                        "this channel has been deleted. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteChannelPermanently() }, enabled = !isDeleting) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = Color(0xFFFF3B30), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !isDeleting) {
                    Text("Cancel")
                }
            }
        )
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
