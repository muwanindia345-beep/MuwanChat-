import io
path = "app/src/main/java/com/muwan/muwanchat/screens/CreateGroupScreen.kt"
with io.open(path, "r", encoding="utf-8", newline=None) as f:
    content = f.read()

old_imports = """import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.navigation.Screen"""

new_imports = """import android.net.Uri
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
import kotlinx.coroutines.launch"""

old_state = """    var groupName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedMembers = GroupMemberSelection.selected

    var comingSoonFeature by remember { mutableStateOf<String?>(null) }"""

new_state = """    var groupName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedMembers = GroupMemberSelection.selected

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCreating by remember { mutableStateOf(false) }"""

old_dialog = """    comingSoonFeature?.let { feature ->
        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {"""

new_dialog = """    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {"""

old_button = """                Button(
                    onClick = { comingSoonFeature = "👥 Group Chat" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }"""

new_button = """                Button(
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
                }"""

patches = [
    (old_imports, new_imports, "imports"),
    (old_state, new_state, "state vars"),
    (old_dialog, new_dialog, "comingSoon dialog removal"),
    (old_button, new_button, "confirm button"),
]

for old, new, label in patches:
    c = content.count(old)
    if c != 1:
        print(f"MATCH FAILED for {label}: found {c}, expected 1")
    else:
        content = content.replace(old, new)

with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("Patched CreateGroupScreen.kt")
