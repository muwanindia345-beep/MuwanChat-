import sys, os

# Usage (APK repo root se):  python guest_presence_apk_patch.py .
# PEHLE backend ka guest_backend_v5.py deploy hona chahiye. APK mein short-link patch (GUEST_P2D) lagi honi chahiye.
#
# GUEST PRESENCE (APK):
#  * Pichhle patch ka "Offline hide" hata diya -- Chats list aur chat header dono mein authentication jaisa
#    Online (hara) / Offline (laal / gray) wapas.
#  * Guest ke liye asli presence: socket connect hote hi contacts ka online status aata hai, aur koi contact
#    online/offline ho to turant update. Sirf wahi contacts jo dono taraf se judd chuke hain dikhte hain.
#  * Backend ke bina (v5 deploy nahi) sab "Offline" dikhega -- kuch crash nahi hota.
#
# Patch: GuestChat.kt, ChatScreen.kt, ConversationListScreen.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likha jata.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2E"

def rd(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: anchor %d baar mila (1 hona chahiye). Kuch nahi likha." % (name, n))
        sys.exit(1)
    return s.replace(old, new)

writes = {}
skipped = []

def plan(path, label, fn, need):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label)
        return
    if need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle purane guest patches lagao. Kuch nahi likha." % (label, need))
        sys.exit(1)
    writes[path] = fn(s)

# --- GuestChat.kt
PRESENCE_FN = '''    // GUEST_P2E ───────── presence ─────────
    private val _online = MutableStateFlow<Set<String>>(emptySet())
    val onlineUids: StateFlow<Set<String>> = _online

    // Contacts ka online status server se (sirf wahi jo dono taraf se allowed hain). Baad ke changes push se aate hain.
    private suspend fun refreshPresence(app: Context) {
        val sock = socket ?: return
        if (!sock.connected()) return
        val myId = GuestStore.activeGuestId(app)
        if (myId.isEmpty()) return
        val ids = MuwanChatDb.get(app, myId).conversationDao().getAll()
            .map { it.uid }.filter { it.startsWith("g_") }.distinct().take(200)
        if (ids.isEmpty()) return
        sock.emit("presence", JSONArray(ids), Ack { args ->
            val arr = (args.getOrNull(0) as? JSONObject)?.optJSONArray("online")
            if (arr != null) {
                val s = HashSet<String>()
                for (i in 0 until arr.length()) s.add(arr.optString(i))
                _online.value = s
            }
        })
    }

    fun refreshPresenceNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                refreshPresence(app)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

'''
def p_chat_engine(s):
    s = rep(s, 'import kotlinx.coroutines.launch\n',
            'import kotlinx.coroutines.flow.MutableStateFlow\nimport kotlinx.coroutines.flow.StateFlow\nimport kotlinx.coroutines.launch\n',
            "imports")
    s = rep(s,
        '            socket = sock\n            socketToken = token\n            sock.connect()\n',
        '            sock.on(Socket.EVENT_CONNECT) {  // GUEST_P2E\n'
        '                scope.launch {\n'
        '                    try {\n'
        '                        refreshPresence(app)\n'
        '                    } catch (e: CancellationException) {\n'
        '                        throw e\n'
        '                    } catch (_: Exception) {\n'
        '                    }\n'
        '                }\n'
        '            }\n'
        '            sock.on(Socket.EVENT_DISCONNECT) { _online.value = emptySet() }\n'
        '            sock.on("presence") { args ->\n'
        '                val o = args.getOrNull(0) as? JSONObject ?: return@on\n'
        '                val id = o.optString("id")\n'
        '                if (id.isEmpty()) return@on\n'
        '                _online.value = if (o.optBoolean("online")) _online.value + id else _online.value - id\n'
        '            }\n'
        '            socket = sock\n            socketToken = token\n            sock.connect()\n',
        "socket handlers")
    s = rep(s, '    // ───────── receive ─────────\n', PRESENCE_FN + '    // ───────── receive ─────────\n', "fn")
    s = rep(s,
        '                markOpenRead(app, roomId, peerUid)\n            } catch (e: CancellationException) {',
        '                refreshPresence(app)  // GUEST_P2E\n'
        '                markOpenRead(app, roomId, peerUid)\n            } catch (e: CancellationException) {',
        "chat opened")
    return s
plan(os.path.join(BASE, "data/GuestChat.kt"), "GuestChat.kt", p_chat_engine, "GUEST_P2C")

# --- ChatScreen.kt
def p_chat_screen(s):
    s = rep(s, '                showPresence = !isGuest,  // GUEST_P2D\n', '', "show presence arg")
    s = rep(s,
        '    LaunchedEffect(Unit) {\n        if (isGuest) {  // GUEST_P2C: guest ke liye alag raasta (auth token/API nahi)\n',
        '    LaunchedEffect(isGuest, receiverUid) {  // GUEST_P2E: guest contact ka online/offline\n'
        '        if (isGuest) {\n'
        '            com.muwan.muwanchat.data.GuestChat.onlineUids.collect { isReceiverOnline = it.contains(receiverUid) }\n'
        '        }\n'
        '    }\n'
        '\n'
        '    LaunchedEffect(Unit) {\n        if (isGuest) {  // GUEST_P2C: guest ke liye alag raasta (auth token/API nahi)\n',
        "chat presence")
    return s
plan(os.path.join(BASE, "screens/ChatScreen.kt"), "ChatScreen.kt", p_chat_screen, "GUEST_P2D")

# --- ConversationListScreen.kt
def p_conv(s):
    s = rep(s,
        '    val onlineUids by AppSocketManager.onlineUids.collectAsState()\n',
        '    val presenceGuest = remember { com.muwan.muwanchat.data.GuestStore.isGuestActive(context) }  // GUEST_P2E\n'
        '    val onlineUids by (if (presenceGuest) com.muwan.muwanchat.data.GuestChat.onlineUids else AppSocketManager.onlineUids).collectAsState()\n',
        "online uids")
    s = rep(s,
        '                val rowCtx = androidx.compose.ui.platform.LocalContext.current  // GUEST_P2D\n'
        '                val hidePresence = remember {  // guest mein presence nahi\n'
        '                    com.muwan.muwanchat.data.GuestStore.isGuestActive(rowCtx)\n'
        '                }\n'
        '                if (!hidePresence) Text(\n',
        '                Text(\n',
        "unhide")
    return s
plan(os.path.join(BASE, "screens/ConversationListScreen.kt"), "ConversationListScreen.kt", p_conv, "GUEST_P2D")

if not writes:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

for path, content in writes.items():
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK  " + os.path.relpath(path, ROOT))
for x in skipped:
    print("skip (pehle se hai) " + x)
print("\nPresence patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
