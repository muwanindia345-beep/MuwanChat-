import sys, shutil
F = "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt"
s = open(F, encoding="utf-8").read()

edits = [
 ("    displayType: String = category,\n    setUploading: (Boolean) -> Unit\n) {\n    val id = UUID.randomUUID().toString()\n    val filename = withContext(Dispatchers.IO) { getFileName(context, uri) }\n    val guessedMime = context.contentResolver.getType(uri) ?: \"application/octet-stream\"",
  "    displayType: String = category,\n    mimeOverride: String? = null,\n    setUploading: (Boolean) -> Unit\n) {\n    val id = UUID.randomUUID().toString()\n    val filename = withContext(Dispatchers.IO) { getFileName(context, uri) }\n    val guessedMime = mimeOverride?.takeIf { it.startsWith(\"image/\") }\n        ?: context.contentResolver.getType(uri)\n        ?: \"application/octet-stream\""),
 ("            onGifReceived = { uri, _, release ->",
  "            onGifReceived = { uri, kbMime, release ->"),
 ("skipCompression = true, setUploading = {})\n                    release()",
  "skipCompression = true, mimeOverride = kbMime, setUploading = {})\n                    release()"),
]
changed = False
for old, new in edits:
    if new in s:
        print("already applied:", old[:40].strip()); continue
    if s.count(old) != 1:
        sys.exit(f"ERROR: expected exactly 1 match, found {s.count(old)} for: {old[:60]}")
    s = s.replace(old, new)
    changed = True
if not changed:
    print("nothing to do, already patched"); sys.exit(0)
shutil.copy(F, F + ".bak_gifmime")
open(F, "w", encoding="utf-8").write(s)
print("app patched OK (backup: ChatScreen.kt.bak_gifmime)")
