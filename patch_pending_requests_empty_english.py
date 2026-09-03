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
# RequestsScreen.kt — "Chat Requests" empty-state text English mein badla,
# icon/layout untouched
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/RequestsScreen.kt",
'''                    Text("Koi pending request nahi", color = Color(0xFF666688), fontSize = 16.sp)''',
'''                    Text("No pending requests", color = Color(0xFF666688), fontSize = 16.sp)''',
    "RequestsScreen.kt: empty-state text"
)

print("\n[DONE] Chat Requests empty-state text updated to English")
