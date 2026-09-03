def apply(path, old, new, label):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    n = src.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label} ({path}): found {n} matches (expected 1)\n"
                          f"       Isse pehle 'patch_group_all_seen_tick_android.py' chala hua hona chahiye.")
    src = src.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"[OK] {label}")

# ---------------------------------------------------------------------------
# ChatRepository.kt — 1-1 chat mein history resync (chat reopen / app resume)
# har baar status ko hardcoded "SENT" pe reset kar deta tha, chahe backend
# `seen: 1` bata raha ho. Isliye ek baar green hua tick agli baar chat
# kholne pe wapas grey ho jaata tha. Ab backend ke `seen` flag ko use karo.
# ---------------------------------------------------------------------------
apply(
    "app/src/main/java/com/muwan/muwanchat/data/ChatRepository.kt",
'''            val computedStatus = if (groupMemberCount != null && groupMemberCount > 0) {
                if ((it.seen_by?.size ?: 1) >= groupMemberCount) "SEEN" else "SENT"
            } else "SENT"''',
'''            // 1-1 chat: backend ka `seen` flag (0/1) already sach bata raha
            // hai ki doosre banda ne dekh liya ya nahi — pehle yahan hardcoded
            // "SENT" tha jo har resync (chat reopen / app resume) pe already
            // SEEN ho chuka tick bhi wapas grey kar deta tha. Ab uska sahi
            // status use hoga.
            val computedStatus = if (groupMemberCount != null && groupMemberCount > 0) {
                if ((it.seen_by?.size ?: 1) >= groupMemberCount) "SEEN" else "SENT"
            } else if (it.seen == 1) "SEEN" else "SENT"''',
    "ChatRepository.kt: 1-1 chat seen-flag aware status on resync"
)

print("\n[DONE] 1-1 chat tick 'green to grey on reopen' bug fixed in ChatRepository.kt")
