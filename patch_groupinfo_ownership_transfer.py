path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with open(path) as f:
    content = f.read()

# --- 1. naya state var: kis member ko transfer karna confirm karna hai ---
old1 = "    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }"
new1 = '''    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var memberPendingOwnershipTransfer by remember { mutableStateOf<GroupMemberProfile?>(null) }'''
assert old1 in content
content = content.replace(old1, new1, 1)

# --- 2. transferOwnership() function add karo, setMemberAdmin ke baad ---
old2 = '''    fun kickMember(uid: String) {'''
new2 = '''    fun transferOwnership(uid: String) {
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

    fun kickMember(uid: String) {'''
assert old2 in content
content = content.replace(old2, new2, 1)

# --- 3. confirmation dialog add karo, showLeaveConfirm dialog ke baad ---
old3 = '''    selectedMemberForSheet?.let { member ->
        val canToggleAdmin = isOwner && !member.isOwner
        val canKick = isAdmin && !member.isOwner && member.uid != myUid'''
new3 = '''    memberPendingOwnershipTransfer?.let { target ->
        AlertDialog(
            onDismissRequest = { memberPendingOwnershipTransfer = null },
            containerColor = DarkHeader,
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
        val canKick = isAdmin && !member.isOwner && member.uid != myUid'''
assert old3 in content
content = content.replace(old3, new3, 1)

# --- 4. bottom sheet me "Transfer Ownership" row add karo, admin toggle ke baad ---
old4 = '''                if (canKick) {
                    SheetOptionRow(
                        icon = Icons.Filled.PersonRemove,
                        label = "Kick this member",
                        tint = Color(0xFFFF3B30)
                    ) {
                        kickMember(member.uid)
                    }
                }'''
new4 = '''                if (canTransferOwnership) {
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
                }'''
assert old4 in content
content = content.replace(old4, new4, 1)

with open(path, "w") as f:
    f.write(content)
print("GroupInfoScreen.kt patched: Transfer Ownership option + confirm dialog + admin-permission expanded to all admins")
