def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

# ---------------------------------------------------------------------------
# UserSearchScreen.kt — "New Chat" search box ka placeholder text hindi-mix
# se plain English mein badla, box/UI kuch touch nahi hua
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/UserSearchScreen.kt",
'''                placeholder = { Text("Username ya email search karo...", color = Color(0xFF888888)) },''',
'''                placeholder = { Text("Search username...", color = Color(0xFF888888)) },''',
    "UserSearchScreen.kt: search box placeholder text"
)

print("\n[DONE] Search box placeholder text updated to English")
