#!/data/data/com.termux/files/usr/bin/bash
# patch_message_theme_wiring.sh
# Final wiring for the "Message Theme" feature:
#  1. Adds Screen.MessageTheme route to NavGraph.kt + wires MessageThemeScreen
#  2. Replaces the "Coming Soon" popup with real navigation in both
#     ChatScreen.kt (1-on-1) and GroupChatScreen.kt (group)
#  3. Makes both screens observe the saved per-chat theme and pass it
#     into every MessageBubble(...) call, so the selected theme actually
#     shows up in the chat.
# Requires: patch_bubbletheme_db.sh and patch_messagebubble_theme.sh must
# already be applied (BubbleTheme/BubbleThemePresets/ChatBubbleThemeDao
# must exist), and MessageThemeScreen.kt must already be created.
# Run from project root (MuwanChat--main folder):
#   bash patch_message_theme_wiring.sh

set -e

NAV_FILE="app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt"
CHAT_FILE="app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
GROUP_FILE="app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

for f in "$NAV_FILE" "$CHAT_FILE" "$GROUP_FILE"; do
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
            print(f"SKIP ({label}): already patched")
            continue
        if old in content:
            content = content.replace(old, new, 1)
            changed = True
        else:
            print(f"WARN ({label}): anchor not found — already patched or changed manually?")
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")

# ───────────────────────── NavGraph.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/navigation/NavGraph.kt",
    [
        (
            '''    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
    object WallpaperPreview : Screen("wallpaper_preview/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper_preview/$roomId"
    }''',
            '''    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
    object WallpaperPreview : Screen("wallpaper_preview/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper_preview/$roomId"
    }
    object MessageTheme    : Screen("message_theme/{roomId}") {
        fun createRoute(roomId: String) = "message_theme/$roomId"
    }'''
        ),
        (
            '''        composable(Screen.WallpaperPreview.route) { back ->
            WallpaperPreviewScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }''',
            '''        composable(Screen.WallpaperPreview.route) { back ->
            WallpaperPreviewScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.MessageTheme.route) { back ->
            MessageThemeScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }'''
        ),
    ],
    "NavGraph.kt"
)

# ───────────────────────── ChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt",
    [
        (
            '    val currentWallpaper by db.chatWallpaperDao().observeByRoomId(roomId).collectAsState(initial = null)',
            '''    val currentWallpaper by db.chatWallpaperDao().observeByRoomId(roomId).collectAsState(initial = null)
    val currentBubbleThemeEntity by db.chatBubbleThemeDao().observeByRoomId(roomId).collectAsState(initial = null)
    val bubbleTheme = com.muwan.muwanchat.data.BubbleThemePresets.fromId(currentBubbleThemeEntity?.themeId)'''
        ),
        (
            '''                onMessageTheme = {
                    showMenuSheet = false
                    comingSoonFeature = "🎨 Message Theme"
                },''',
            '''                onMessageTheme = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.MessageTheme.createRoute(roomId))
                },'''
        ),
        (
            '''                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(visible = replyTo != null) {''',
            '''                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        },
                        bubbleTheme = bubbleTheme
                    )
                }
            }
        }

        AnimatedVisibility(visible = replyTo != null) {'''
        ),
    ],
    "ChatScreen.kt"
)

# ───────────────────────── GroupChatScreen.kt ─────────────────────────
patch(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
    [
        (
            '    val currentWallpaper by db.chatWallpaperDao().observeByRoomId(groupId).collectAsState(initial = null)',
            '''    val currentWallpaper by db.chatWallpaperDao().observeByRoomId(groupId).collectAsState(initial = null)
    val currentBubbleThemeEntity by db.chatBubbleThemeDao().observeByRoomId(groupId).collectAsState(initial = null)
    val bubbleTheme = com.muwan.muwanchat.data.BubbleThemePresets.fromId(currentBubbleThemeEntity?.themeId)'''
        ),
        (
            '''                onMessageTheme = {
                    showMenuSheet = false
                    comingSoonFeature = "🎨 Message Theme"
                }''',
            '''                onMessageTheme = {
                    showMenuSheet = false
                    navController.navigate(com.muwan.muwanchat.navigation.Screen.MessageTheme.createRoute(groupId))
                }'''
        ),
        (
            '''                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        }
                    )
                }
            }
        }


        AnimatedVisibility(visible = replyTo != null) {''',
            '''                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessageIds = setOf(it.id)
                                if (!it.isDeleted) showReactionPicker = true
                            }
                        },
                        bubbleTheme = bubbleTheme
                    )
                }
            }
        }


        AnimatedVisibility(visible = replyTo != null) {'''
        ),
    ],
    "GroupChatScreen.kt"
)
PYEOF

echo ""
echo "Verifying brace/paren balance..."
for f in "$NAV_FILE" "$CHAT_FILE" "$GROUP_FILE"; do
    python3 -c "
content = open('$f').read()
o, c = content.count('{'), content.count('}')
po, pc = content.count('('), content.count(')')
status = 'OK' if (o == c and po == pc) else 'MISMATCH!'
print(f'$f -> braces {o}/{c}, parens {po}/{pc} -> {status}')
"
done
