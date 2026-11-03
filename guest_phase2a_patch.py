import sys, os

# Usage (Termux, repo root MuwanChat se):
#   python guest_phase2a_patch.py .
#
# GUEST PHASE 2A -- guest ab home screen tak pahunchta hai, UI wahi (same bottom nav, same screens).
# PEHLE guest_phase1_patch.py lag chuka hona chahiye.
#
#  1. Profile (guest) Save ab asli: backend handshake -> profile local Room mein -> guest "onboarded"
#     -> Chats home. Handshake fail ho to error dikhta hai (Coming Soon nahi).
#  2. Splash: guest onboarded ho to seedha Chats home.
#  3. Guest ki apni Room DB (guestId naam se) -- AuthDataStore.getUidBlocking() guest mein guestId deta hai,
#     isliye BottomNav / Broadcast / Calls / Status sab apne aap guest DB use karte hain.
#  4. FAB sheet: auth ko "New Chat" + "Create Group"; guest ko "Invite People" + "Create Group" (dono Coming Soon).
#  4b. Guest ke paas auth token nahi, to Chats ka loading spinner band kar diya (warna hamesha ghumta).
#  5. Jo features abhi backend se nahi jude, guest ke liye mojooda Coming Soon popup dikhate hain:
#     New Chat search, Requests, Create Group/Channel, Add from contacts, New Status, Account Settings,
#     Accepted Users, Profile edit, Logout.
#  Auth users ke liye kuch nahi badla (har jagah "guest active" check hai).
#
# Naya file : screens/GuestGate.kt
# Patch     : data/GuestStore.kt, data/AuthDataStore.kt, screens/ProfileScreen.kt, screens/SplashScreen.kt,
#             screens/ConversationListScreen.kt, screens/SettingsScreen.kt, navigation/NavGraph.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likhta.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2A"

GATE_KT = r'''package com.muwan.muwanchat.screens

// GUEST_P2A
// Guest ke liye abhi backend se na jude screens: guest ho to Coming Soon popup (dismiss par wapas),
// warna asli screen. Auth users par koi asar nahi.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.data.GuestStore

@Composable
fun GuestGate(navController: NavController, feature: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isGuest = remember { GuestStore.isGuestActive(context) }
    if (isGuest) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            ComingSoonDialog(feature = feature, onDismiss = { navController.popBackStack() })
        }
    } else {
        content()
    }
}
'''

STORE_ADD = r'''    fun clearToken(context: Context) {
        prefs(context).edit().remove(TOKEN_KEY).apply()
    }

    // ───── GUEST_P2A: "guest mode active" ─────
    // Plain prefs (Keystore nahi): guestId public key ka hash hai (secret nahi) aur ye flag
    // AuthDataStore.getUidBlocking() jaise hot paths se padha jaata hai.
    private const val STATE_PREFS = "guest_state"

    private fun state(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    fun markOnboarded(context: Context, guestId: String) {
        state(context).edit().putBoolean("onboarded", true).putString("gid", guestId).commit()
    }

    // Guest active = onboarded AUR koi auth login nahi. Auth login ho to auth hi jeetta hai.
    // Active nahi to "" (isi se AuthDataStore.getUidBlocking() purani tarah kaam karta hai).
    fun activeGuestId(context: Context): String {
        val s = state(context)
        if (!s.getBoolean("onboarded", false)) return ""
        if (AuthDataStore.getTokenBlocking(context).isNotEmpty()) return ""
        return s.getString("gid", null) ?: ""
    }

    fun isGuestActive(context: Context): Boolean = activeGuestId(context).isNotEmpty()
'''

PROFILE_OLD = r'''        if (isGuest) {  // GUEST_P1
            // Phase 1: UI Coming Soon hi rehta hai; peeche silent backend handshake (key + session).
            showGuestComingSoon = true
            scope.launch {
                val r = com.muwan.muwanchat.data.GuestSession.ensure(context)
                val isBeta = com.muwan.muwanchat.BuildConfig.APPLICATION_ID.endsWith(".beta")
                if (isBeta) {
                    android.util.Log.i("GuestSession", r.toString())
                    if (GUEST_P1_SHOW_BETA_RESULT) {
                        guestBetaResult = when (r) {
                            is com.muwan.muwanchat.data.GuestSession.Result.Ok ->
                                "\n\n[beta] backend connected\n" + r.guestId
                            is com.muwan.muwanchat.data.GuestSession.Result.Fail ->
                                "\n\n[beta] backend FAILED\n" + r.reason
                        }
                    }
                }
            }
            return
        }
'''

