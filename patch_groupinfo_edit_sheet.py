import re

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    src = f.read()

def apply(old, new, label):
    global src
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label}: found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    print(f"[OK] {label}")

# 1. Remove old isEditingName/isEditingDescription state, add sheet state
apply(
'''    var isEditingName by remember { mutableStateOf(false) }
    var isEditingDescription by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }''',
'''    var nameDraft by remember { mutableStateOf("") }''',
    "remove old inline-edit flags"
)

apply(
'''    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var memberPendingOwnershipTransfer by remember { mutableStateOf<GroupMemberProfile?>(null) }
    val sheetState = rememberModalBottomSheetState()''',
'''    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var memberPendingOwnershipTransfer by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var showEditGroupSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val editSheetState = rememberModalBottomSheetState()''',
    "add showEditGroupSheet + editSheetState"
)

# 2. Header: add pencil icon before 3-dot, owner/admin only
apply(
'''            Text(
                "Group Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { navController.navigate(Screen.GroupSettings.createRoute(groupId)) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Group Settings", tint = Color.White)
            }''',
'''            Text(
                "Group Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            if (isOwner || isAdmin) {
                IconButton(onClick = {
                    nameDraft = group?.name ?: ""
                    descriptionDraft = group?.description ?: ""
                    showEditGroupSheet = true
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Group", tint = Color.White)
                }
            }
            IconButton(onClick = { navController.navigate(Screen.GroupSettings.createRoute(groupId)) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Group Settings", tint = Color.White)
            }''',
    "header pencil icon"
)

# 3. Avatar in view screen: remove camera overlay, tap opens full-screen view for everyone
apply(
'''                Box(
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
                }''',
'''                Box(
                    modifier = Modifier.clickable {
                        AvatarViewerSelection.set(pendingAvatarBase64 ?: g.avatar, g.name)
                        navController.navigate(com.muwan.muwanchat.navigation.Screen.ViewAvatar.route)
                    }
                ) {
                    AvatarView(
                        avatarBase64 = pendingAvatarBase64 ?: g.avatar,
                        fallbackText = g.name,
                        size = 110.dp,
                        fontSize = 38.sp
                    )
                }''',
    "avatar -> tap to view full screen"
)

# 4. Name: plain text only, no edit
apply(
'''                // Naam -- admin ke liye tap-to-edit, member ke liye plain text
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
                }''',
'''                Text(g.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)''',
    "name -> plain text only"
)

# 5. Description: plain text only, no edit
apply(
'''                // Description -- admin ke liye tap-to-edit, member ke liye plain text (ya kuch nahi agar khaali)
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
                }''',
'''                if (!g.description.isNullOrBlank()) {
                    Text(
                        g.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }''',
    "description -> plain text only"
)

# 6. Insert the new Edit Group bottom sheet, right before the main screen Column
apply(
'''    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {''',
'''    if (showEditGroupSheet && group != null) {
        val g = group!!
        var isEditingNameSheet by remember { mutableStateOf(false) }
        var isEditingDescriptionSheet by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showEditGroupSheet = false },
            sheetState = editSheetState,
            containerColor = DarkHeader
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Edit Group", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

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

                if (isEditingNameSheet) {
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
                                isEditingNameSheet = false
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
                                isEditingNameSheet = true
                            }
                    ) {
                        Text(g.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Edit, contentDescription = null,
                            tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isEditingDescriptionSheet) {
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
                                isEditingDescriptionSheet = false
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
                                isEditingDescriptionSheet = true
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
                    onClick = { showEditGroupSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done \u2705", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {''',
    "insert Edit Group bottom sheet"
)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("\nDone. GroupInfoScreen.kt patched.")
