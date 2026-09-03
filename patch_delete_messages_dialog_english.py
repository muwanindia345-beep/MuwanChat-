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
# Delete-messages confirm dialog subtitle English mein badla, dono jagah
# (1-1 chat + group chat), title/buttons untouched
# ---------------------------------------------------------------------------
for path in [
    "app/src/main/java/com/muwan/muwanchat/screens/ChatScreen.kt",
    "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt",
]:
    apply(
        path,
'''                        "'Delete for Everyone' sabki screen se hatayega, 'Delete for Me' sirf aapki screen se."''',
'''                        "'Delete for Everyone' will remove it from everyone's screen, 'Delete for Me' will only remove it from yours."''',
        f"{path}: delete-messages dialog subtitle"
    )

print("\n[DONE] Delete messages dialog subtitle updated to English in both chat + group chat")
