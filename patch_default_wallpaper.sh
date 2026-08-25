#!/data/data/com.termux/files/usr/bin/bash
# patch_default_wallpaper.sh
# Sets a bundled space-themed image as the default chat background
# (used ONLY when a user hasn't picked their own wallpaper).
# Existing user-set wallpapers are untouched — this only changes the fallback.
# Applies to both 1-on-1 chat (ChatScreen) and group chat (GroupChatScreen)
# since both share the same WallpaperPreviewBackground composable.
#
# IMPORTANT: place default_chat_wallpaper.jpg in
#   app/src/main/res/drawable-nodpi/
# BEFORE or AFTER running this script (order doesn't matter for the code patch,
# but the app won't build until the image is there).
#
# Run from project root (MuwanChat--main folder):
#   bash patch_default_wallpaper.sh

set -e

WP_FILE="app/src/main/java/com/muwan/muwanchat/screens/WallpaperScreen.kt"
IMG_FILE="app/src/main/res/drawable-nodpi/default_chat_wallpaper.jpg"

if [ ! -f "$WP_FILE" ]; then
    echo "ERROR: $WP_FILE not found. Run this script from the MuwanChat--main root folder."
    exit 1
fi

if [ ! -f "$IMG_FILE" ]; then
    echo "WARN: $IMG_FILE not found yet. Code will patch fine, but build will fail until you add the image there."
fi

python3 - << 'PYEOF'
path = "app/src/main/java/com/muwan/muwanchat/screens/WallpaperScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = '''        else -> {
            Box(Modifier.fillMaxSize().background(DarkBg))
        }'''

new = '''        else -> {
            AsyncImage(
                model = com.muwan.muwanchat.R.drawable.default_chat_wallpaper,
                contentDescription = "Default wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }'''

if new in content:
    print("SKIP: WallpaperScreen.kt already patched")
elif old in content:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: WallpaperScreen.kt -> default wallpaper now shows bundled space image")
else:
    print("WARN: anchor not found in WallpaperScreen.kt — file already patched or changed manually?")
PYEOF

echo "Done."
