#!/usr/bin/env python3
# GUEST_LOGOUT -- guest logout (sirf session band; identity + guest DB device par rehte hain)
import io, sys

BASE = "app/src/main/java/com/muwan/muwanchat/"
MARK = "GUEST_LOGOUT"
DRY = "--dry-run" in sys.argv

def rd(p):
    with io.open(BASE + p, "r", encoding="utf-8", newline="") as f:
        return f.read()

def wr(p, s):
    with io.open(BASE + p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

EDITS = []  # (file, anchor, replacement)

# 1) GuestApi: DELETE guest/push-token
EDITS.append(("network/GuestApi.kt",
    "import retrofit2.http.Body\n",
    "import retrofit2.http.Body\nimport retrofit2.http.DELETE  // GUEST_LOGOUT\n"))
EDITS.append(("network/GuestApi.kt",
    "        @Body request: GuestAckRequest\n    ): Response<okhttp3.ResponseBody>\n",
    "        @Body request: GuestAckRequest\n    ): Response<okhttp3.ResponseBody>\n\n"
    "    // GUEST_LOGOUT\n"
    "    @DELETE(\"guest/push-token\")\n"
    "    suspend fun unregisterPush(\n"
    "        @Header(\"Authorization\") auth: String\n"
    "    ): Response<okhttp3.ResponseBody>\n"))

# 2) GuestStore: markLoggedOut / hasLoggedOutGuest
EDITS.append(("data/GuestStore.kt",
    "    fun isGuestActive(context: Context): Boolean = activeGuestId(context).isNotEmpty()\n",
    "    fun isGuestActive(context: Context): Boolean = activeGuestId(context).isNotEmpty()\n\n"
    "    // GUEST_LOGOUT -- sirf session band: token hata, active flag off. Seed/identity, guestId aur guest ka\n"
    "    // Room DB (guestId naam se) ko haath nahi lagta, isliye 'Continue Existing Guest' se wapas aa sakta hai.\n"
    "    fun markLoggedOut(context: Context) {\n"
    "        clearToken(context)\n"
    "        state(context).edit().putBoolean(\"onboarded\", false).commit()\n"
    "    }\n\n"
    "    fun hasLoggedOutGuest(context: Context): Boolean {\n"
    "        val s = state(context)\n"
    "        return !s.getBoolean(\"onboarded\", false) && !s.getString(\"gid\", null).isNullOrEmpty() && hasIdentity(context)\n"
    "    }\n"))

# 3) GuestPush: unregister (best effort)
EDITS.append(("data/GuestPush.kt",
    "    private fun notifId(roomId: String): Int = roomId.hashCode()\n",
    "    // GUEST_LOGOUT -- server se push token hatao (best effort; net na ho to chup-chaap aage badho)\n"
    "    suspend fun unregister(app: Context) {\n"
    "        try {\n"
    "            if (GuestSession.ensure(app) is GuestSession.Result.Ok) {\n"
    "                val t = GuestStore.token(app)\n"
    "                if (t != null) RetrofitClient.guestApi.unregisterPush(\"Bearer \" + t)\n"
    "            }\n"
    "        } catch (e: CancellationException) {\n"
    "            throw e\n"
    "        } catch (_: Exception) {\n"
    "        }\n"
    "        app.getSharedPreferences(\"guest_push\", Context.MODE_PRIVATE).edit().clear().apply()\n"
    "    }\n\n"
    "    private fun notifId(roomId: String): Int = roomId.hashCode()\n"))

# 4) GuestChat: stop() + logged-out guards
EDITS.append(("data/GuestChat.kt",
    "    fun start(context: Context) {\n        val app = context.applicationContext\n",
    "    fun start(context: Context) {\n        val app = context.applicationContext\n"
    "        if (!GuestStore.isGuestActive(app)) return  // GUEST_LOGOUT\n"))
EDITS.append(("data/GuestChat.kt",
    "    private suspend fun connect(app: Context) {\n        connectLock.withLock {\n",
    "    // GUEST_LOGOUT -- socket band, open chat bhula do, online list khali\n"
    "    fun stop() {\n"
    "        try {\n"
    "            socket?.off()\n"
    "            socket?.disconnect()\n"
    "        } catch (_: Exception) {\n"
    "        }\n"
    "        socket = null\n"
    "        socketToken = null\n"
    "        openRoomId = null\n"
    "        openPeerId = null\n"
    "        _online.value = emptySet()\n"
    "    }\n\n"
    "    private suspend fun connect(app: Context) {\n        connectLock.withLock {\n"
    "            if (!GuestStore.isGuestActive(app)) return@withLock  // GUEST_LOGOUT\n"))
EDITS.append(("data/GuestChat.kt",
    "    fun onPushWake(context: Context) {\n        val app = context.applicationContext\n",
    "    fun onPushWake(context: Context) {\n        val app = context.applicationContext\n"
    "        if (!GuestStore.isGuestActive(app)) return  // GUEST_LOGOUT\n"))

# 5) SettingsScreen: Logout row (guest)
EDITS.append(("screens/SettingsScreen.kt",
    "                    if (isGuestSession) { guestLogoutSoon = true; return@clickable }  // GUEST_P2A\n",
    "                    if (isGuestSession) {  // GUEST_LOGOUT\n"
    "                        scope.launch {\n"
    "                            kotlinx.coroutines.withTimeoutOrNull(5000L) {\n"
    "                                com.muwan.muwanchat.data.GuestPush.unregister(context)\n"
    "                            }\n"
    "                            com.muwan.muwanchat.data.GuestChat.stop()\n"
    "                            com.muwan.muwanchat.data.GuestStore.markLoggedOut(context)\n"
    "                            navController.navigate(Screen.Login.route) {\n"
    "                                popUpTo(Screen.ConversationList.route) { inclusive = true }\n"
    "                            }\n"
    "                        }\n"
    "                        return@clickable\n"
    "                    }\n"))

# ---- check (kuch likhne se pehle) ----
files = {}
for fp, a, r in EDITS:
    files.setdefault(fp, rd(fp))
if all(MARK in s for s in files.values()):
    print("already applied"); sys.exit(0)
for fp, a, r in EDITS:
    if MARK in files[fp] and r.split("\n")[0] in files[fp] and MARK in r and r in files[fp]:
        continue
    if files[fp].count(a) != 1:
        print("anchor nahi mila / unique nahi:", fp, repr(a[:60])); print("kuch nahi likha"); sys.exit(1)
if DRY:
    print("dry-run OK, saare anchors mil gaye"); sys.exit(0)
for fp, a, r in EDITS:
    s = files[fp]
    if r in s:
        continue
    files[fp] = s.replace(a, r, 1)
for fp, s in files.items():
    wr(fp, s)
    print("applied:", fp)
