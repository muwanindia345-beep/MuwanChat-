#!/usr/bin/env python3
"""
bugfix_app_prefetch_v1.py -- MuwanChat APP: chats ko background mein pehle hi download karo

Usage (APP repo ke root se, jahan app/ folder hai):
    python3 bugfix_app_prefetch_v1.py --dry-run
    python3 bugfix_app_prefetch_v1.py

Problem: offline se online aane par chat list mein unread/preview aa jaata hai,
lekin messages khud tab tak download nahi hote jab tak chat tap na karo.
Tap karte hi network call + purana chat dikhta hai, phir delay se update.

Fix:
  1. ChatRepository.prefetchMessagesInBackground(): chat list sync hote hi, jin chats
     mein unread ya naya message hai (max 15, 3 parallel) unke messages chupke se
     Room mein save. markSeen/clearUnread yahan KABHI nahi hota (sirf chat kholne par).
  2. ConversationListScreen: reloadConversations() ke baad prefetch trigger.
  3. ChatScreen + GroupChatScreen: wallpaper restore ki network call ab messages
     ko block nahi karti (pehle har chat open pe pehle wallpaper call, phir messages).
"""
import sys
from pathlib import Path

ROOT = Path.cwd()
BASE = "app/src/main/java/com/muwan/muwanchat"
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


def replace_once(text, rel, name, old, new, marker=None):
    if (marker and marker in text) or new in text:
        print(f"  [skip] {name} (already applied)")
        return text
    n = text.count(old)
    if n != 1:
        failures.append(f"{rel}: '{name}' anchor {n} baar mila (1 chahiye) -- manually dekho")
        print(f"  [FAIL] {name}")
        return text
    print(f"  [ ok ] {name}")
    return text.replace(old, new)


# ─────────────────────────────────────────────────────────────────────
# 1. ChatRepository.kt
# ─────────────────────────────────────────────────────────────────────
REPO = f"{BASE}/data/ChatRepository.kt"
print(f"\n== {REPO} ==")
t = read(REPO)
if t is not None:
    t = replace_once(
        t, REPO, "imports",
        "import kotlinx.coroutines.sync.Mutex\n",
        "import kotlinx.coroutines.CoroutineScope\n"
        "import kotlinx.coroutines.Dispatchers\n"
        "import kotlinx.coroutines.SupervisorJob\n"
        "import kotlinx.coroutines.async\n"
        "import kotlinx.coroutines.awaitAll\n"
        "import kotlinx.coroutines.coroutineScope\n"
        "import kotlinx.coroutines.launch\n"
        "import kotlinx.coroutines.sync.Mutex\n"
        "import kotlinx.coroutines.sync.Semaphore\n"
        "import kotlinx.coroutines.sync.withPermit\n"
        "import com.muwan.muwanchat.network.RetrofitClient\n"
        "import java.util.concurrent.ConcurrentHashMap\n"
        "import java.util.concurrent.atomic.AtomicBoolean\n",
        marker="BG_PREFETCH_PATCH",
    )

    PREFETCH = '''    // ───────── BG_PREFETCH_PATCH ─────────
    // Chat list sync hote hi, jin chats mein unread / naya message hai unke messages
    // chupke se Room mein download kar lo -- taaki chat tap karte hi sab local ho
    // aur "purana chat dikhta hai, phir delay se update" wali problem na aaye.
    // NOTE: yahan markSeen / clearUnread KABHI nahi hota -- wo sirf chat kholne par hoga.
    private const val PREFETCH_MAX_ROOMS = 15
    private const val PREFETCH_PARALLEL = 3
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetchRunning = AtomicBoolean(false)
    // roomId -> "lastTime|unread" jo ek baar successfully prefetch ho chuka (loop/duplicate se bachne ke liye)
    private val prefetchedState = ConcurrentHashMap<String, String>()

    fun prefetchMessagesInBackground(db: MuwanChatDb, token: String, items: List<ConversationItem>) {
        if (token.isBlank() || items.isEmpty()) return
        if (!prefetchRunning.compareAndSet(false, true)) return
        prefetchScope.launch {
            try {
                val ordered = items
                    .filter { !it.isRemoved && it.lastTime.isNotBlank() }
                    .sortedWith(
                        compareByDescending<ConversationItem> { it.unreadCount > 0 }
                            .thenByDescending { it.lastTime }
                    )
                val targets = ArrayList<ConversationItem>()
                for (item in ordered) {
                    if (targets.size >= PREFETCH_MAX_ROOMS) break
                    val stateKey = "${item.lastTime}|${item.unreadCount}"
                    if (prefetchedState[item.room_id] == stateKey) continue
                    val localLatest = db.messageDao().getLatestMessage(item.room_id)
                    val stale = localLatest == null || localLatest.createdAt < item.lastTime
                    if (item.unreadCount > 0 || stale) targets.add(item)
                }
                if (targets.isEmpty()) return@launch

                val gate = Semaphore(PREFETCH_PARALLEL)
                coroutineScope {
                    targets.map { item ->
                        async { gate.withPermit { prefetchOneRoom(db, token, item) } }
                    }.awaitAll()
                }
            } catch (_: Exception) {
                // best-effort: fail ho to chat kholne par normal fetch chalega hi
            } finally {
                prefetchRunning.set(false)
            }
        }
    }

    private suspend fun prefetchOneRoom(db: MuwanChatDb, token: String, item: ConversationItem) {
        try {
            val res = RetrofitClient.chatApi.getMessages("Bearer $token", item.room_id)
            if (res.isSuccessful) {
                val memberCount = if (item.isGroup && item.memberCount > 0) item.memberCount else null
                syncMessages(db, res.body()?.messages ?: emptyList(), memberCount)
                prefetchedState[item.room_id] = "${item.lastTime}|${item.unreadCount}"
            }
        } catch (_: Exception) {
            // network fail -- agli list sync pe dobara try hoga
        }
    }
    // ───────── /BG_PREFETCH_PATCH ─────────

'''
    t = replace_once(
        t, REPO, "prefetch functions",
        "    // FCM push se aaye message (app background / socket dead) ko Room mein save karne ke liye.\n",
        PREFETCH + "    // FCM push se aaye message (app background / socket dead) ko Room mein save karne ke liye.\n",
        marker="BG_PREFETCH_PATCH ─────────\n    // Chat list sync",
    )
    write(REPO, t)

