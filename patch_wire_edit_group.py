def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

NAVGRAPH = "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
GROUPINFO = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"

# ---------------------------------------------------------------------------
# 1) NavGraph.kt: add EditGroup route
# ---------------------------------------------------------------------------
apply(
    NAVGRAPH,
    '''    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
''',
    '''    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object EditGroup       : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: String) = "edit_group/$groupId"
    }
''',
    "NavGraph: add EditGroup route",
)

# ---------------------------------------------------------------------------
# 2) NavGraph.kt: register EditGroup composable
# ---------------------------------------------------------------------------
apply(
    NAVGRAPH,
    '''        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
''',
    '''        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.EditGroup.route) { back ->
            EditGroupScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
''',
    "NavGraph: register EditGroup composable",
)

# ---------------------------------------------------------------------------
# 3) GroupInfoScreen.kt: pencil icon ab EditGroupScreen pe navigate karega
# ---------------------------------------------------------------------------
apply(
    GROUPINFO,
    '''            if (isOwner || isAdmin) {
                IconButton(onClick = {
                    nameDraft = group?.name ?: ""
                    descriptionDraft = group?.description ?: ""
                    showEditGroupSheet = true
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Group", tint = Color.White)
                }
            }''',
    '''            if (isOwner || isAdmin) {
                IconButton(onClick = {
                    navController.navigate(Screen.EditGroup.createRoute(groupId))
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Group", tint = Color.White)
                }
            }''',
    "GroupInfoScreen: pencil icon -> EditGroupScreen",
)

# ---------------------------------------------------------------------------
# 4) GroupInfoScreen.kt: ab-unused sheet-only state hatao
# ---------------------------------------------------------------------------
apply(
    GROUPINFO,
    '''    var nameDraft by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf("") }
    var pendingAvatarBase64 by remember { mutableStateOf<String?>(null) }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var memberPendingOwnershipTransfer by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var showEditGroupSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val editSheetState = rememberModalBottomSheetState()

    val isAdmin = group?.admins?.contains(myUid) == true
    val isOwner = group?.owner == myUid

    // Avatar crop result flow -- CreateGroupScreen jaisa hi pattern
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val croppedAvatarFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>("cropped_avatar", null)
    }
    val croppedAvatar = croppedAvatarFlow?.collectAsState()?.value

    suspend fun refreshGroup() {''',
    '''    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var memberPendingOwnershipTransfer by remember { mutableStateOf<GroupMemberProfile?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val isAdmin = group?.admins?.contains(myUid) == true
    val isOwner = group?.owner == myUid

    suspend fun refreshGroup() {''',
    "GroupInfoScreen: remove unused sheet-only state",
)

# ---------------------------------------------------------------------------
# 5) GroupInfoScreen.kt: avatar-crop effect -> group_edited refresh effect
# ---------------------------------------------------------------------------
apply(
    GROUPINFO,
    '''    // Naya avatar crop hoke aaya -- turant edit call karo
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
''',
    '''    // EditGroupScreen se wapas aane par (naam/desc/avatar edit hua) fresh data laao
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
''',
    "GroupInfoScreen: avatar-crop effect -> group_edited refresh",
)

# ---------------------------------------------------------------------------
# 6) GroupInfoScreen.kt: pendingAvatarBase64 fallback -> seedha g.avatar
# ---------------------------------------------------------------------------
apply(
    GROUPINFO,
    'AvatarViewerSelection.set(pendingAvatarBase64 ?: g.avatar, g.name)',
    'AvatarViewerSelection.set(g.avatar, g.name)',
    "GroupInfoScreen: AvatarViewerSelection uses g.avatar",
)

apply(
    GROUPINFO,
    '''AvatarView(
                        avatarBase64 = pendingAvatarBase64 ?: g.avatar,
                        fallbackText = g.name,
                        size = 110.dp,
                        fontSize = 38.sp
                    )''',
    '''AvatarView(
                        avatarBase64 = g.avatar,
                        fallbackText = g.name,
                        size = 110.dp,
                        fontSize = 38.sp
                    )''',
    "GroupInfoScreen: header avatar uses g.avatar",
)

# ---------------------------------------------------------------------------
# 7) GroupInfoScreen.kt: purana "Edit Group" bottom sheet poora hatao
# ---------------------------------------------------------------------------
apply(
    GROUPINFO,
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
                    .verticalScroll(rememberScrollState())
                    .imePadding()
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
                    Text("Done ✅", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
''',
    '',
    "GroupInfoScreen: remove old Edit Group bottom sheet",
)

print("\\nAll patches applied successfully.")
