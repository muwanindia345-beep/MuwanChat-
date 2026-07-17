import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/WallpaperScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.material.icons.filled.DeleteOutline"
new_import = (
    "import androidx.compose.material.icons.filled.DeleteOutline\n"
    "import androidx.compose.material.icons.filled.Visibility"
)
if old_import not in content:
    print("Import anchor not found!")
    sys.exit(1)
content = content.replace(old_import, new_import)

old_block = '''            // ── Preview ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                WallpaperPreviewBackground(current)
            }

            // ── Gallery + Remove row ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { galleryPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccent)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = { removeWallpaper() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05555))
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }'''

new_block = '''            // ── Gallery + Remove row ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { galleryPicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccent)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { removeWallpaper() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05555))
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove", maxLines = 1)
                }
            }

            // ── Preview Wallpaper button ──
            OutlinedButton(
                onClick = {
                    navController.navigate(
                        com.muwan.muwanchat.navigation.Screen.WallpaperPreview.createRoute(roomId)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preview Wallpaper", maxLines = 1)
            }'''

if old_block not in content:
    print("Block anchor not found!")
    sys.exit(1)
content = content.replace(old_block, new_block)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("WallpaperScreen.kt patched: preview box removed, buttons fixed, Preview Wallpaper button added")