# ─────────────────────────────────────────────────────────────────────
# 2. ConversationListScreen.kt
# ─────────────────────────────────────────────────────────────────────
LIST = f"{BASE}/screens/ConversationListScreen.kt"
print(f"\n== {LIST} ==")
t = read(LIST)
if t is not None:
    t = replace_once(
        t, LIST, "trigger prefetch after list sync",
        "                ChatRepository.syncConversations(db, serverItems)\n",
        "                ChatRepository.syncConversations(db, serverItems)\n"
        "                // BG_PREFETCH_PATCH: unread/naye chats ke messages background mein local kar lo\n"
        "                ChatRepository.prefetchMessagesInBackground(db, token, serverItems)\n",
        marker="prefetchMessagesInBackground",
    )
    write(LIST, t)

# ─────────────────────────────────────────────────────────────────────
# 3. ChatScreen.kt -- wallpaper restore ab messages ko block nahi karta
# ─────────────────────────────────────────────────────────────────────
CHAT = f"{BASE}/screens/ChatScreen.kt"
print(f"\n== {CHAT} ==")
t = read(CHAT)
if t is not None:
    t = replace_once(
        t, CHAT, "wallpaper restore non-blocking",
        '''        // Wallpaper local na mile (reinstall ke baad) toh backend se apna preset restore karo
        try {
            if (db.chatWallpaperDao().getByRoomId(roomId) == null) {
                val res = RetrofitClient.chatApi.getWallpaper("Bearer $token", roomId)
                res.body()?.wallpaper?.let { wp ->
                    db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, wp.type, wp.value))
                }
            }
        } catch (_: Exception) {}
''',
        '''        // Wallpaper local na mile (reinstall ke baad) toh backend se apna preset restore karo
        // BG_PREFETCH_PATCH: alag coroutine mein -- messages ki fetch ko ab block nahi karta
        launch {
            try {
                if (db.chatWallpaperDao().getByRoomId(roomId) == null) {
                    val wpRes = RetrofitClient.chatApi.getWallpaper("Bearer $token", roomId)
                    wpRes.body()?.wallpaper?.let { wp ->
                        db.chatWallpaperDao().upsert(ChatWallpaperEntity(roomId, wp.type, wp.value))
                    }
                }
            } catch (_: Exception) {}
        }
''',
    )
    write(CHAT, t)

# ─────────────────────────────────────────────────────────────────────
# 4. GroupChatScreen.kt
# ─────────────────────────────────────────────────────────────────────
GROUP = f"{BASE}/screens/GroupChatScreen.kt"
print(f"\n== {GROUP} ==")
t = read(GROUP)
if t is not None:
    t = replace_once(
        t, GROUP, "group wallpaper restore non-blocking",
        '''        try {
            if (db.chatWallpaperDao().getByRoomId(groupId) == null) {
                val res = RetrofitClient.chatApi.getWallpaper("Bearer $token", groupId)
                res.body()?.wallpaper?.let { wp ->
                    db.chatWallpaperDao().upsert(ChatWallpaperEntity(groupId, wp.type, wp.value))
                }
            }
        } catch (_: Exception) {}
''',
        '''        // BG_PREFETCH_PATCH: alag coroutine mein -- messages ki fetch ko ab block nahi karta
        launch {
            try {
                if (db.chatWallpaperDao().getByRoomId(groupId) == null) {
                    val wpRes = RetrofitClient.chatApi.getWallpaper("Bearer $token", groupId)
                    wpRes.body()?.wallpaper?.let { wp ->
                        db.chatWallpaperDao().upsert(ChatWallpaperEntity(groupId, wp.type, wp.value))
                    }
                }
            } catch (_: Exception) {}
        }
''',
    )
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
print("\nSab theek. Ab: git diff  -> commit -> push (GitHub Actions beta APK banayega)")
