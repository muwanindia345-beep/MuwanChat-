import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/WallpaperPreviewScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import com.muwan.muwanchat.DarkBubbleSent"
new_import = "import com.muwan.muwanchat.DarkBubbleSent\nimport com.muwan.muwanchat.DarkHeader"
if old_import not in content:
    print("Import anchor not found!")
    sys.exit(1)
content = content.replace(old_import, new_import, 1)

old_body = '''    Box(modifier = Modifier.fillMaxSize()) {
        // ── Wallpaper background, full screen ──
        WallpaperPreviewBackground(current)

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // ── Header (transparent, floats over wallpaper) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Preview Wallpaper", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            // ── Dummy chat bubbles ──
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(dummyMessages) { msg ->
                    DummyBubble(msg)
                }
            }
        }
    }
}'''
new_body = '''    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // ── Header, solid/opaque — ChatScreen jaisa, wallpaper ke peeche nahi ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Preview Wallpaper", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // ── Wallpaper + dummy chat bubbles, sirf isi box tak limited ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WallpaperPreviewBackground(current)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(dummyMessages) { msg ->
                    DummyBubble(msg)
                }
            }
        }
    }
}'''
if old_body not in content:
    print("WallpaperPreviewScreen body anchor not found!")
    sys.exit(1)
content = content.replace(old_body, new_body, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("WallpaperPreviewScreen.kt patched: header solid, wallpaper ab box tak limited")
