# -*- coding: utf-8 -*-
# GroupChatHeader ko channel-aware banata hai:
#   - isChannel=true par: Video/Voice call icons hide, naam ke neeche wala
#     member-count/typing subtitle bhi hide (sirf naam)
#   - 3-dot se pehle ek Share (Link icon) button add hota hai
#   - Dropdown menu channel ke liye Wallpaper/Message-Theme ki jagah
#     Profile / Share / Leave dikhata hai
# Call site (GroupChatScreen) me wiring:
#   - onProfile aur onLeave abhi ComingSoonDialog dikhate hain (existing
#     comingSoonFeature mechanism reuse) -- Leave ka koi backend endpoint
#     abhi exist nahi karta, isliye real nahi banaya
#   - onShareLink asli kaam karta hai: invite link (GroupSettingsScreen
#     jaisa hi "muwanchat://join/<code>" format) ke saath Android share
#     sheet khol deta hai

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old_icon_imports = '''import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper'''

new_icon_imports = '''import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Share'''

n0 = src.count(old_icon_imports)
if n0 != 1:
    raise SystemExit(f"[FAIL] icon imports anchor: found {n0} matches (expected 1)")
src = src.replace(old_icon_imports, new_icon_imports, 1)

# ---- 1. Header composable signature + body ----

old_sig = '''@Composable
private fun GroupChatHeader(
    groupName: String,
    groupAvatar: String?,
    memberCount: Int,
    typingUsernames: List<String>,
    onBack: () -> Unit,
    onHeaderTap: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {},
    showMenu: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onSetWallpaper: () -> Unit = {},
    onMessageTheme: () -> Unit = {}
) {'''

new_sig = '''@Composable
private fun GroupChatHeader(
    groupName: String,
    groupAvatar: String?,
    memberCount: Int,
    typingUsernames: List<String>,
    onBack: () -> Unit,
    onHeaderTap: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {},
    showMenu: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onSetWallpaper: () -> Unit = {},
    onMessageTheme: () -> Unit = {},
    isChannel: Boolean = false,
    onShareLink: () -> Unit = {},
    onProfile: () -> Unit = {},
    onLeave: () -> Unit = {}
) {'''

n = src.count(old_sig)
if n != 1:
    raise SystemExit(f"[FAIL] GroupChatHeader signature: found {n} matches (expected 1)")
src = src.replace(old_sig, new_sig, 1)

old_subtitle = '''                val statusText = when {
                    typingUsernames.isNotEmpty() ->
                        if (typingUsernames.size == 1) "${typingUsernames[0]} is typing..."
                        else "${typingUsernames.size} people typing..."
                    else -> "$memberCount member${if (memberCount != 1) "s" else ""}"
                }
                Text(
                    statusText,
                    color = if (typingUsernames.isNotEmpty()) DarkAccent else Color(0xFF888888),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )'''

new_subtitle = '''                if (!isChannel) {
                    val statusText = when {
                        typingUsernames.isNotEmpty() ->
                            if (typingUsernames.size == 1) "${typingUsernames[0]} is typing..."
                            else "${typingUsernames.size} people typing..."
                        else -> "$memberCount member${if (memberCount != 1) "s" else ""}"
                    }
                    Text(
                        statusText,
                        color = if (typingUsernames.isNotEmpty()) DarkAccent else Color(0xFF888888),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }'''

n2 = src.count(old_subtitle)
if n2 != 1:
    raise SystemExit(f"[FAIL] subtitle block: found {n2} matches (expected 1)")
src = src.replace(old_subtitle, new_subtitle, 1)

old_trailing_row = '''        Row {
            IconButton(onClick = onVideoCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.VideoCall, contentDescription = "Video",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onVoiceCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.Call, contentDescription = "Call",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                    modifier = Modifier.background(DarkSheet)
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
                    DropdownMenuItem(
                        text = { Text("Message Theme", color = Color.White) },
                        leadingIcon = {
                            Icon(Icons.Filled.Palette, contentDescription = null, tint = DarkAccent)
                        },
                        onClick = {
                            onMenuDismiss()
                            onMessageTheme()
                        }
                    )
                }
            }
        }'''

