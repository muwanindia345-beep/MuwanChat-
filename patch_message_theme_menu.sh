#!/data/data/com.termux/files/usr/bin/bash
# patch_message_theme_menu.sh
# Adds a "Message Theme" option (below "Set Wallpaper") to the 3-dot
# dropdown menu in BOTH 1-on-1 chat (ChatHeader.kt) and group chat
# (GroupChatHeader inside GroupChatScreen.kt).
# Also converts the group chat menu from a bottom sheet to a dropdown
# (matching 1-on-1 chat), since it didn't have one before.
# Tapping "Message Theme" shows the existing "Coming Soon" popup for now.
# Nothing else changes — header, input bar, wallpaper all untouched.
# Run from project root (MuwanChat--main folder):
#   bash patch_message_theme_menu.sh

set -e

CHAT_HEADER="app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt"
CHAT_SCREEN="app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
GROUP_SCREEN="app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

for f in "$CHAT_HEADER" "$CHAT_SCREEN" "$GROUP_SCREEN"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: $f not found. Run this script from the MuwanChat--main root folder."
        exit 1
    fi
done

python3 - << 'PYEOF'
def patch(path, replacements, label):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for old, new in replacements:
        if new in content:
            print(f"SKIP ({label}): already patched — {old.splitlines()[0][:50]}...")
            continue
        if old in content:
            content = content.replace(old, new, 1)
            changed = True
        else:
            print(f"WARN ({label}): anchor not found — already patched or changed manually?")
            print(f"       anchor start: {old.splitlines()[0][:60]}")
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")

# ───────────────────────── ChatHeader.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/ChatHeader.kt",
    [
        (
            "import androidx.compose.material.icons.filled.Wallpaper",
            "import androidx.compose.material.icons.filled.Palette\nimport androidx.compose.material.icons.filled.Wallpaper"
        ),
        (
            "    onSetWallpaper: () -> Unit = {},\n    onAvatarClick: (() -> Unit)? = null",
            "    onSetWallpaper: () -> Unit = {},\n    onMessageTheme: () -> Unit = {},\n    onAvatarClick: (() -> Unit)? = null"
        ),
        (
            """                    DropdownMenuItem(
                        text = { Text("Set Wallpaper", color = Color.White) },
                        leadingIcon = {
                            Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = DarkAccent)
                        },
                        onClick = {
                            onMenuDismiss()
                            onSetWallpaper()
                        }
                    )
                }""",
            """                    DropdownMenuItem(
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
                }"""
        ),
    ],
    "ChatHeader.kt"
)

# ───────────────────────── ChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt",
    [
        (
            """                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(roomId))
                },""",
            """                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(roomId))
                },
                onMessageTheme = {
                    showMenuSheet = false
                    comingSoonFeature = "🎨 Message Theme"
                },"""
        ),
    ],
    "ChatScreen.kt"
)

# ───────────────────────── GroupChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
    [
        (
            "import androidx.compose.material.icons.filled.MoreVert",
            "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.Palette\nimport androidx.compose.material.icons.filled.Wallpaper\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem"
        ),
        (
            """private fun GroupChatHeader(
    groupName: String,
    groupAvatar: String?,
    memberCount: Int,
    typingUsernames: List<String>,
    onBack: () -> Unit,
    onHeaderTap: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenuClick: () -> Unit = {}
) {""",
            """private fun GroupChatHeader(
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
) {"""
        ),
        (
            """            IconButton(onClick = onVoiceCall) {
                Icon(androidx.compose.material.icons.Icons.Filled.Call, contentDescription = "Call",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onMenuClick) {
                Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu",
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}""",
            """            IconButton(onClick = onVoiceCall) {
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
        }
    }
}"""
        ),
        (
            """                onMenuClick = { showMenuSheet = true }
            )
        }

        if (showMenuSheet) {
            ChatWallpaperSheet(
                onDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(groupId))
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {""",
            """                onMenuClick = { showMenuSheet = true },
                showMenu = showMenuSheet,
                onMenuDismiss = { showMenuSheet = false },
                onSetWallpaper = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.Wallpaper.createRoute(groupId))
                },
                onMessageTheme = {
                    showMenuSheet = false
                    comingSoonFeature = "🎨 Message Theme"
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {"""
        ),
    ],
    "GroupChatScreen.kt"
)
PYEOF

echo ""
echo "Done. Verifying brace/paren balance..."
for f in "$CHAT_HEADER" "$CHAT_SCREEN" "$GROUP_SCREEN"; do
    python3 -c "
content = open('$f').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$f -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"
done
