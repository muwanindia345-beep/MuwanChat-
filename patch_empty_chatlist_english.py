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
# ConversationListScreen.kt — "Koi chat nahi hai" empty-state text English
# mein badla, baaki UI (icon, layout, FAB) untouched
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt",
'''                        Text("Koi chat nahi abhi", color = Color(0xFF666688), fontSize = 16.sp)''',
'''                        Text("No chats yet", color = Color(0xFF666688), fontSize = 16.sp)''',
    "ConversationListScreen.kt: empty-state title"
)

apply(
    "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt",
'''                        Text("Neeche + se naya chat shuru karo", color = Color(0xFF444466), fontSize = 13.sp)''',
'''                        Text("Start a new chat using + below", color = Color(0xFF444466), fontSize = 13.sp)''',
    "ConversationListScreen.kt: empty-state subtext"
)

print("\n[DONE] Empty chat list text updated to English")
