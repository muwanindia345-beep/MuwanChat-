import io
path = "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

patches = []

# 1. Imports
old_imports = """import android.net.Uri
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
import com.muwan.muwanchat.data.ChatRepository
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.AddMembersRequest
import com.muwan.muwanchat.network.EditGroupRequest
import com.muwan.muwanchat.network.GroupData
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch"""
new_imports = """import android.net.Uri
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
import kotlinx.coroutines.launch"""
patches.append((old_imports, new_imports, "imports"))

# 2. OptIn annotation
old_fun = """@Composable
fun GroupInfoScreen(navController: NavController, groupId: String) {"""
new_fun = """@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(navController: NavController, groupId: String) {"""
patches.append((old_fun, new_fun, "OptIn annotation"))

# 3. New state vars
old_state = """    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val isAdmin"""
new_state = """    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var selectedMemberForSheet by remember { mutableStateOf<GroupMemberProfile?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val isAdmin"""
patches.append((old_state, new_state, "state vars"))

# 4. New action functions before leave-confirm dialog
old_fns = """    if (showLeaveConfirm) {
        AlertDialog("""
new_fns = """    fun setMemberAdmin(uid: String, makeAdmin: Boolean) {
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
        AlertDialog("""
patches.append((old_fns, new_fns, "action functions"))

# 5. Bottom sheet block, inserted right before main Column
old_col = """            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    Column(
        modifier = Modifier
"""
new_col = """            dismissButton = {
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
"""
patches.append((old_col, new_col, "bottom sheet block"))

# 6. member row: clickable -> combinedClickable (long press)
old_row = """                g.memberProfiles.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (member.uid != myUid) {
                                    navController.navigate(Screen.UserProfile.createRoute(member.uid))
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            avatarBase64 = member.avatar,
                            fallbackText = member.username,
                            size = 44.dp,"""
new_row = """                g.memberProfiles.forEach { member ->
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
                            size = 44.dp,"""
patches.append((old_row, new_row, "member row long-press"))

# 7. Add SheetOptionRow helper after RoleBadge
old_badge = """@Composable
private fun RoleBadge(text: String, emoji: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("$emoji $text", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}"""
new_badge = """@Composable
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
}"""
patches.append((old_badge, new_badge, "SheetOptionRow helper"))

for old, new, label in patches:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched GroupInfoScreen.kt with member action bottom sheet")
