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
# ConversationListScreen.kt — "Delete chat?" confirm dialog ka subtitle
# English mein badla, baaki dialog (title, buttons) untouched
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/screens/ConversationListScreen.kt",
'''                    "Yeh sirf tumhare liye delete honge — doosre user ki chat waisi hi rahegi.",''',
'''                    "This will only be deleted for you — the other user's chat will stay as is.",''',
    "ConversationListScreen.kt: delete-chat dialog subtitle"
)

print("\n[DONE] Delete chat dialog subtitle updated to English")
