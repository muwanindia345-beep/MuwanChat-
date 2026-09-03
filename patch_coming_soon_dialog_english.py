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
# ComingSoonDialog.kt — ek hi shared component hai jo Voice Call, Video
# Call dono (1-1 chat aur group chat, dono jagah) ke liye use hota hai.
# Isliye yeh ek fix sabhi jagah apply ho jaayega, alag-alag files nahi.
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/ComingSoonDialog.kt",
'''                    "Yeh feature abhi development mein hai.\\nJald aayega! 🚀",''',
'''                    "This feature is currently under development.\\nComing soon! 🚀",''',
    "ComingSoonDialog.kt: dialog body text"
)

print("\n[DONE] Coming Soon dialog updated to English (applies to Voice/Video call, 1-1 + group chat)")