new_trailing_row = '''        Row {
            if (!isChannel) {
                IconButton(onClick = onVideoCall) {
                    Icon(androidx.compose.material.icons.Icons.Filled.VideoCall, contentDescription = "Video",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onVoiceCall) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Call, contentDescription = "Call",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            } else {
                IconButton(onClick = onShareLink) {
                    Icon(Icons.Filled.Link, contentDescription = "Share Link",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                    modifier = Modifier.background(DarkSheet)
                ) {
                    if (isChannel) {
                        DropdownMenuItem(
                            text = { Text("Profile", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = DarkAccent)
                            },
                            onClick = {
                                onMenuDismiss()
                                onProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null, tint = DarkAccent)
                            },
                            onClick = {
                                onMenuDismiss()
                                onShareLink()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Leave", color = Color(0xFFFF3B30)) },
                            leadingIcon = {
                                Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFF3B30))
                            },
                            onClick = {
                                onMenuDismiss()
                                onLeave()
                            }
                        )
                    } else {
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
                        DropdownMenuItem(
                            text = { Text("Message Theme", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Filled.Palette, contentDescription = null, tint = DarkAccent)
                            },
                            onClick = {
                                onMenuDismiss()
                                onMessageTheme()
                            }
                        )
                    }
                }
            }
        }'''

n3 = src.count(old_trailing_row)
if n3 != 1:
    raise SystemExit(f"[FAIL] trailing icons row: found {n3} matches (expected 1)")
src = src.replace(old_trailing_row, new_trailing_row, 1)

# ---- 2. Call site wiring ----

old_call = '''            GroupChatHeader(
                groupName = groupName,
                groupAvatar = groupAvatar ?: conversationEntity?.avatar,
                memberCount = memberCount,
                typingUsernames = typingUids.mapNotNull { memberNames[it] },
                onBack = { navController.popBackStack() },
                onHeaderTap = {
                    navController.navigate(Screen.GroupInfo.createRoute(groupId))
                },
                onVideoCall = { comingSoonFeature = "📹 Group Video Call" },
                onVoiceCall = { comingSoonFeature = "📞 Group Voice Call" },
                onMenuClick = { showMenuSheet = true },
                showMenu = showMenuSheet,
                onMenuDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(groupId))
                },
                onMessageTheme = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.MessageTheme.createRoute(groupId))
                }
            )'''

new_call = '''            GroupChatHeader(
                groupName = groupName,
                groupAvatar = groupAvatar ?: conversationEntity?.avatar,
                memberCount = memberCount,
                typingUsernames = typingUids.mapNotNull { memberNames[it] },
                onBack = { navController.popBackStack() },
                onHeaderTap = {
                    navController.navigate(Screen.GroupInfo.createRoute(groupId))
                },
                onVideoCall = { comingSoonFeature = "📹 Group Video Call" },
                onVoiceCall = { comingSoonFeature = "📞 Group Voice Call" },
                onMenuClick = { showMenuSheet = true },
                showMenu = showMenuSheet,
                onMenuDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(groupId))
                },
                onMessageTheme = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.MessageTheme.createRoute(groupId))
                },
                isChannel = group?.isChannel ?: false,
                onShareLink = {
                    val code = group?.inviteCode
                    if (code == null) {
                        Toast.makeText(context, "No invite link yet", Toast.LENGTH_SHORT).show()
                    } else {
                        val shareText = "Join \\"${groupName}\\" on MuwanChat: muwanchat://join/$code"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite link"))
                    }
                },
                onProfile = { comingSoonFeature = "Channel Profile" },
                onLeave = { comingSoonFeature = "Leave Channel" }
            )'''

n4 = src.count(old_call)
if n4 != 1:
    raise SystemExit(f"[FAIL] GroupChatHeader call site: found {n4} matches (expected 1)")
src = src.replace(old_call, new_call, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Channel header: no call icons, no member-count subtitle, Share icon, Profile/Share/Leave dropdown")
