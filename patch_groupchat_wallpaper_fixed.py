import os, sys

def find_file(name):
    for root, dirs, files in os.walk("."):
        if name in files:
            return os.path.join(root, name)
    return None

path = find_file("GroupChatScreen.kt")
if not path:
    print("[-] GroupChatScreen.kt nahi mili!")
    sys.exit(1)

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

changed = 0

# ---- Fix 1: outer Column ko Box + fixed WallpaperPreviewBackground + inner Column banao ----
old1 = """Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {"""

new1 = """Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        WallpaperPreviewBackground(currentWallpaper)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {"""

if old1 in content:
    count1 = content.count(old1)
    if count1 == 1:
        content = content.replace(old1, new1)
        changed += 1
        print("[+] Outer layout fix (1/3) applied — wallpaper ab fixed Box mein hai")
    else:
        print(f"[-] old1 pattern {count1} baar mila, ambiguous, skip.")
else:
    print("[*] old1 pattern nahi mila — shayad already patched, skip.")

# ---- Fix 2: andar wale weight(1f) Box se duplicate WallpaperPreviewBackground hatao ----
old2 = """        Box(modifier = Modifier.weight(1f)) {
            WallpaperPreviewBackground(currentWallpaper)
            LazyColumn("""

new2 = """        Box(modifier = Modifier.weight(1f)) {
            LazyColumn("""

if old2 in content:
    content = content.replace(old2, new2, 1)
    changed += 1
    print("[+] Duplicate wallpaper call hataya (2/3)")
else:
    print("[*] Fix 2 pattern nahi mila — shayad already patched, skip.")

# ---- Fix 3: naye outer Box ko close karne ke liye extra '}' add karo ----
old3 = "    if (showMediaSheet) {"
count3 = content.count(old3)
if count3 == 1 and changed >= 1:
    content = content.replace(old3, "    }\n\n" + old3, 1)
    changed += 1
    print("[+] Outer Box close brace add hui (3/3)")
elif count3 != 1:
    print(f"[-] Fix 3 anchor {count3} baar mila, ambiguous, skip — manual check karo.")

if changed == 3:
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"\n[+] Sab 3 fixes apply ho gaye: {path}")
elif changed == 0:
    print("\n[*] Kuch nahi badla — file already patched lag rahi hai.")
else:
    print(f"\n[-] Sirf {changed}/3 fixes apply hue — WRITE NAHI KIYA, file untouched. Manual check zaruri hai.")
