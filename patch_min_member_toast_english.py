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
# CreateGroupScreen.kt — "Confirm" pe tap karne par bina member ke aane
# wala Toast message English mein badla
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/CreateGroupScreen.kt",
'''                            Toast.makeText(context, "Kam se kam 1 member add karo", Toast.LENGTH_SHORT).show()''',
'''                            Toast.makeText(context, "Add at least 1 member", Toast.LENGTH_SHORT).show()''',
    "CreateGroupScreen.kt: min-member Toast message"
)

print("\n[DONE] Min-member Toast message updated to English")
EOF 
