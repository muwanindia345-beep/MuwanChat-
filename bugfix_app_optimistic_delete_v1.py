#!/usr/bin/env python3
"""
bugfix_app_optimistic_delete_v1.py -- "Delete for everyone" ab optimistic

Usage (APP repo ke root se):
    python3 bugfix_app_optimistic_delete_v1.py --dry-run
    python3 bugfix_app_optimistic_delete_v1.py

Pehle: har message ke liye server DELETE ka jawab aane TAK ruk-ta tha (ek-ek karke),
uske BAAD screen pe "This message was deleted" dikhta tha  -> slow feel.
Aur server 403 de (jaise "Not your message") to bhi app local pe deleted dikha deta tha.

Ab:
  1. Tap karte hi turant "deleted" dikhta hai (local Room), preview bhi turant update.
  2. Server calls background mein, sab messages parallel.
  3. Fail ho (no internet / server error) to message wapas aa jaata hai + toast.
     (404 = server pe pehle se nahi hai -> success maana jaata hai)
ChatScreen (1-1) aur GroupChatScreen dono mein.
"""
import sys
from pathlib import Path

ROOT = Path.cwd()
BASE = "app/src/main/java/com/muwan/muwanchat/screens"
DRY = "--dry-run" in sys.argv
failures = []
changed = set()


def read(rel):
    p = ROOT / rel
    if not p.exists():
        failures.append(f"{rel}: file nahi mili (script APP repo root se chalao)")
        return None
    return p.read_text(encoding="utf-8")


def write(rel, text):
    changed.add(rel)
    if not DRY:
        (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text, rel, name, old, new):
    if new in text:
        print(f"  [skip] {name} (already applied)")
        return text
    n = text.count(old)
    if n != 1:
        failures.append(f"{rel}: '{name}' anchor {n} baar mila (1 chahiye) -- manually dekho")
        print(f"  [FAIL] {name}")
        return text
    print(f"  [ ok ] {name}")
    return text.replace(old, new)


def add_import(text, rel, line):
    """Import sirf tab jodo jab pehle se na ho (launch import ke theek pehle)."""
    if f"{line}\n" in text:
        print(f"  [skip] {line} (already imported)")
        return text
    anchor = "import kotlinx.coroutines.launch\n"
    if text.count(anchor) != 1:
        failures.append(f"{rel}: launch import anchor nahi mila ({line})")
        print(f"  [FAIL] {line}")
        return text
    print(f"  [ ok ] {line}")
    return text.replace(anchor, f"{line}\n{anchor}")


def new_function(room_var):
    return f'''    fun deleteSelectedForEveryone() {{
        val ids = selectedMessageIds.toList()
        // BG_OPTIMISTIC_DELETE_PATCH: pehle screen pe turant "deleted" dikhao (server ka wait nahi),
        // phir background mein server call. Fail ho to message wapas aa jaata hai + toast.
        scope.launch {{
            val backups = ids.mapNotNull {{ db.messageDao().getById(it) }}
            ids.forEach {{ db.messageDao().markDeleted(it) }}
            ChatRepository.refreshLastMessagePreview(db, {room_var})

            val failedIds = ids.map {{ id ->
                async {{
                    val ok = try {{
                        val res = RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", {room_var}, id)
                        res.isSuccessful || res.code() == 404   // 404 = server pe pehle se nahi hai
                    }} catch (_: Exception) {{
                        false
                    }}
                    if (ok) null else id
                }}
            }}.awaitAll().filterNotNull()

            if (failedIds.isNotEmpty()) {{
                backups.filter {{ it.id in failedIds }}.forEach {{ db.messageDao().insert(it) }}
                ChatRepository.refreshLastMessagePreview(db, {room_var})
                Toast.makeText(context, "Couldn't delete for everyone. Check your connection.", Toast.LENGTH_SHORT).show()
            }}
        }}
        exitSelectionMode()
    }}
'''


# ── ChatScreen.kt ────────────────────────────────────────────────────
CHAT = f"{BASE}/ChatScreen.kt"
print(f"\n== {CHAT} ==")
t = read(CHAT)
if t is not None:
    t = add_import(t, CHAT, "import kotlinx.coroutines.async")
    t = add_import(t, CHAT, "import kotlinx.coroutines.awaitAll")
    OLD_CHAT = '''    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", roomId, id)
                } catch (_: Exception) {
                    // Backend call fail ho jaaye (jaise no internet) to bhi apni screen se hata dete hain;
                    // dusre user tak socket event backend se hi jaayega jab connection wapas aayega.
                }
                db.messageDao().markDeleted(id)
            }
            ChatRepository.refreshLastMessagePreview(db, roomId)
        }
        exitSelectionMode()
    }
'''
    t = replace_once(t, CHAT, "optimistic delete for everyone", OLD_CHAT, new_function("roomId"))
    write(CHAT, t)

# ── GroupChatScreen.kt ───────────────────────────────────────────────
GROUP = f"{BASE}/GroupChatScreen.kt"
print(f"\n== {GROUP} ==")
t = read(GROUP)
if t is not None:
    t = add_import(t, GROUP, "import kotlinx.coroutines.async")
    t = add_import(t, GROUP, "import kotlinx.coroutines.awaitAll")
    OLD_GROUP = '''    fun deleteSelectedForEveryone() {
        val ids = selectedMessageIds.toList()
        scope.launch {
            ids.forEach { id ->
                try {
                    RetrofitClient.chatApi.deleteMsgById("Bearer $myToken", groupId, id)
                } catch (_: Exception) {}
                db.messageDao().markDeleted(id)
            }
            ChatRepository.refreshLastMessagePreview(db, groupId)
        }
        exitSelectionMode()
    }
'''
    t = replace_once(t, GROUP, "optimistic delete for everyone (group)", OLD_GROUP, new_function("groupId"))
    write(GROUP, t)

print("\n" + "=" * 60)
if DRY:
    print("DRY RUN -- kuch likha nahi gaya.")
print(f"Files: {len(changed)}")
for f in sorted(changed):
    print("  -", f)
if failures:
    print("\nFAILED (inhe manually dekho):")
    for f in failures:
        print("  !", f)
    sys.exit(1)
print("\nSab theek. Ab: git diff --stat -> commit -> push")
