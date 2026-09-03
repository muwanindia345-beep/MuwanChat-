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
# CreateGroupScreen.kt — description placeholder + "Add members" ke dono
# option-subtitles English mein badle, baaki UI (layout, icons) untouched
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/CreateGroupScreen.kt",
'''                            "Purpose, rules, kuch bhi likh sakte ho",''',
'''                            "Purpose, rules, anything you want to write",''',
    "CreateGroupScreen.kt: description placeholder"
)

apply(
    "app/src/main/java/com/muwan/muwanchat/screens/CreateGroupScreen.kt",
'''                    subtitle = "Apne accepted connections se select karo",''',
'''                    subtitle = "Select from your accepted connections",''',
    "CreateGroupScreen.kt: 'Add from contacts' subtitle"
)

apply(
    "app/src/main/java/com/muwan/muwanchat/screens/CreateGroupScreen.kt",
'''                    subtitle = "Kisi ko bhi search karke add karo",''',
'''                    subtitle = "Search for anyone and add them",''',
    "CreateGroupScreen.kt: 'Search members' subtitle"
)

print("\n[DONE] New Group screen texts updated to English")
