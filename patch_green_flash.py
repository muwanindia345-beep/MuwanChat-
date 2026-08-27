import io

def read(p):
    with io.open(p, "r", encoding="utf-8", newline="") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

# ---- 1) MuwanChatApp.kt ----
f1 = "app/src/main/java/com/muwan/muwanchat/MuwanChatApp.kt"
s1 = read(f1)

old1a = "import coil.decode.GifDecoder"
new1a = "import coil.decode.GifDecoder\nimport coil.memory.MemoryCache"
assert old1a in s1, "pattern 1a not found"
s1 = s1.replace(old1a, new1a, 1)

old1b = '''        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {'''
new1b = '''        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .crossfade(120)
            .components {'''
assert old1b in s1, "pattern 1b not found"
s1 = s1.replace(old1b, new1b, 1)

write(f1, s1)
print("[OK] MuwanChatApp.kt patched")

# ---- 2) MessageBubble.kt ----
f2 = "app/src/main/java/com/muwan/muwanchat/screens/MessageBubble.kt"
s2 = read(f2)

old2a = "import coil.compose.AsyncImage\n"
new2a = "import coil.compose.AsyncImage\nimport androidx.compose.ui.graphics.painter.ColorPainter\n"
assert old2a in s2, "pattern 2a not found"
s2 = s2.replace(old2a, new2a, 1)

old2b = '''                        AsyncImage(
                            model = url,
                            contentDescription = "Image",
                            modifier = Modifier'''
new2b = '''                        AsyncImage(
                            model = url,
                            contentDescription = "Image",
                            placeholder = ColorPainter(Color(0xFF2A2A2A)),
                            error = ColorPainter(Color(0xFF2A2A2A)),
                            modifier = Modifier'''
assert old2b in s2, "pattern 2b not found"
s2 = s2.replace(old2b, new2b, 1)

old2c = '''                            AsyncImage(
                                model = url,
                                contentDescription = "Sticker",
                                modifier = Modifier'''
new2c = '''                            AsyncImage(
                                model = url,
                                contentDescription = "Sticker",
                                placeholder = ColorPainter(Color(0xFF2A2A2A)),
                                error = ColorPainter(Color(0xFF2A2A2A)),
                                modifier = Modifier'''
assert old2c in s2, "pattern 2c not found"
s2 = s2.replace(old2c, new2c, 1)

old2d = '''                            AsyncImage(
                                model = url,
                                contentDescription = "Video thumbnail",
                                modifier = Modifier.fillMaxSize(),'''
new2d = '''                            AsyncImage(
                                model = url,
                                contentDescription = "Video thumbnail",
                                placeholder = ColorPainter(Color(0xFF1A1A1A)),
                                error = ColorPainter(Color(0xFF1A1A1A)),
                                modifier = Modifier.fillMaxSize(),'''
assert old2d in s2, "pattern 2d not found"
s2 = s2.replace(old2d, new2d, 1)

old2e = '''                            AsyncImage(
                                model = message.previewImage,
                                contentDescription = "Link preview",
                                modifier = Modifier'''
new2e = '''                            AsyncImage(
                                model = message.previewImage,
                                contentDescription = "Link preview",
                                placeholder = ColorPainter(Color(0xFF2A2A2A)),
                                error = ColorPainter(Color(0xFF2A2A2A)),
                                modifier = Modifier'''
assert old2e in s2, "pattern 2e not found"
s2 = s2.replace(old2e, new2e, 1)

write(f2, s2)
print("[OK] MessageBubble.kt patched")
