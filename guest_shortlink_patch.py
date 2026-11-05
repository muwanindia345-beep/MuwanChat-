import sys, os

# Usage (APK repo root se):  python guest_shortlink_patch.py .
# PEHLE backend ka guest_backend_v4.py deploy hona chahiye, aur APK mein Phase 2C + paste-link patch lage hone chahiye.
#
# GUEST SHORT LINK (APK side):
#  * Invite link / QR ab chhota: https://<backend>/g/<CODE>#<SECRET>   (~90 akshar). QR bhi halka, scan aasan.
#    Server fail/purana ho to apne aap purana lamba link bana deta hai (kuch toot-ta nahi).
#  * Paste / scan / link-tap par app code se invite ka public data server se laati hai (naam, country, public key),
#    key ko guestId se verify karti hai, phir wahi "Add contact?" popup. Purane lambe links bhi chalte hain.
#  * Deep link muwan://invite?c=..&s=.. (landing page se) -> app khulti hai.
#  * "Offline" label guest ke liye hata diya (guest mode mein presence nahi hota) -- chat header aur Chats list dono.
#
# Patch: GuestApi.kt, GuestInvite.kt, GuestInviteInbox.kt, GuestInviteHost.kt, NavGraph.kt, AndroidManifest.xml,
#        ChatHeader.kt, ChatScreen.kt, ConversationListScreen.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likhta.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2D"

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

def plan_patch(path, label, fn, need):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label)
        return
    if need and need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle purane guest patches lagao. Kuch nahi likha." % (label, need))
        sys.exit(1)
    writes[path] = fn(s)

# --- GuestApi.kt
API_DATA = '''// GUEST_P2D -- short invite link (backend guest_backend_v4)
data class GuestLinkRequest(
    val publicKey: String,
    val name: String,
    val country: String,
    val ttlHours: Int,
    val code: String? = null
)

data class GuestLinkResponse(
    val success: Boolean?,
    val code: String?,
    val expiresAt: String?,
    val g: String?,
    val k: String?,
    val n: String?,
    val c: String?,
    val error: String?
)

'''
def p_api(s):
    s = rep(s, 'interface GuestApi {\n', API_DATA + 'interface GuestApi {\n', "api data")
    s = rep(s,
        '        @Body request: GuestRelayRequest\n    ): Response<GuestRelayResponse>\n',
        '        @Body request: GuestRelayRequest\n    ): Response<GuestRelayResponse>\n'
        '\n'
        '    // GUEST_P2D\n'
        '    @POST("guest/link")\n'
        '    suspend fun createLink(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @Body request: GuestLinkRequest\n'
        '    ): Response<GuestLinkResponse>\n'
        '\n'
        '    @GET("guest/link/{code}")\n'
        '    suspend fun getLink(@retrofit2.http.Path("code") code: String): Response<GuestLinkResponse>\n',
        "api methods")
    return s
plan_patch(os.path.join(BASE, "network/GuestApi.kt"), "GuestApi.kt", p_api, "GUEST_P2C")

# --- GuestInvite.kt
SHORT_FN = '''    // GUEST_P2D: chhota link https://<backend>/g/<CODE>#<SECRET>. Server par sirf public data (id, key, naam, country);
    // secret fragment mein rehta hai (server ko nahi jaata). Kuch bhi fail ho to null -> purana lamba link.
    private suspend fun shortLink(
        context: Context, token: String, base: String, secret: String, expiry: Long, name: String, country: String
    ): String? {
        return try {
            val pubB64 = GuestCrypto.b64(GuestStore.identity(context).publicKey)
            val sig = sha256Hex(name.take(40) + "|" + country.take(40) + "|" + pubB64)
            val sp = context.getSharedPreferences("guest_invite_link", Context.MODE_PRIVATE)
            val storedCode = sp.getString("code", null)
            val storedSig = sp.getString("sig", null)
            val storedExp = sp.getLong("exp", 0L)
            val now = System.currentTimeMillis()
            if (storedCode != null && storedSig == sig && storedExp >= expiry - 5 * 60_000L &&
                storedExp - now >= REUSE_MIN_LEFT_MS
            ) {
                return base + "/g/" + storedCode + "#" + secret
            }
            val hours = ((expiry - now + 3_599_999L) / 3_600_000L).coerceIn(1L, 720L).toInt()
            val res = RetrofitClient.guestApi.createLink(
                "Bearer " + token,
                com.muwan.muwanchat.network.GuestLinkRequest(pubB64, name.take(40), country.take(40), hours, storedCode)
            )
            val code = res.body()?.code
            if (!res.isSuccessful || code.isNullOrBlank()) return null
            sp.edit().putString("code", code).putString("sig", sig).putLong("exp", now + hours * 3_600_000L).apply()
            base + "/g/" + code + "#" + secret
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

'''
def p_inv(s):
    s = rep(s,
        '        return Result.Ok(Info(base + "/g#" + payload, expiry))\n',
        '        val shortLink = shortLink(context, token, base, secret, expiry, profile?.name ?: "", profile?.country ?: "")  // GUEST_P2D\n'
        '        return Result.Ok(Info(shortLink ?: (base + "/g#" + payload), expiry))\n',
        "invite return")
    s = rep(s, '    private fun newSecret(): String {\n', SHORT_FN + '    private fun newSecret(): String {\n', "invite fn")
    return s
