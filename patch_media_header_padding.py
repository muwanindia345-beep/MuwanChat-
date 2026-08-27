p = "app/src/main/java/com/muwan/muwanchat/screens/MediaScreen.kt"
s = open(p, encoding="utf-8").read()

old = '''    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {'''
new = '''    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {'''

if old in s:
    s = s.replace(old, new)
    open(p, "w", encoding="utf-8").write(s)
    print("MediaScreen.kt: systemBarsPadding added to root Column")
elif "systemBarsPadding()" in s:
    print("MediaScreen.kt: already has systemBarsPadding, no change needed")
else:
    print("WARNING: anchor not found, check manually")

print("DONE")
