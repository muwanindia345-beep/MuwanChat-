path = "app/src/main/java/com/muwan/muwanchat/Colors.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = "val DarkHeader = Color(0xFF16213e)"
new = "val DarkHeader = Color(0xFF000000)"

if old not in content:
    print("WARN: anchor not found — file already patched or changed manually?")
else:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched: DarkHeader -> #000000 (pure black)")