plan_patch(os.path.join(BASE, "data/GuestInvite.kt"), "GuestInvite.kt", p_inv, "GUEST_P2C")

# --- GuestInviteInbox.kt
RESOLVE_KT = '''    // GUEST_P2D: lamba (payload) ya chhota (/g/CODE#SECRET, muwan://invite?c=&s=) -- dono chalte hain.
    class Resolved(val invite: Invite?, val error: String?)

    private val SHORT_RE = Regex("/g/([A-Za-z0-9]{10})/?#([A-Za-z0-9_-]{16,200})")
    private val CODE_RE = Regex("^[A-Za-z0-9]{10}$")
    private val SECRET_RE = Regex("^[A-Za-z0-9_-]{16,200}$")

    private fun parseShort(raw: String): Pair<String, String>? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 6000) return null
        if (t.startsWith("muwan://invite", ignoreCase = true)) {
            val u = Uri.parse(t)
            val c = u.getQueryParameter("c") ?: return null
            val s = u.getQueryParameter("s") ?: return null
            return if (CODE_RE.matches(c) && SECRET_RE.matches(s)) Pair(c, s) else null
        }
        val m = SHORT_RE.find(t) ?: return null
        return Pair(m.groupValues[1], m.groupValues[2])
    }

    // Chhote link ke liye server se public data laata hai; key ko guestId se verify karta hai (nakli key nahi chalegi).
    suspend fun resolve(raw: String): Resolved = withContext(Dispatchers.IO) {
        val ref = parseShort(raw)
        if (ref == null) return@withContext Resolved(parse(raw), null)
        try {
            val res = RetrofitClient.guestApi.getLink(ref.first)
            val b = res.body()
            if (res.code() == 404) {
                return@withContext Resolved(null, "This invite link is invalid or has expired. Ask your friend for a new one.")
            }
            if (!res.isSuccessful || b == null) {
                return@withContext Resolved(null, "Couldn't check this invite right now. Check your internet and try again.")
            }
            val g = b.g ?: ""
            val pub = try { GuestCrypto.fromB64(b.k ?: "") } catch (_: Exception) { ByteArray(0) }
            if (!GID_RE.matches(g) || pub.size != 32 || GuestCrypto.guestIdOf(pub) != g) {
                return@withContext Resolved(null, "This invite link is invalid.")
            }
            Resolved(Invite(g, ref.second, (b.n ?: "").trim().take(40), (b.c ?: "").trim().take(40), pub), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resolved(null, friendlyErrorMessage(e))
        }
    }

'''
def p_inbox(s):
    return rep(s, '    fun parse(raw: String): Invite? {\n', RESOLVE_KT + '    fun parse(raw: String): Invite? {\n', "inbox resolve")
plan_patch(os.path.join(BASE, "data/GuestInviteInbox.kt"), "GuestInviteInbox.kt", p_inbox, "GUEST_P2C")

# --- GuestInviteHost.kt
def p_host(s):
    s = rep(s,
        '    val invite = remember(raw) { raw?.let { GuestInviteInbox.parse(it) } }\n',
        '    var resolved by remember(raw) { mutableStateOf<GuestInviteInbox.Resolved?>(null) }  // GUEST_P2D\n'
        '    LaunchedEffect(raw) {\n'
        '        val r = raw\n'
        '        resolved = if (r == null) null else GuestInviteInbox.resolve(r)\n'
        '    }\n'
        '    val invite = resolved?.invite\n',
        "host invite")
    s = rep(s,
        '    } else if (invite == null) {\n        AlertDialog(\n',
        '    } else if (resolved == null) {  // GUEST_P2D: invite server se check ho raha hai\n'
        '        AlertDialog(\n'
        '            onDismissRequest = { close() },\n'
        '            containerColor = DarkSheet,\n'
        '            title = { Text("Checking invite…", color = Color.White, fontWeight = FontWeight.Bold) },\n'
        '            text = { Text("Please wait a moment.", color = Color(0xFFCCCCCC), fontSize = 14.sp) },\n'
        '            confirmButton = {},\n'
        '            dismissButton = { TextButton(onClick = { close() }) { Text("Cancel", color = Color(0xFF888888)) } }\n'
        '        )\n'
        '    } else if (invite == null) {\n        AlertDialog(\n',
        "host checking")
    s = rep(s,
        '                    "This QR code or link is not a valid TalkWave guest invite.",\n',
        '                    resolved?.error ?: "This QR code or link is not a valid TalkWave guest invite.",\n',
        "host error")
    return s
