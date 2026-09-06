# -*- coding: utf-8 -*-
# Patch 1/2 -- BroadcastChannelsScreen ke header me 3-dot dropdown menu
# add karta hai, ConversationListScreen wale menu jaisa hi style/colors
# (DarkSheet background, white text DropdownMenuItem). Menu me ek hi
# option: "Create Channel" -- ismein tap karne se sirf `showCreateComingSoon`
# state true hota hai. Popup khud agle patch (patch_broadcast_channel_
# create_channel_coming_soon.py) me wire hoga.

path = "app/src/main/java/com/muwan/muwanchat/screens/BroadcastChannelsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader'''

new_imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet'''

n = src.count(old_imports)
if n != 1:
    raise SystemExit(f"[FAIL] imports block: found {n} matches (expected 1)")
src = src.replace(old_imports, new_imports, 1)

old_fun = '''@Composable
fun BroadcastChannelsScreen(navController: NavController) {
    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            // Same header style as the rest of the app, minus a back arrow —
            // this is a top-level tab, not a screen you navigate "into".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Broadcast Channels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }'''

new_fun = '''@Composable
fun BroadcastChannelsScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    var showCreateComingSoon by remember { mutableStateOf(false) }

    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            // Same header style as the rest of the app, minus a back arrow —
            // this is a top-level tab, not a screen you navigate "into".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Broadcast Channels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DarkSheet)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create Channel", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showCreateComingSoon = true
                            }
                        )
                    }
                }
            }'''

n2 = src.count(old_fun)
if n2 != 1:
    raise SystemExit(f"[FAIL] BroadcastChannelsScreen fun body: found {n2} matches (expected 1)")
src = src.replace(old_fun, new_fun, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] 3-dot dropdown menu added with 'Create Channel' option (popup wiring in next patch)")
