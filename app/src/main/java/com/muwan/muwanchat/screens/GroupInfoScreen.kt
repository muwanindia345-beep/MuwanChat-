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
import com.google.gson.Gson
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.GroupInfoCacheEntity
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.AddMembersRequest
import com.muwan.muwanchat.network.EditGroupRequest
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.GroupMemberProfile
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.SetAdminRequest
import com.muwan.muwanchat.DarkSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val gson = remember { Gson() }

    var myUid by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<GroupData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var memberPendingOwnershipTransfer by remember { mutableStateOf<GroupMemberProfile?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val isAdmin = group?.admins?.contains(myUid) == true
    val isOwner = group?.owner == myUid

    suspend fun refreshGroup() {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
        if (res.isSuccessful) {
            val fresh = res.body()?.group
            if (fresh != null) {
                group = fresh
                db.groupInfoCacheDao().upsert(GroupInfoCacheEntity(groupId = groupId, json = gson.toJson(fresh)))
            }
        } else if (group == null) {
            errorMsg = "Group load nahi ho paya"
        }
    }

    // Local cache se turant dikhao (offline-first) -- background me refreshGroup() fresh data laata hai
    LaunchedEffect(groupId) {
        val cached = db.groupInfoCacheDao().get(groupId)
        if (cached != null) {
            try {
                group = gson.fromJson(cached.json, GroupData::class.java)
                isLoading = false
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        if (group == null) isLoading = true
        try {
            refreshGroup()
        } catch (e: Exception) {
            if (group == null) errorMsg = e.message ?: "Network error"
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

    // EditGroupScreen se wapas aane par (naam/desc/avatar edit hua) fresh data laao
    val editedFlow = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("group_edited", false)
    val wasEdited = editedFlow?.collectAsState()?.value
    LaunchedEffect(wasEdited) {
        if (wasEdited == true) {
            navController.currentBackStackEntry?.savedStateHandle?.set("group_edited", false)
            try { refreshGroup() } catch (_: Exception) {}
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

    fun transferOwnership(uid: String) {
        scope.launch {
            isBusy = true
            try {
                val token = AuthDataStore.getToken(context).first() ?: return@launch
                val res = RetrofitClient.chatApi.transferOwnership("Bearer $token", groupId, uid)
                if (res.isSuccessful && res.body()?.success == true) {
                    memberPendingOwnershipTransfer = null
                    selectedMemberForSheet = null
                    refreshGroup()
                } else {
                    Toast.makeText(context, "Ownership transfer nahi ho paya", Toast.LENGTH_SHORT).show()
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
            containerColor = DarkSheet,
            title = { Text("Leave Group?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You won't be able to see this group's messages anymore until you're added back.",
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

    memberPendingOwnershipTransfer?.let { target ->
        AlertDialog(
            onDismissRequest = { memberPendingOwnershipTransfer = null },
            containerColor = DarkSheet,
            title = { Text("Transfer Ownership?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "@${target.username} is group ka naya owner ban jayega. Tum admin rahoge, apni marzi se khud ko admin se hata sakte ho.",
                    color = Color(0xFFAAAAAA)
                )
            },
            confirmButton = {
                TextButton(onClick = { transferOwnership(target.uid) }) {
                    Text("Transfer", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingOwnershipTransfer = null }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    selectedMemberForSheet?.let { member ->
        // "Give/Remove Admin" ab koi bhi admin kar sakta hai (sirf owner nahi) --
        // par owner ka khud ka admin status kisi se bhi change nahi hota.
        val canToggleAdmin = isAdmin && !member.isOwner
        val canTransferOwnership = isOwner && !member.isOwner
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

                if (canTransferOwnership) {
                    SheetOptionRow(icon = Icons.Filled.Star, label = "Transfer Ownership") {
                        memberPendingOwnershipTransfer = member
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
            if (isOwner || isAdmin) {
                IconButton(onClick = {
                    navController.navigate(Screen.EditGroup.createRoute(groupId))
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Group", tint = Color.White)
                }
            }
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
                    modifier = Modifier.clickable {
                        AvatarViewerSelection.set(g.avatar, g.name)
                        navController.navigate(com.muwan.muwanchat.navigation.Screen.ViewAvatar.route)
                    }
                ) {
                    AvatarView(
                        avatarBase64 = g.avatar,
                        fallbackText = g.name,
                        size = 110.dp,
                        fontSize = 38.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(g.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)

                Spacer(Modifier.height(4.dp))
                Text("${g.members.size} members", color = Color(0xFF888888), fontSize = 13.sp)

                Spacer(Modifier.height(14.dp))

                if (!g.description.isNullOrBlank()) {
                    Text(
                        g.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(22.dp))

                // Admin hamesha add kar sakta hai; regular member sirf
                // tab jab group owner ne "Allow members to add members" ON kiya ho.
                val canAddMembers = isAdmin || group?.membersCanAdd == true
                if (canAddMembers) {
                    // Add Members
                    InfoActionRow(
                        icon = Icons.Filled.Person,
                        label = "Add from Contacts",
                        onClick = {
                            GroupMemberSelection.setExistingUids(g.members)
                            navController.navigate(Screen.AddFromContacts.route)
                        }
                    )

                    InfoActionRow(
                        icon = Icons.Filled.Search,
                        label = "Search Members",
                        onClick = {
                            GroupMemberSelection.setExistingUids(g.members)
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
                        showRedDot = (g.pendingRequests ?: emptyList()).isNotEmpty(),
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
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
