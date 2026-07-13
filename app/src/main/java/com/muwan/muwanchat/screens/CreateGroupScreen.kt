package com.muwan.muwanchat.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.CreateGroupRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(navController: NavController) {

    var groupName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedMembers = GroupMemberSelection.selected

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCreating by remember { mutableStateOf(false) }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val croppedAvatarFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("cropped_avatar", null)
    }
    val croppedAvatar = croppedAvatarFlow?.collectAsState()?.value
    LaunchedEffect(croppedAvatar) {
        if (croppedAvatar != null) {
            avatarBase64 = croppedAvatar
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

    val previewName = groupName.ifBlank { "New Group" }

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
                    "New Group",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

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
                        avatarBase64 = avatarBase64,
                        fallbackText = previewName,
                        size = 110.dp,
                        fontSize = 36.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Tap to add group photo",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group name", color = Color(0xFF888888)) },
                    placeholder = { Text("New Group", color = Color(0xFF555555)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = groupFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = Color(0xFF888888)) },
                    placeholder = {
                        Text(
                            "Purpose, rules, kuch bhi likh sakte ho",
                            color = Color(0xFF555555)
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = groupFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    "Add members",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                AddMemberRow(
                    icon = Icons.Filled.Person,
                    title = "Add from contacts",
                    subtitle = "Apne accepted connections se select karo",
                    onClick = { navController.navigate(Screen.AddFromContacts.route) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                AddMemberRow(
                    icon = Icons.Filled.Search,
                    title = "Search members",
                    subtitle = "Kisi ko bhi search karke add karo",
                    onClick = { navController.navigate(Screen.SearchMembersForGroup.route) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (selectedMembers.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${selectedMembers.size} member${if (selectedMembers.size > 1) "s" else ""} added",
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            selectedMembers.forEach { member ->
                                Box(contentAlignment = Alignment.TopEnd) {
                                    AvatarView(
                                        avatarBase64 = member.avatar,
                                        fallbackText = member.username,
                                        size = 56.dp,
                                        fontSize = 20.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE05555))
                                            .clickable { GroupMemberSelection.remove(member.uid) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove ${member.username}",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Button(
                    onClick = {
                        if (selectedMembers.isEmpty()) {
                            Toast.makeText(context, "Kam se kam 1 member add karo", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isCreating) return@Button
                        isCreating = true
                        scope.launch {
                            try {
                                val token = AuthDataStore.getToken(context).first()
                                val myUid = AuthDataStore.getUid(context).first() ?: ""
                                if (token == null) {
                                    Toast.makeText(context, "Session expired, dobara login karo", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val res = RetrofitClient.chatApi.createGroup(
                                    "Bearer $token",
                                    CreateGroupRequest(
                                        name = groupName.ifBlank { "New Group" },
                                        avatar = avatarBase64,
                                        description = description.ifBlank { null },
                                        memberUids = selectedMembers.map { it.uid }
                                    )
                                )
                                val group = res.body()?.group
                                if (res.isSuccessful && res.body()?.success == true && group != null) {
                                    val db = MuwanChatDb.get(context, myUid)
                                    ChatRepository.addConversationPlaceholder(
                                        db = db,
                                        roomId = group.id,
                                        uid = group.id,
                                        username = group.name,
                                        avatar = group.avatar,
                                        isGroup = true,
                                        memberCount = group.members.size
                                    )
                                    GroupMemberSelection.clear()
                                    navController.navigate(
                                        Screen.GroupChat.createRoute(group.id, group.name)
                                    ) {
                                        popUpTo(Screen.ConversationList.route) { inclusive = false }
                                    }
                                } else {
                                    Toast.makeText(context, "Group create nahi ho paya, dobara try karo", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Network error, dobara try karo", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCreating = false
                            }
                        }
                    },
                    enabled = !isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AddMemberRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkHeader)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DarkAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFF888888), fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
    }
}

@Composable
private fun groupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DarkAccent,
    unfocusedBorderColor = Color(0xFF444444),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = DarkAccent
)
