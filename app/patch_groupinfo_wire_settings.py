path = "src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path, "r") as f:
    content = f.read()

old_header = """        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Group Info", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }"""

new_header = """        Row(
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
        }"""

assert old_header in content
content = content.replace(old_header, new_header)

old_join_requests = """                    // Join Requests -- backend abhi nahi bana, structure ready hai
                    InfoActionRow(
                        icon = Icons.Filled.HowToReg,
                        label = "Join Requests",
                        badgeCount = 0,
                        onClick = {
                            Toast.makeText(
                                context,
                                "Join Requests jald aa raha hai — abhi group sirf direct-add se banta hai",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )"""

new_join_requests = """                    InfoActionRow(
                        icon = Icons.Filled.HowToReg,
                        label = "Join Requests",
                        showRedDot = g.pendingRequests.isNotEmpty(),
                        onClick = {
                            navController.navigate(Screen.ApprovalRequests.createRoute(groupId))
                        }
                    )"""

assert old_join_requests in content
content = content.replace(old_join_requests, new_join_requests)

old_action_row = """@Composable
private fun InfoActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int? = null,
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
        if (badgeCount != null && badgeCount > 0) {
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
}"""

new_action_row = """@Composable
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
}"""

assert old_action_row in content
content = content.replace(old_action_row, new_action_row)

old_launched_effect = """    LaunchedEffect(groupId) {
        myUid = AuthDataStore.getUid(context).first() ?: ""
        isLoading = true
        try {
            refreshGroup()
        } catch (e: Exception) {
            errorMsg = e.message ?: "Network error"
        }
        isLoading = false
    }"""

new_launched_effect = """    LaunchedEffect(groupId) {
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
    }"""

assert old_launched_effect in content
content = content.replace(old_launched_effect, new_launched_effect)

with open(path, "w") as f:
    f.write(content)
print("GroupInfoScreen.kt patched successfully")
