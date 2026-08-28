def patch(path, old, new, label):
    with open(path, encoding='utf-8') as f:
        s = f.read()
    if old not in s and new in s:
        print(f"[skip] {label} (already reverted)")
        return
    assert old in s, f"[FAIL] pattern not found for: {label} in {path}"
    s = s.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print(f"[ok] reverted: {label}")

BASE = "app/src/main/java/com/muwan/muwanchat"

patch(f"{BASE}/network/ChatApi.kt",
    "    val is_forwarded: Boolean = false,\n"
    "    val mentions: List<String>? = null\n"
    ")",
    "    val is_forwarded: Boolean = false\n"
    ")",
    "MessageItem.mentions field")

patch(f"{BASE}/screens/ChatMessage.kt",
    "    val isForwarded: Boolean = false,\n"
    "    val mentions: List<String> = emptyList()\n"
    ")",
    "    val isForwarded: Boolean = false\n"
    ")",
    "ChatMessage.mentions field")

patch(f"{BASE}/screens/ChatMessage.kt",
    "    isForwarded = is_forwarded,\n"
    "    mentions = mentions ?: emptyList()\n"
    ")",
    "    isForwarded = is_forwarded\n"
    ")",
    "MessageItem.toChatMessage mentions mapping")

patch(f"{BASE}/screens/ChatMessage.kt",
    "    isForwarded = isForwarded,\n"
    "    mentions = mentions?.split(\",\")?.filter { it.isNotBlank() } ?: emptyList()\n"
    ")",
    "    isForwarded = isForwarded\n"
    ")",
    "MessageEntity.toChatMessage mentions mapping")

patch(f"{BASE}/data/MessageEntity.kt",
    "    val previewUrl: String? = null,\n"
    "    val mentions: String? = null   // comma-separated uids jinhe is message mein mention kiya gaya\n"
    ")",
    "    val previewUrl: String? = null\n"
    ")",
    "MessageEntity.mentions column")

import os
if os.path.exists("patch_part1.py"):
    os.remove("patch_part1.py")
    print("[ok] removed: patch_part1.py (no longer needed)")
else:
    print("[skip] patch_part1.py already removed")

print("\nRevert done. Adhura 'mentions' feature completely hat gaya.")
print("NOTE: DB version (22) jaanbujh kar nahi chheda -- agar is device pe pehle se")
print("v22 wali DB bani hui hai (mentions column ke saath), naya build install karne")
print("se pehle app UNINSTALL ya clear data kar lena, warna Room data-integrity crash dega.")
