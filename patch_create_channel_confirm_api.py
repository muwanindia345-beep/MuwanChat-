# -*- coding: utf-8 -*-
# CreateChannelScreen ke Confirm button se Coming Soon dialog hata ke
# asli POST /groups/create-channel API call wire karta hai (backend
# already deployed hai). Success par naye AddMembersForChannel route pe
# navigate karta hai (add-members ya skip yahi decide karega ki channel
# screen kab khulti hai), aur CreateChannelScreen ko backstack se pop kar
# deta hai (BroadcastChannels tab tak popUpTo) taaki wapas is form pe na
# aa sake.

path = "app/src/main/java/com/muwan/muwanchat/screens/CreateChannelScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_imports = '''import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.navigation.Screen'''

new_imports = '''import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.CreateChannelRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch'''

n = src.count(old_imports)
if n != 1:
    raise SystemExit(f"[FAIL] imports block: found {n} matches (expected 1)")
src = src.replace(old_imports, new_imports, 1)

old_state = '''    var channelName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }
    var showComingSoon by remember { mutableStateOf(false) }'''

new_state = '''    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channelName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var avatarBase64 by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    fun confirmCreateChannel() {
        val name = channelName.trim()
        if (name.isEmpty() || isCreating) return
        isCreating = true
        scope.launch {
            try {
                val token = AuthDataStore.getToken(context).first()
                if (token == null) {
                    Toast.makeText(context, "Login required", Toast.LENGTH_SHORT).show()
                    isCreating = false
                    return@launch
                }
                val res = RetrofitClient.chatApi.createChannel(
                    "Bearer $token",
                    CreateChannelRequest(name = name, avatar = avatarBase64, description = description.trim())
                )
                val group = res.body()?.group
                if (res.isSuccessful && res.body()?.success == true && group != null) {
                    navController.navigate(
                        Screen.AddMembersForChannel.createRoute(group.id, group.name)
                    ) {
                        popUpTo(Screen.BroadcastChannels.route) { inclusive = false }
                    }
                } else {
                    Toast.makeText(context, "Channel nahi ban paya, dobara try karo", Toast.LENGTH_SHORT).show()
                    isCreating = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, friendlyErrorMessage(e), Toast.LENGTH_SHORT).show()
                isCreating = false
            }
        }
    }'''

n2 = src.count(old_state)
if n2 != 1:
    raise SystemExit(f"[FAIL] state block: found {n2} matches (expected 1)")
src = src.replace(old_state, new_state, 1)

old_button = '''                Button(
                    onClick = { showComingSoon = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showComingSoon) {
        ComingSoonDialog(
            feature = "Create Channel",
            onDismiss = { showComingSoon = false }
        )
    }
}'''

new_button = '''                Button(
                    onClick = { confirmCreateChannel() },
                    enabled = channelName.isNotBlank() && !isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}'''

n3 = src.count(old_button)
if n3 != 1:
    raise SystemExit(f"[FAIL] confirm button block: found {n3} matches (expected 1)")
src = src.replace(old_button, new_button, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Confirm button now calls POST /groups/create-channel and navigates to AddMembersForChannel")