PROFILE_NEW = r'''        if (isGuest) {  // GUEST_P2A
            // Guest Save: backend handshake (key + session) -> profile apni guest Room DB mein -> home.
            // Network na ho / server na mile to guest nahi banta, error dikhta hai (dobara try kar sakte hain).
            scope.launch {
                isSaving = true
                errorMsg = ""
                try {
                    val r = com.muwan.muwanchat.data.GuestSession.ensure(context)
                    when (r) {
                        is com.muwan.muwanchat.data.GuestSession.Result.Ok -> {
                            val guestGender = if (gender == "Custom") customGender.trim() else gender
                            val guestDb = MuwanChatDb.get(context, r.guestId)
                            guestDb.myProfileDao().upsert(
                                MyProfileEntity(
                                    name = name.trim(), bio = bio.trim().ifBlank { null },
                                    city = city.trim().ifBlank { null }, country = country.trim().ifBlank { null },
                                    gender = guestGender.ifBlank { null }, avatar = avatarBase64
                                )
                            )
                            com.muwan.muwanchat.data.GuestStore.markOnboarded(context, r.guestId)
                            navController.navigate(Screen.ConversationList.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                        is com.muwan.muwanchat.data.GuestSession.Result.Fail -> {
                            errorMsg = "Couldn't connect to server. Check your internet and try again. (" + r.reason + ")"
                        }
                    }
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Something went wrong, try again"
                }
                isSaving = false
            }
            return
        }
'''

# ───────────────────────── helpers ─────────────────────────

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

def plan_patch(rel, fn, need=None):
    path = os.path.join(BASE, rel)
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao, aur pehle Phase 1 patch lagao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(rel)
        return
    if need and need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle guest_phase1_patch.py lagao. Kuch nahi likha." % (rel, need))
        sys.exit(1)
    writes[path] = fn(s)

# --- naya file
gate_path = os.path.join(BASE, "screens/GuestGate.kt")
if os.path.exists(gate_path):
    if MARK in rd(gate_path):
        skipped.append("screens/GuestGate.kt")
    else:
        print("FAIL: GuestGate.kt pehle se hai par GUEST_P2A wala nahi. Kuch nahi likha.")
        sys.exit(1)
else:
    writes[gate_path] = GATE_KT

# --- GuestStore.kt
def p_store(s):
    return rep(s,
        '    fun clearToken(context: Context) {\n        prefs(context).edit().remove(TOKEN_KEY).apply()\n    }\n',
        STORE_ADD, "GuestStore")
plan_patch("data/GuestStore.kt", p_store, need="GUEST_P1")

# --- AuthDataStore.kt : guest mode mein uid = guestId
def p_auth(s):
    return rep(s,
        '    fun getUidBlocking(context: Context): String =\n        prefs(context).getString(UID_KEY, null) ?: ""\n',
        '    fun getUidBlocking(context: Context): String {\n'
        '        val uid = prefs(context).getString(UID_KEY, null) ?: ""\n'
        '        if (uid.isNotEmpty()) return uid\n'
        '        // GUEST_P2A: guest mode mein "uid" = guestId (isi se Room DB guest ki alag banti hai)\n'
        '        return GuestStore.activeGuestId(context)\n'
        '    }\n',
        "AuthDataStore")
plan_patch("data/AuthDataStore.kt", p_auth)

# --- ProfileScreen.kt : guest Save asli
def p_profile(s):
    return rep(s, PROFILE_OLD, PROFILE_NEW, "ProfileScreen guest branch")
plan_patch("screens/ProfileScreen.kt", p_profile, need="GUEST_P1")

# --- SplashScreen.kt : guest onboarded -> home
def p_splash(s):
    return rep(s,
        '        } else {\n'
        '            navController.navigate(Screen.Login.route) {\n'
        '                popUpTo(Screen.Splash.route) { inclusive = true }\n'
        '            }\n'
        '        }\n',
        '        } else if (com.muwan.muwanchat.data.GuestStore.isGuestActive(context)) {  // GUEST_P2A\n'
        '            navController.navigate(Screen.ConversationList.route) {\n'
        '                popUpTo(Screen.Splash.route) { inclusive = true }\n'
        '            }\n'
        '        } else {\n'
        '            navController.navigate(Screen.Login.route) {\n'
        '                popUpTo(Screen.Splash.route) { inclusive = true }\n'
        '            }\n'
        '        }\n',
        "SplashScreen")
plan_patch("screens/SplashScreen.kt", p_splash)

