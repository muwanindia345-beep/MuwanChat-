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
import com.muwan.muwanchat.network.EditGroupRequest
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// GroupInfoScreen ke "Edit Group" bottom sheet jaisa hi UI/UX -- bas ab full
// screen hai (pencil icon se navigate hota hai). Design/colors/flow same.
@Composable
fun EditGroupScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var group by remember { mutableStateOf<GroupData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var nameDraft by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf("") }
    var pendingAvatarBase64 by remember { mutableStateOf<String?>(null) }

    var isEditingName by remember { mutableStateOf(false) }
    var isEditingDescription by remember { mutableStateOf(false) }

    suspend fun refreshGroup() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) {
            res.body()?.group?.let { group = it }
        }
    }

    LaunchedEffect(groupId) {
        isLoading = true
        try { refreshGroup() } catch (_: Exception) {}
        isLoading = false
    }

    // Avatar crop result flow -- GroupInfoScreen/CreateGroupScreen jaisa hi pattern
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val croppedAvatarFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("cropped_avatar", null)
    }
    val croppedAvatar = croppedAvatarFlow?.collectAsState()?.value

    LaunchedEffect(croppedAvatar) {
        if (croppedAvatar != null) {
            pendingAvatarBase64 = croppedAvatar
            savedStateHandle?.remove<String>("cropped_avatar")
            scope.launch {
                try {
                    val token = AuthDataStore.getToken(context).first() ?: return@launch
                    val res = RetrofitClient.chatApi.editGroup(
                        "Bearer $token", groupId, EditGroupRequest(avatar = croppedAvatar)
                    )
                    if (res.isSuccessful) refreshGroup()
                    else Toast.makeText(context, "Avatar update nahi hua", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
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

    fun goBack() {
        // GroupInfoScreen ko batao ki refresh kar le (naya naam/desc/avatar dikhe)
        navController.previousBackStackEntry?.savedStateHandle?.set("group_edited", true)
        navController.popBackStack()
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
            IconButton(onClick = { goBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Edit Group", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (isLoading || group == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else {
            val g = group!!

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.clickable { photoPicker.launch("image/*") }
                ) {
                    AvatarView(
                        avatarBase64 = pendingAvatarBase64 ?: g.avatar,
                        fallbackText = g.name,
                        size = 100.dp,
                        fontSize = 34.sp
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(DarkAccent)
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Change avatar",
                            tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (isEditingName) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        label = { Text("Name", color = Color(0xFF888888)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = DarkAccent, unfocusedBorderColor = Color(0xFF444466)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                isEditingName = false
                                if (nameDraft.isNotBlank() && nameDraft != g.name) {
                                    scope.launch {
                                        val token = AuthDataStore.getToken(context).first() ?: return@launch
                                        val res = RetrofitClient.chatApi.editGroup(
                                            "Bearer $token", groupId, EditGroupRequest(name = nameDraft)
                                        )
                                        if (res.isSuccessful) refreshGroup()
                                    }
                                }
                            }) { Icon(Icons.Filled.Check, contentDescription = "Save", tint = DarkAccent) }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameDraft = g.name
                                isEditingName = true
                            }
                    ) {
                        Text(g.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Edit, contentDescription = null,
                            tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isEditingDescription) {
                    OutlinedTextField(
                        value = descriptionDraft,
                        onValueChange = { descriptionDraft = it },
                        placeholder = { Text("Group description...", color = Color(0xFF666688)) },
                        label = { Text("Description", color = Color(0xFF888888)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = DarkAccent, unfocusedBorderColor = Color(0xFF444466)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                isEditingDescription = false
                                scope.launch {
                                    val token = AuthDataStore.getToken(context).first() ?: return@launch
                                    val res = RetrofitClient.chatApi.editGroup(
                                        "Bearer $token", groupId, EditGroupRequest(description = descriptionDraft)
                                    )
                                    if (res.isSuccessful) refreshGroup()
                                }
                            }) { Icon(Icons.Filled.Check, contentDescription = "Save", tint = DarkAccent) }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                descriptionDraft = g.description ?: ""
                                isEditingDescription = true
                            }
                    ) {
                        Text(
                            if (g.description.isNullOrBlank()) "+ Add group description" else g.description,
                            color = if (g.description.isNullOrBlank()) Color(0xFF888888) else Color(0xFFCCCCCC),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.Edit, contentDescription = null,
                            tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { goBack() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done ✅", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
