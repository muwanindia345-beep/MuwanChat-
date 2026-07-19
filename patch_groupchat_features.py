path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"
with open(path) as f:
    content = f.read()

changes = 0

def rep(old, new, label):
    global content, changes
    if old in content:
        content = content.replace(old, new, 1)
        changes += 1
    else:
        print(f"WARN: {label} anchor not found")

# Imports
rep(
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.horizontalScroll",
    "import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.horizontalScroll",
    "border import"
)
rep(
    "import androidx.compose.ui.text.style.TextOverflow\n",
    "import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.text.buildAnnotatedString\nimport androidx.compose.ui.text.SpanStyle\nimport androidx.compose.ui.text.withStyle\nimport androidx.compose.material3.ModalBottomSheet\n",
    "text/bottomsheet imports"
)
rep(
    "import com.muwan.muwanchat.network.RetrofitClient\n",
    "import com.muwan.muwanchat.network.RetrofitClient\nimport com.muwan.muwanchat.network.GroupData\n",
    "GroupData import"
)

# State
rep(
    '''    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberAvatars by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var memberCount by remember { mutableStateOf(0) }''',
    '''    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberAvatars by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var memberCount by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<GroupData?>(null) }
    var showAdminsSheet by remember { mutableStateOf(false) }
    val isAdmin = group?.admins?.contains(myUid) == true
    val onlineUidsForAdmins by AppSocketManager.onlineUids.collectAsState()''',
    "member state"
)

# Store fetched group
rep(
    '''        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
            }
        } catch (_: Exception) {}''',
    '''        try {
            val res = RetrofitClient.chatApi.getGroup("Bearer $token", groupId)
            res.body()?.group?.let { g ->
                group = g
                memberNames = g.memberProfiles.associate { it.uid to it.username }
                memberAvatars = g.memberProfiles.associate { it.uid to it.avatar }
                memberCount = g.members.size
            }
        } catch (_: Exception) {}''',
    "initial group fetch"
)

# GroupUpdated + groupDeleted sentinel handling
rep(
    '''                is SocketEvent.GroupRemoved -> {
                    if (event.roomId == groupId && !event.selfLeave) {
                        db.conversationDao().markRemoved(
                            groupId,
                            event.removedByUsername ?: "Admin"
                        )
                    }
                }''',
    '''                is SocketEvent.GroupRemoved -> {
                    if (event.roomId == groupId && !event.selfLeave) {
                        db.conversationDao().markRemoved(
                            groupId,
                            if (event.groupDeleted) "GROUP_DELETED" else (event.removedByUsername ?: "Admin")
                        )
                    }
                }
                is SocketEvent.GroupUpdated -> {
                    if (event.roomId == groupId) {
                        scope.launch {
                            try {
                                val res = RetrofitClient.chatApi.getGroup("Bearer $myToken", groupId)
                                res.body()?.group?.let { g -> group = g }
                            } catch (_: Exception) {}
                        }
                    }
                }''',
    "GroupRemoved/GroupUpdated socket handler"
)

# Removed-banner text (groupDeleted variant)
rep(
    '''                Text(
                    "You were removed from this group by @${conversationEntity?.removedByUsername ?: "Admin"}",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            ChatInputBar(''',
    '''                Text(
                    if (conversationEntity?.removedByUsername == "GROUP_DELETED")
                        "This group was deleted by the owner"
                    else
                        "You were removed from this group by @${conversationEntity?.removedByUsername ?: "Admin"}",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (group?.onlyAdminsCanSend == true && !isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .clickable { showAdminsSheet = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val onlyText = buildAnnotatedString {
                    append("Only ")
                    withStyle(SpanStyle(color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)) {
                        append("admins")
                    }
                    append(" can send messages")
                }
                Text(onlyText, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            ChatInputBar(''',
    "removed banner + only-admins banner"
)

# Admins bottom sheet at end of function
rep(
    '''                    androidx.compose.material3.TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}''',
    '''                    androidx.compose.material3.TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showAdminsSheet) {
        ModalBottomSheet(onDismissRequest = { showAdminsSheet = false }, containerColor = DarkHeader) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Group Admins",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val admins = group?.memberProfiles?.filter { it.isAdmin || it.isOwner } ?: emptyList()
                admins.forEach { admin ->
                    val isOnline = onlineUidsForAdmins.contains(admin.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAdminsSheet = false
                                navController.navigate(Screen.UserProfile.createRoute(admin.uid))
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AvatarView(
                                avatarBase64 = admin.avatar,
                                fallbackText = admin.username,
                                size = 44.dp,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        if (isOnline) Color(0xFF2ECC71) else Color(0xFF666688),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(2.dp, DarkHeader, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(admin.username, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (isOnline) "Online" else "Offline",
                                color = if (isOnline) Color(0xFF2ECC71) else Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}''',
    "admins bottom sheet"
)

with open(path, "w") as f:
    f.write(content)
print(f"GroupChatScreen.kt changes: {changes}")
