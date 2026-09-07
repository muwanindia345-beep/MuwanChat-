# -*- coding: utf-8 -*-
# AddFromContactsScreen ko channel-creation flow ke liye extend karta hai:
#   - Naye optional params: channelId (null jab tak "add to existing group"
#     jaisa normal reuse ho), channelName
#   - Channel-mode me: bottom-right "Skip" FAB dikhta hai -- tap karte hi
#     seedha naye channel ki GroupChatScreen khul jaati hai, kisi ko add
#     kiye bina
#   - Channel-mode me back-arrow ka behavior badal jaata hai: agar kuch
#     contacts selected hain to pehle unhe addGroupMembers API se add karta
#     hai, phir GroupChatScreen khol deta hai (back button = "done", FAB =
#     "skip" -- dono alag actions hain jaisa confirm hua)
#   - Normal reuse (channelId == null, jaise CreateGroupScreen/GroupInfoScreen
#     se) me kuch nahi badla -- back arrow plain popBackStack hi karta hai

path = "app/src/main/java/com/muwan/muwanchat/screens/AddFromContactsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import com.muwan.muwanchat.network.UserItem
import kotlinx.coroutines.flow.first'''

new_imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.AddMembersRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import com.muwan.muwanchat.network.UserItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch'''

n = src.count(old_imports)
if n != 1:
    raise SystemExit(f"[FAIL] imports block: found {n} matches (expected 1)")
src = src.replace(old_imports, new_imports, 1)

old_sig = '''@Composable
fun AddFromContactsScreen(navController: NavController) {
    val context = LocalContext.current

    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }'''

new_sig = '''@Composable
fun AddFromContactsScreen(
    navController: NavController,
    channelId: String? = null,
    channelName: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var isFinishing by remember { mutableStateOf(false) }

    fun goToChannel() {
        navController.navigate(Screen.GroupChat.createRoute(channelId ?: "", channelName)) {
            popUpTo(Screen.BroadcastChannels.route) { inclusive = false }
        }
    }

    // Back arrow ka "done" behavior channel-mode me: jo bhi select kiya
    // hai use add karo, phir channel screen khol do. Kuch select nahi
    // kiya ho to bhi seedha channel khul jaata hai (FAB "Skip" bhi
    // yahi karta hai, bas explicit "kuch add nahi karna" ke liye hai).
    fun finishChannelSetup() {
        if (isFinishing) return
        isFinishing = true
        val toAdd = GroupMemberSelection.selected.map { it.uid }
        if (channelId == null || toAdd.isEmpty()) {
            GroupMemberSelection.clear()
            goToChannel()
            return
        }
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first()
                if (token != null) {
                    RetrofitClient.chatApi.addGroupMembers(
                        "Bearer $token", channelId, AddMembersRequest(memberUids = toAdd)
                    )
                }
            } catch (_: Exception) {
                // Channel already ban chuka hai -- member add fail bhi ho
                // jaaye to bhi channel screen khulni chahiye, baad me
                // Add from Contacts se dobara add kiya ja sakta hai.
            }
            GroupMemberSelection.clear()
            goToChannel()
        }
    }'''

n2 = src.count(old_sig)
if n2 != 1:
    raise SystemExit(f"[FAIL] function signature block: found {n2} matches (expected 1)")
src = src.replace(old_sig, new_sig, 1)

old_header = '''            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Add from contacts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }'''

new_header = '''            IconButton(onClick = {
                if (channelId != null) finishChannelSetup() else navController.popBackStack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Add from contacts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }'''

n3 = src.count(old_header)
if n3 != 1:
    raise SystemExit(f"[FAIL] header back-arrow block: found {n3} matches (expected 1)")
src = src.replace(old_header, new_header, 1)

# Poore screen ko Box me wrap karke bottom-right "Skip" FAB add karta hai,
# sirf channel-mode me.
old_wrapper_open = '''    Column(
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
        ) {'''

new_wrapper_open = '''    Box(modifier = Modifier.fillMaxSize()) {
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
        ) {'''

n4 = src.count(old_wrapper_open)
if n4 != 1:
    raise SystemExit(f"[FAIL] wrapper-open anchor: found {n4} matches (expected 1)")
src = src.replace(old_wrapper_open, new_wrapper_open, 1)

old_wrapper_close = '''            }
        }
    }
}'''

new_wrapper_close = '''            }
        }
    }

    if (channelId != null) {
        ExtendedFloatingActionButton(
            onClick = { GroupMemberSelection.clear(); goToChannel() },
            containerColor = DarkAccent,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.ArrowForward, contentDescription = null) },
            text = { Text("Skip") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
    }
}'''

n5 = src.count(old_wrapper_close)
if n5 != 1:
    raise SystemExit(f"[FAIL] wrapper-close anchor: found {n5} matches (expected 1)")
src = src.replace(old_wrapper_close, new_wrapper_close, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Channel-mode wired: Skip FAB (always skip) + back arrow (add selected, then open channel)")
