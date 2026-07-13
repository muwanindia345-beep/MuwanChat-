package com.muwan.muwanchat.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.AddMembersRequest
import com.muwan.muwanchat.network.EditGroupRequest
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.GroupMemberProfile
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SetAdminRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var myUid by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<GroupData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }

    var isEditingName by remember { mutableStateOf(false) }
    var isEditingDescription by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf("") }
    var pendingAvatarBase64 by remember { mutableStateOf<String?>(null) }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val isAdmin = group?.admins?.contains(myUid) == true
    val isOwner = group?.owner == myUid

    // Avatar crop result flow -- CreateGroupScreen jaisa hi pattern
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val croppedAvatarFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("cropped_avatar", null)
    }
    val croppedAvatar = croppedAvatarFlow?.collectAsState()?.value

    suspend fun refreshGroup() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) {
            group = res.body()?.group
        } else {
            errorMsg = "Group load nahi ho paya"
        }
    }

    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        isLoading = true
        try {
            refreshGroup()
        } catch (e: Exception) {
            errorMsg = e.message ?: "Network error"
        }
        isLoading = false
    }

    // Naya join request aaye (link se ya kisi member ke add karne se) toh
    // group turant refetch karo -- red dot bina manual refresh ke aa jaayega.
    LaunchedEffect(groupId) {
        com.muwan.muwanchat.data.AppSocketManager.events.collect { event ->
            if (event is com.muwan.muwanchat.data.SocketEvent.JoinRequest && event.roomId == groupId) {
                try { refreshGroup() } catch (_: Exception) {}
            }
        }
    }

    // Naya avatar crop hoke aaya -- turant edit call karo
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

    // "Add Members" se wapas aane par selection yahin dikhega -- confirm karne par API call
    fun confirmAddSelectedMembers() {
        val toAdd = GroupMemberSelection.selected.map { it.uid }
        if (toAdd.isEmpty()) return
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.addGroupMembers(
                    "Bearer $token", groupId, AddMembersRequest(memberUids = toAdd)
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    GroupMemberSelection.clear()
                    refreshGroup()
                } else {
                    Toast.makeText(context, "Members add nahi ho paye", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    fun leaveGroup() {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.removeGroupMember("Bearer $token", groupId, myUid)
                if (res.isSuccessful && res.body()?.success == true) {
                    val db = MuwanChatDb.get(context, myUid)
                    ChatRepository.deleteChatsLocally(db, setOf(groupId))
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.ConversationList.route) { inclusive = true }
                    }
                } else {
                    val err = res.body()?.let { "" } ?: "Owner group nahi chhod sakta — pehle ownership transfer karo"
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    fun setMemberAdmin(uid: String, makeAdmin: Boolean) {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.setGroupAdmin(
                    "Bearer $token", groupId, uid, SetAdminRequest(makeAdmin = makeAdmin)
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    selectedMemberForSheet = null
                    refreshGroup()
                } else {
                    Toast.makeText(context, res.body()?.let { "" } ?: "Sirf owner role change kar sakta hai", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    fun kickMember(uid: String) {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.removeGroupMember("Bearer $token", groupId, uid)
                if (res.isSuccessful && res.body()?.success == true) {
                    selectedMemberForSheet = null
                    refreshGroup()
                } else {
                    Toast.makeText(context, "Member remove nahi ho paya", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
            isBusy = false
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = DarkHeader,
            title = { Text("Leave Group?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Tum is group ke messages ab nahi dekh paoge jab tak dobara add na ho.",
                    color = Color(0xFFAAAAAA)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    leaveGroup()
                }) { Text("Leave", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    selectedMemberForSheet?.let { member ->
        val canToggleAdmin = isOwner && !member.isOwner
        val canKick = isAdmin && !member.isOwner && member.uid != myUid

        ModalBottomSheet(
            onDismissRequest = { selectedMemberForSheet = null },
            sheetState = sheetState,
            containerColor = DarkHeader
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(avatarBase64 = member.avatar, fallbackText = member.username, size = 40.dp, fontSize = 15.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(member.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFF2A2A44))

                SheetOptionRow(icon = Icons.Filled.Person, label = "See Profile") {
                    selectedMemberForSheet = null
                    navController.navigate(Screen.UserProfile.createRoute(member.uid))
                }

                if (canToggleAdmin) {
                    if (member.isAdmin) {
                        SheetOptionRow(icon = Icons.Filled.RemoveModerator, label = "Remove Admin") {
                            setMemberAdmin(member.uid, false)
                        }
                    } else {
                        SheetOptionRow(icon = Icons.Filled.AdminPanelSettings, label = "Give Admin") {
                            setMemberAdmin(member.uid, true)
                        }
                    }
                }

                if (canKick) {
                    SheetOptionRow(
                        icon = Icons.Filled.PersonRemove,
                        label = "Kick this member",
                        tint = Color(0xFFFF3B30)
                    ) {
                        kickMember(member.uid)
                    }
                }
            }
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
            Text(
                "Group Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { navController.navigate(Screen.GroupSettings.createRoute(groupId)) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Group Settings", tint = Color.White)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarkAccent)
            }
        } else if (group == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg.ifBlank { "Group not found" }, color = Color(0xFF888888))
            }
        } else {
            val g = group!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier.clickable(enabled = isAdmin) { photoPicker.launch("image/*") }
                ) {
                    AvatarView(
                        avatarBase64 = pendingAvatarBase64 ?: g.avatar,
                        fallbackText = g.name,
                        size = 110.dp,
                        fontSize = 38.sp
                    )
                    if (isAdmin) {
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
                }

                Spacer(Modifier.height(16.dp))

                // Naam -- admin ke liye tap-to-edit, member ke liye plain text
                if (isEditingName) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = DarkAccent, unfocusedBorderColor = Color(0xFF444466)
                        ),
                        trailingIcon = {
                            Row {
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
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = isAdmin) {
                            nameDraft = g.name
                            isEditingName = true
                        }
                    ) {
                        Text(g.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (isAdmin) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Edit, contentDescription = null,
                                tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("${g.members.size} members", color = Color(0xFF888888), fontSize = 13.sp)

                Spacer(Modifier.height(14.dp))

                // Description -- admin ke liye tap-to-edit, member ke liye plain text (ya kuch nahi agar khaali)
                if (isEditingDescription) {
                    OutlinedTextField(
                        value = descriptionDraft,
                        onValueChange = { descriptionDraft = it },
                        placeholder = { Text("Group description...", color = Color(0xFF666688)) },
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
                } else if (!g.description.isNullOrBlank()) {
                    Text(
                        g.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isAdmin) {
                                descriptionDraft = g.description ?: ""
                                isEditingDescription = true
                            }
                    )
                } else if (isAdmin) {
                    Text(
                        "+ Add group description",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            descriptionDraft = ""
                            isEditingDescription = true
                        }
                    )
                }

                Spacer(Modifier.height(22.dp))

                if (isAdmin) {
                    // Add Members
                    InfoActionRow(
                        icon = Icons.Filled.Person,
                        label = "Add from Contacts",
                        onClick = {
                            navController.navigate(Screen.AddFromContacts.route)
                        }
                    )

                    InfoActionRow(
                        icon = Icons.Filled.Search,
                        label = "Search Members",
                        onClick = {
                            navController.navigate(Screen.SearchMembersForGroup.route)
                        }
                    )

                    if (GroupMemberSelection.selected.isNotEmpty()) {
                        Button(
                            onClick = { confirmAddSelectedMembers() },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Add ${GroupMemberSelection.selected.size} Selected Member${if (GroupMemberSelection.selected.size != 1) "s" else ""}",
                                color = Color.White, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    InfoActionRow(
                        icon = Icons.Filled.HowToReg,
                        label = "Join Requests",
                        showRedDot = g.pendingRequests.isNotEmpty(),
                        onClick = {
                            navController.navigate(Screen.ApprovalRequests.createRoute(groupId))
                        }
                    )

                    Spacer(Modifier.height(18.dp))
                }

                Divider(color = Color(0xFF2A2A44))
                Spacer(Modifier.height(10.dp))

                Text(
                    "MEMBERS",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )

                g.memberProfiles.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (member.uid != myUid) {
                                        navController.navigate(Screen.UserProfile.createRoute(member.uid))
                                    }
                                },
                                onLongClick = {
                                    if (isAdmin && member.uid != myUid) {
                                        selectedMemberForSheet = member
                                    }
                                }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            avatarBase64 = member.avatar,
                            fallbackText = member.username,
                            size = 44.dp,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (member.uid == myUid) "${member.username} (You)" else member.username,
                                color = Color.White, fontSize = 15.sp
                            )
                        }
                        when {
                            member.isOwner -> RoleBadge(text = "Owner", emoji = "\uD83D\uDFE3", color = Color(0xFF9C27B0))
                            member.isAdmin -> RoleBadge(text = "Admin", emoji = "\uD83D\uDC9A", color = Color(0xFF4CAF50))
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Divider(color = Color(0xFF2A2A44))
                Spacer(Modifier.height(18.dp))

                TextButton(
                    onClick = { showLeaveConfirm = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFF3B30))
                    Spacer(Modifier.width(8.dp))
                    Text("Leave Group", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InfoActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int? = null,
    showRedDot: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkHeader)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DarkAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (showRedDot) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .size(10.dp)
            )
            Spacer(Modifier.width(10.dp))
        } else if (badgeCount != null && badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkAccent)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$badgeCount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RoleBadge(text: String, emoji: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("$emoji $text", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SheetOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}