# --- ConversationListScreen.kt : FAB sheet
def p_conv(s):
    s = rep(s,
        '    var comingSoonFeature by remember { mutableStateOf<String?>(null) }\n',
        '    var comingSoonFeature by remember { mutableStateOf<String?>(null) }\n'
        '    val isGuest = remember { com.muwan.muwanchat.data.GuestStore.isGuestActive(context) }  // GUEST_P2A\n',
        "conv isGuest")
    s = rep(s,
        '        val token = AuthDataStore.getToken(context).first() ?: return@LaunchedEffect\n'
        '        myToken = token\n',
        '        val token = AuthDataStore.getToken(context).first() ?: run {  // GUEST_P2A\n'
        '            // guest ke paas auth token nahi: server sync nahi hota, isliye loading spinner yahin band\n'
        '            isLoading = false\n'
        '            return@LaunchedEffect\n'
        '        }\n'
        '        myToken = token\n',
        "conv guest spinner")
    s = rep(s,
        '                FabSheetOption(Icons.Filled.Add, "New Chat") {\n'
        '                    showFabSheet = false\n'
        '                    navController.navigate(Screen.UserSearch.route)\n'
        '                }\n'
        '                FabSheetOption(Icons.Filled.Add, "Create Group") {\n'
        '                    showFabSheet = false\n'
        '                    navController.navigate(Screen.CreateGroup.route)\n'
        '                }\n',
        '                if (isGuest) {  // GUEST_P2A: guest ko New Chat nahi, Invite People (abhi Coming Soon)\n'
        '                    FabSheetOption(Icons.Filled.Add, "Invite People") {\n'
        '                        showFabSheet = false\n'
        '                        comingSoonFeature = "👥 Invite People"\n'
        '                    }\n'
        '                    FabSheetOption(Icons.Filled.Add, "Create Group") {\n'
        '                        showFabSheet = false\n'
        '                        comingSoonFeature = "👥 Create Group"\n'
        '                    }\n'
        '                } else {\n'
        '                    FabSheetOption(Icons.Filled.Add, "New Chat") {\n'
        '                        showFabSheet = false\n'
        '                        navController.navigate(Screen.UserSearch.route)\n'
        '                    }\n'
        '                    FabSheetOption(Icons.Filled.Add, "Create Group") {\n'
        '                        showFabSheet = false\n'
        '                        navController.navigate(Screen.CreateGroup.route)\n'
        '                    }\n'
        '                }\n',
        "conv fab sheet")
    return s
plan_patch("screens/ConversationListScreen.kt", p_conv)

# --- SettingsScreen.kt : guest Logout -> Coming Soon
def p_settings(s):
    s = rep(s,
        '    var notificationsEnabled by remember { mutableStateOf(true) }\n',
        '    var notificationsEnabled by remember { mutableStateOf(true) }\n'
        '    val isGuestSession = remember { com.muwan.muwanchat.data.GuestStore.isGuestActive(context) }  // GUEST_P2A\n'
        '    var guestLogoutSoon by remember { mutableStateOf(false) }  // GUEST_P2A\n'
        '    if (guestLogoutSoon) {  // GUEST_P2A\n'
        '        ComingSoonDialog(feature = "🚪 Logout / Delete Guest Data", onDismiss = { guestLogoutSoon = false })\n'
        '    }\n',
        "settings state")
    s = rep(s,
        '                .clickable {\n'
        '                    scope.launch {\n'
        '                        AppSocketManager.disconnect()\n',
        '                .clickable {\n'
        '                    if (isGuestSession) { guestLogoutSoon = true; return@clickable }  // GUEST_P2A\n'
        '                    scope.launch {\n'
        '                        AppSocketManager.disconnect()\n',
        "settings logout")
    return s
plan_patch("screens/SettingsScreen.kt", p_settings)

# --- NavGraph.kt : abhi na jude screens guest ke liye Coming Soon
def p_nav(s):
    simple = [
        ("Screen.NewStatus.route", "NewStatusScreen(navController)", "  // STATUS_V1", "📸 New Status"),
        ("Screen.UserSearch.route", "UserSearchScreen(navController)", "", "🔍 New Chat"),
        ("Screen.Requests.route", "RequestsScreen(navController)", "", "🔔 Requests"),
        ("Screen.CreateGroup.route", "CreateGroupScreen(navController)", "", "👥 Create Group"),
        ("Screen.CreateChannel.route", "CreateChannelScreen(navController)", "", "📢 Create Channel"),
        ("Screen.AddFromContacts.route", "AddFromContactsScreen(navController)", "", "📇 Add from Contacts"),
        ("Screen.AccountSettings.route", "AccountSettingsScreen(navController)", "", "⚙️ Account Settings"),
        ("Screen.AcceptedUsers.route", "AcceptedUsersScreen(navController)", "", "👥 Accepted Users"),
    ]
    for route, call, tail, title in simple:
        old = '        composable(%s) { %s }%s\n' % (route, call, tail)
        new = ('        composable(%s) { GuestGate(navController, "%s") { %s } }%s  // GUEST_P2A\n'
               % (route, title, call, tail))
        s = rep(s, old, new, "nav " + route)
    s = rep(s,
        '        composable(Screen.Profile.route) { back ->\n'
        '            ProfileScreen(navController, back.arguments?.getString("mode") ?: "edit")\n'
        '        }\n',
        '        composable(Screen.Profile.route) { back ->\n'
        '            val profileMode = back.arguments?.getString("mode") ?: "edit"  // GUEST_P2A\n'
        '            GuestGate(navController, "👤 Edit Profile") { ProfileScreen(navController, profileMode) }\n'
        '        }\n',
        "nav Profile")
    return s
plan_patch("navigation/NavGraph.kt", p_nav)

# ───────────────────────── likho ─────────────────────────

if not writes:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

for path, content in writes.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK  " + os.path.relpath(path, ROOT))
for x in skipped:
    print("skip (pehle se hai) " + x)
print("\nGuest Phase 2A patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