plan_patch(os.path.join(BASE, "screens/GuestInviteHost.kt"), "GuestInviteHost.kt", p_host, "GUEST_P2B2")

# --- NavGraph.kt
def p_nav(s):
    s = rep(s,
        '    object GuestJoin       : Screen("guest_join?p={p}")  // GUEST_P2B2\n',
        '    object GuestJoin       : Screen("guest_join?p={p}")  // GUEST_P2B2\n'
        '    object GuestJoinShort  : Screen("guest_join_short?c={c}&s={s}")  // GUEST_P2D\n',
        "nav object")
    s = rep(s,
        '            GuestJoinRoute(navController, back.arguments?.getString("p"))\n        }\n',
        '            GuestJoinRoute(navController, back.arguments?.getString("p"))\n        }\n'
        '        composable(  // GUEST_P2D: chhota link deep link  muwan://invite?c=CODE&s=SECRET\n'
        '            Screen.GuestJoinShort.route,\n'
        '            arguments = listOf(\n'
        '                navArgument("c") {\n'
        '                    type = androidx.navigation.NavType.StringType\n'
        '                    nullable = true\n'
        '                    defaultValue = null\n'
        '                },\n'
        '                navArgument("s") {\n'
        '                    type = androidx.navigation.NavType.StringType\n'
        '                    nullable = true\n'
        '                    defaultValue = null\n'
        '                }\n'
        '            ),\n'
        '            deepLinks = listOf(navDeepLink { uriPattern = "muwan://invite?c={c}&s={s}" })\n'
        '        ) { back ->\n'
        '            val c = back.arguments?.getString("c")\n'
        '            val sec = back.arguments?.getString("s")\n'
        '            GuestJoinRoute(navController, if (c != null && sec != null) "muwan://invite?c=" + c + "&s=" + sec else null)\n'
        '        }\n',
        "nav composable")
    return s
plan_patch(os.path.join(BASE, "navigation/NavGraph.kt"), "NavGraph.kt", p_nav, "GUEST_P2B2")

# --- AndroidManifest.xml
def p_manifest(s):
    return rep(s,
        '                <data android:scheme="muwan" android:host="guest" />\n            </intent-filter>\n',
        '                <data android:scheme="muwan" android:host="guest" />\n            </intent-filter>\n'
        '\n'
        '            <!-- GUEST_P2D: chhota invite link deep link: muwan://invite?c={code}&s={secret} -->\n'
        '            <intent-filter>\n'
        '                <action android:name="android.intent.action.VIEW" />\n'
        '                <category android:name="android.intent.category.DEFAULT" />\n'
        '                <category android:name="android.intent.category.BROWSABLE" />\n'
        '                <data android:scheme="muwan" android:host="invite" />\n'
        '            </intent-filter>\n',
        "manifest")
plan_patch(os.path.join(ROOT, "app/src/main/AndroidManifest.xml"), "AndroidManifest.xml", p_manifest, "GUEST_P2B2")

# --- "Offline" label guest ke liye hatao
def p_header(s):
    s = rep(s, '    isOnline: Boolean,\n', '    isOnline: Boolean,\n    showPresence: Boolean = true,  // GUEST_P2D: guest mode mein online/offline nahi dikhate\n', "header param")
    s = rep(s,
        '                Text(\n                    statusText,\n                    color = statusColor,\n',
        '                if (showPresence || isTyping) Text(\n                    statusText,\n                    color = statusColor,\n',
        "header text")
    return s
plan_patch(os.path.join(BASE, "screens/ChatHeader.kt"), "ChatHeader.kt", p_header, None)

def p_chat(s):
    return rep(s, '                isOnline = isReceiverOnline,\n',
               '                isOnline = isReceiverOnline,\n                showPresence = !isGuest,  // GUEST_P2D\n', "chat header arg")
plan_patch(os.path.join(BASE, "screens/ChatScreen.kt"), "ChatScreen.kt", p_chat, "GUEST_P2C")

def p_conv(s):
    return rep(s,
        '            } else {\n                Text(\n                    if (conv.isOnline) "Online" else "Offline",\n',
        '            } else {\n'
        '                val rowCtx = androidx.compose.ui.platform.LocalContext.current  // GUEST_P2D\n'
        '                val hidePresence = remember {  // guest mein presence nahi\n'
        '                    com.muwan.muwanchat.data.GuestStore.isGuestActive(rowCtx)\n'
        '                }\n'
        '                if (!hidePresence) Text(\n                    if (conv.isOnline) "Online" else "Offline",\n',
        "conv offline")
plan_patch(os.path.join(BASE, "screens/ConversationListScreen.kt"), "ConversationListScreen.kt", p_conv, "GUEST_P2C")

if not writes:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

for path, content in writes.items():
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK  " + os.path.relpath(path, ROOT))
for x in skipped:
    print("skip (pehle se hai) " + x)
print("\nShort link patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
