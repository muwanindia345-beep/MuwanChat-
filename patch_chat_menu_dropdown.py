import os
import sys

def find_file(name):
    for root, dirs, files in os.walk("."):
        if name in files:
            return os.path.join(root, name)
    return None

# ---------- 1. ChatHeader.kt: pura overwrite (chhoti file, structure same) ----------
header_path = find_file("ChatHeader.kt")
if not header_path:
    print("[-] ChatHeader.kt nahi mili!")
    sys.exit(1)

header_content = '''package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkHeader

@Composable
fun ChatHeader(
    receiverUsername: String,
    isOnline: Boolean,
    isTyping: Boolean = false,
    avatarBase64: String? = null,
    onBack: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {},
    showMenu: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onSetWallpaper: () -> Unit = {},
    onAvatarClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkHeader)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            AvatarView(
                avatarBase64 = avatarBase64,
                fallbackText = receiverUsername,
                size = 38.dp,
                fontSize = 16.sp,
                onClick = onAvatarClick
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    receiverUsername,
                    color = DarkAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = when {
                    isTyping -> "typing..."
                    isOnline -> "Online"
                    else -> "Offline"
                }
                val statusColor = when {
                    isTyping -> DarkAccent
                    isOnline -> Color(0xFF4CD964)
                    else -> Color(0xFF888888)
                }
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row {
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call",
                    tint = DarkAccent, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu",
                        tint = DarkAccent, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                    containerColor = DarkHeader
                ) {
                    DropdownMenuItem(
                        text = { Text("Set Wallpaper", color = Color.White) },
                        leadingIcon = {
                            Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = DarkAccent)
                        },
                        onClick = {
                            onMenuDismiss()
                            onSetWallpaper()
                        }
                    )
                }
            }
        }
    }
}
'''

with open(header_path, "w", encoding="utf-8") as f:
    f.write(header_content)
print(f"[+] ChatHeader.kt update ho gayi: {header_path}")

# ---------- 2. ChatScreen.kt: sirf targeted str_replace (bada file, kuch aur nahi chhedna) ----------
screen_path = find_file("ChatScreen.kt")
if not screen_path:
    print("[-] ChatScreen.kt nahi mili!")
    sys.exit(1)

with open(screen_path, "r", encoding="utf-8") as f:
    content = f.read()

old_block = """            ChatHeader(
                receiverUsername = receiverUsername,
                isOnline = isReceiverOnline,
                isTyping = isReceiverTyping,
                avatarBase64 = conversationEntity?.avatar,
                onBack = { navController.popBackStack() },
                onVideoCall = { comingSoonFeature = "\U0001F4F9 Video Call" },
                onVoiceCall = { comingSoonFeature = "\U0001F4DE Voice Call" },
                onMenuClick = { showMenuSheet = true },
                onAvatarClick = {
                    AvatarViewerSelection.set(conversationEntity?.avatar, receiverUsername)
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.ViewAvatar.route)
                }
            )
        }

        if (showMenuSheet) {
            ChatWallpaperSheet(
                onDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(roomId))
                }
            )
        }"""

new_block = """            ChatHeader(
                receiverUsername = receiverUsername,
                isOnline = isReceiverOnline,
                isTyping = isReceiverTyping,
                avatarBase64 = conversationEntity?.avatar,
                onBack = { navController.popBackStack() },
                onVideoCall = { comingSoonFeature = "\U0001F4F9 Video Call" },
                onVoiceCall = { comingSoonFeature = "\U0001F4DE Voice Call" },
                onMenuClick = { showMenuSheet = true },
                showMenu = showMenuSheet,
                onMenuDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(roomId))
                },
                onAvatarClick = {
                    AvatarViewerSelection.set(conversationEntity?.avatar, receiverUsername)
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.ViewAvatar.route)
                }
            )
        }"""

if old_block in content:
    content = content.replace(old_block, new_block)
    with open(screen_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[+] ChatScreen.kt update ho gayi: {screen_path}")
    print("[+] Chat screen ka 3-dot ab dropdown menu kholega (bottom sheet ki jagah)")
elif "showMenu = showMenuSheet" in content:
    print("[*] Patch pehle se hi applied lag raha hai, kuch nahi kiya.")
else:
    print("[-] Old block match nahi hua — file already modified/different hai.")
    print("    Manually check karo ya mujhe current ChatScreen.kt ka relevant hissa bhejo.")
    sys.exit(1)
