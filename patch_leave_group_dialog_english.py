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

apply(
    "app/src/main/java/com/muwan/muwanchat/screens/GroupInfoScreen.kt",
'''                    "Tum is group ke messages ab nahi dekh paoge jab tak dobara add na ho.",''',
'''                    "You won't be able to see this group's messages anymore until you're added back.",''',
    "GroupInfoScreen.kt: Leave Group dialog subtitle"
)

print("\n[DONE] Leave Group dialog subtitle updated to English")
