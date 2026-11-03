import sys, os

# Usage (Termux, repo root MuwanChat se):
#   python guest_phase2b2_patch.py .
# PEHLE guest_phase1_patch.py, guest_phase2a_patch.py aur guest_phase2b1_patch.py lage hone chahiye.
#
# GUEST PHASE 2B-2 -- invite ko kaam karwana: scan, link kholna, confirm popup, contact add.
#  * Invite People screen ka "Scan a QR code" ab asli (Google code scanner -- camera permission nahi chahiye).
#  * Link/QR (https://<backend>/g#...  ya  muwan://guest?p=...) kholne par app khulti hai (manifest + deep link).
#  * Scan/link ke baad Chats home par popup: "Add contact? <naam> (<country>)  [Add] [Cancel]".
#  * Add -> POST /guest/invite/redeem (dono taraf allow) -> contact Chats list mein aa jata hai.
#  * Galat/purana/apna invite -> saaf message. Pehle se contact ho to "already in your chats".
#  * Invite screen ki "about N day(s)" ab upar round hoti hai (7 din = "7").
#  Sirf guest ke liye. Auth users ko invite link par "guest mode only" toast milta hai.
#
# Dhyan: jisne invite banaya (tu) uske phone mein contact tab dikhega jab dusra pehla message bhejega
# (Phase 2C). Abhi sirf scan karne wale ke phone mein contact judta hai.
#
# Naye files : data/GuestInviteInbox.kt, screens/GuestInviteHost.kt
# Patch      : app/build.gradle.kts, AndroidManifest.xml, network/GuestApi.kt, navigation/NavGraph.kt,
#              screens/InvitePeopleScreen.kt, screens/ConversationListScreen.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likhta.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2B2"

INBOX_KT = r'''package com.muwan.muwanchat.data

// GUEST_P2B2
// Guest invite ka "inbox": scan ya deep link se aaya raw invite yahan rakha jaata hai (memory mein), Chats home
// ka GuestInviteHost use confirm popup dikhata hai. Parse + redeem (contact add) bhi yahin.
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.muwan.muwanchat.network.GuestRedeemRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

object GuestInviteInbox {

    class Invite(val guestId: String, val secret: String, val name: String, val country: String)

    val pending = MutableStateFlow<String?>(null)

    fun offer(raw: String) {
        pending.value = raw.take(6000)
    }

    fun clear() {
        pending.value = null
    }

    private val GID_RE = Regex("^g_[0-9a-f]{32}$")
    private val PAYLOAD_RE = Regex("^[A-Za-z0-9_-]{16,4000}$")

    // Accepts: https://<backend>/g#<payload> | muwan://guest?p=<payload> | sirf <payload>
    private fun extractPayload(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 6000) return null
        val fragment = t.substringAfter('#', "")
        val candidate: String? = when {
            t.startsWith("muwan://", ignoreCase = true) -> Uri.parse(t).getQueryParameter("p")
            fragment.isNotEmpty() -> fragment
            else -> t
        }
        return candidate?.takeIf { PAYLOAD_RE.matches(it) }
    }

    fun parse(raw: String): Invite? {
        val payload = extractPayload(raw) ?: return null
        return try {
            val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8))
            val guestId = json.optString("g")
            val secret = json.optString("s")
            if (json.optInt("v", 0) != 1) null
            else if (!GID_RE.matches(guestId)) null
            else if (secret.length < 16 || secret.length > 200) null
            else Invite(
                guestId,
                secret,
                json.optString("n").trim().take(40),
                json.optString("c").trim().take(40)
            )
        } catch (_: Exception) {
            null
        }
    }

    // Returns (ok, message). Secret sirf hamare apne backend ko jaata hai (host link se nahi liya jaata).
    suspend fun accept(context: Context, inv: Invite): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext
            val session = GuestSession.ensure(app)
            val myId = when (session) {
                is GuestSession.Result.Ok -> session.guestId
                is GuestSession.Result.Fail -> return@withContext Pair(false, session.reason)
            }
            if (inv.guestId == myId) return@withContext Pair(false, "This is your own invite.")
            val token = GuestStore.token(app) ?: return@withContext Pair(false, "Guest session missing.")

            val db = MuwanChatDb.get(app, myId)
            val shown = inv.name.ifBlank { "Guest " + inv.guestId.takeLast(4) }
            if (db.conversationDao().getByUid(inv.guestId) != null) {
                return@withContext Pair(true, shown + " is already in your chats.")
            }

            val res = RetrofitClient.guestApi.redeemInvite(
                "Bearer " + token,
                GuestRedeemRequest(inv.guestId, inv.secret)
            )
            if (!res.isSuccessful) {
                return@withContext if (res.code() == 404) {
                    Pair(false, "This invite is invalid, expired or already used up. Ask your friend for a new one.")
                } else {
                    Pair(false, "Couldn't add contact (" + errorOf(res) + ")")
                }
            }

            ChatRepository.addConversationPlaceholder(
                db = db,
                roomId = roomIdFor(myId, inv.guestId),
                uid = inv.guestId,
                username = shown,
                avatar = null
            )
            Pair(true, shown + " added to your chats.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Pair(false, friendlyErrorMessage(e))
        }
    }

    // Dono taraf same roomId (sorted pair ka hash) -- chat (Phase 2C) isi se milegi.
    fun roomIdFor(a: String, b: String): String {
        val pair = if (a < b) a + ":" + b else b + ":" + a
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(pair.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder("gdm_")
        for (x in h.take(12)) sb.append("%02x".format(x.toInt() and 0xff))
        return sb.toString()
    }

    private fun errorOf(res: Response<*>): String {
        val raw = try { res.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { JSONObject(raw ?: "").optString("error") } catch (_: Exception) { "" }
        return if (msg.isNotEmpty()) "HTTP " + res.code() + ": " + msg else "HTTP " + res.code()
    }
}
'''

HOST_KT = r'''package com.muwan.muwanchat.screens

// GUEST_P2B2
// 1) GuestInviteHost: Chats home par invite ka confirm popup (Add / Cancel) + natija.
// 2) GuestJoinRoute: muwan://guest?p=... deep link ka darwaza -- invite inbox mein daal ke home le jata hai.
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.GuestInviteInbox
import com.muwan.muwanchat.data.GuestStore
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun GuestInviteHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val raw by GuestInviteInbox.pending.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val invite = remember(raw) { raw?.let { GuestInviteInbox.parse(it) } }

    val current = raw
    if (current == null) return

    fun close() {
        outcome = null
        busy = false
        GuestInviteInbox.clear()
    }

    val result = outcome
    if (result != null) {
        AlertDialog(
            onDismissRequest = { close() },
            containerColor = DarkSheet,
            title = {
                Text(
                    if (result.first) "Done" else "Couldn't add contact",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(result.second, color = Color(0xFFCCCCCC), fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { close() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("OK", color = Color.White) }
            }
        )
    } else if (invite == null) {
        AlertDialog(
            onDismissRequest = { close() },
            containerColor = DarkSheet,
            title = { Text("Invalid invite", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This QR code or link is not a valid TalkWave guest invite.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { close() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("OK", color = Color.White) }
            }
        )
    } else {
        val shown = invite.name.ifBlank { "Guest " + invite.guestId.takeLast(4) }
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            containerColor = DarkSheet,
            title = { Text("Add contact?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(shown, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (invite.country.isNotBlank()) {
                        Text(invite.country, color = Color(0xFF888888), fontSize = 13.sp)
                    }
                    Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    Text(
                        "Add this person to your chats? They will be able to message you.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                    Spacer(androidx.compose.ui.Modifier.height(8.dp))
                    Text(invite.guestId.take(10) + "…" + invite.guestId.takeLast(6), color = Color(0xFF666688), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            outcome = GuestInviteInbox.accept(context, invite)
                            busy = false
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (busy) "Adding…" else "Add", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { close() }, enabled = !busy) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
    }
}

@Composable
fun GuestJoinRoute(navController: NavController, payload: String?) {
    val context = LocalContext.current
    LaunchedEffect(payload) {
        val isAuth = AuthDataStore.getTokenBlocking(context).isNotEmpty()
        val isGuest = GuestStore.isGuestActive(context)
        val target: String
        if (isAuth) {
            Toast.makeText(context, "Invite links work in guest mode only.", Toast.LENGTH_LONG).show()
            target = Screen.ConversationList.route
        } else if (isGuest) {
            if (payload != null) GuestInviteInbox.offer(payload)
            target = Screen.ConversationList.route
        } else {
            // Guest profile abhi bana nahi: invite yaad rakho, profile ke baad home par popup aayega
            if (payload != null) GuestInviteInbox.offer(payload)
            Toast.makeText(context, "Create your guest profile to accept this invite.", Toast.LENGTH_LONG).show()
            target = Screen.Login.route
        }
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}
'''

API_DATA = r'''data class GuestRedeemRequest(
    val to: String,
    val secret: String
)

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

def plan_new_file(rel, content):
    p = os.path.join(BASE, rel)
    if os.path.exists(p):
        if MARK in rd(p):
            skipped.append(rel)
            return
        print("FAIL: %s pehle se hai par GUEST_P2B2 wala nahi. Kuch nahi likha." % rel)
        sys.exit(1)
    writes[p] = content

def plan_patch(path, label, fn, need="GUEST_P2B1"):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao; pehle Phase 1, 2A, 2B-1 lagao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label)
        return
    if need and need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle guest_phase2b1_patch.py lagao. Kuch nahi likha." % (label, need))
        sys.exit(1)
    writes[path] = fn(s)

plan_new_file("data/GuestInviteInbox.kt", INBOX_KT)
plan_new_file("screens/GuestInviteHost.kt", HOST_KT)

# --- build.gradle.kts : Google code scanner
def p_gradle(s):
    return rep(s,
        '    implementation("com.google.zxing:core:3.5.3")\n',
        '    implementation("com.google.zxing:core:3.5.3")\n'
        '    // GUEST_P2B2 -- QR scan (Google Play services ka scanner: camera permission nahi chahiye)\n'
        '    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")\n',
        "gradle scanner")
plan_patch(os.path.join(ROOT, "app/build.gradle.kts"), "build.gradle.kts", p_gradle)

# --- AndroidManifest.xml : muwan://guest deep link
def p_manifest(s):
    return rep(s,
        '                <data android:scheme="muwanchat" android:host="join" />\n'
        '            </intent-filter>\n',
        '                <data android:scheme="muwanchat" android:host="join" />\n'
        '            </intent-filter>\n'
        '\n'
        '            <!-- GUEST_P2B2: guest invite deep link: muwan://guest?p={payload} (landing page /g isi par bhejta hai) -->\n'
        '            <intent-filter>\n'
        '                <action android:name="android.intent.action.VIEW" />\n'
        '                <category android:name="android.intent.category.DEFAULT" />\n'
        '                <category android:name="android.intent.category.BROWSABLE" />\n'
        '                <data android:scheme="muwan" android:host="guest" />\n'
        '            </intent-filter>\n',
        "manifest")
plan_patch(os.path.join(ROOT, "app/src/main/AndroidManifest.xml"), "AndroidManifest.xml", p_manifest, need=None)

# --- GuestApi.kt : redeem
def p_api(s):
    s = rep(s, 'interface GuestApi {\n', API_DATA + 'interface GuestApi {\n', "api data")
    s = rep(s,
        '    ): Response<GuestInviteResponse>\n',
        '    ): Response<GuestInviteResponse>\n'
        '\n'
        '    // GUEST_P2B2: invite redeem -- success par dono taraf allow ban jaata hai\n'
        '    @POST("guest/invite/redeem")\n'
        '    suspend fun redeemInvite(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @Body request: GuestRedeemRequest\n'
        '    ): Response<GuestInviteResponse>\n',
        "api method")
    return s
plan_patch(os.path.join(BASE, "network/GuestApi.kt"), "GuestApi.kt", p_api)

# --- NavGraph.kt : deep link route
def p_nav(s):
    s = rep(s,
        '    object GuestInvite     : Screen("guest_invite")  // GUEST_P2B1\n',
        '    object GuestInvite     : Screen("guest_invite")  // GUEST_P2B1\n'
        '    object GuestJoin       : Screen("guest_join?p={p}")  // GUEST_P2B2\n',
        "nav screen object")
    s = rep(s,
        '        composable(Screen.GuestInvite.route) { InvitePeopleScreen(navController) }  // GUEST_P2B1\n',
        '        composable(Screen.GuestInvite.route) { InvitePeopleScreen(navController) }  // GUEST_P2B1\n'
        '        composable(  // GUEST_P2B2\n'
        '            Screen.GuestJoin.route,\n'
        '            arguments = listOf(navArgument("p") {\n'
        '                type = androidx.navigation.NavType.StringType\n'
        '                nullable = true\n'
        '                defaultValue = null\n'
        '            }),\n'
        '            deepLinks = listOf(navDeepLink { uriPattern = "muwan://guest?p={p}" })\n'
        '        ) { back ->\n'
        '            GuestJoinRoute(navController, back.arguments?.getString("p"))\n'
        '        }\n',
        "nav composable")
    return s
plan_patch(os.path.join(BASE, "navigation/NavGraph.kt"), "NavGraph.kt", p_nav)

# --- InvitePeopleScreen.kt : asli scan + days round-up
def p_invite(s):
    s = rep(s,
        'import com.muwan.muwanchat.data.GuestInvite\n',
        'import com.google.android.gms.codescanner.GmsBarcodeScannerOptions  // GUEST_P2B2\n'
        'import com.google.android.gms.codescanner.GmsBarcodeScanning  // GUEST_P2B2\n'
        'import com.google.mlkit.vision.barcode.common.Barcode  // GUEST_P2B2\n'
        'import com.muwan.muwanchat.data.GuestInvite\n'
        'import com.muwan.muwanchat.data.GuestInviteInbox  // GUEST_P2B2\n',
        "invite imports")
    s = rep(s,
        '                    onClick = { scanSoon = true },\n',
        '                    onClick = {  // GUEST_P2B2: asli QR scan\n'
        '                        val options = GmsBarcodeScannerOptions.Builder()\n'
        '                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)\n'
        '                            .build()\n'
        '                        GmsBarcodeScanning.getClient(context, options).startScan()\n'
        '                            .addOnSuccessListener { barcode ->\n'
        '                                val value = barcode.rawValue\n'
        '                                if (value != null) {\n'
        '                                    GuestInviteInbox.offer(value)\n'
        '                                    navController.popBackStack()\n'
        '                                }\n'
        '                            }\n'
        '                            .addOnFailureListener {\n'
        '                                Toast.makeText(context, "Couldn\'t open the scanner. Check your internet and try again.", Toast.LENGTH_LONG).show()\n'
        '                            }\n'
        '                    },\n',
        "invite scan click")
    s = rep(s,
        'val days = ((expiresAt - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(1L)',
        'val days = ((expiresAt - System.currentTimeMillis() + 86_399_999L) / 86_400_000L).coerceAtLeast(1L)',
        "invite days")
    return s
plan_patch(os.path.join(BASE, "screens/InvitePeopleScreen.kt"), "InvitePeopleScreen.kt", p_invite)

# --- ConversationListScreen.kt : confirm popup host (sirf guest)
def p_conv(s):
    return rep(s,
        '    comingSoonFeature?.let { feature ->\n'
        '        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })\n'
        '    }\n',
        '    comingSoonFeature?.let { feature ->\n'
        '        ComingSoonDialog(feature = feature, onDismiss = { comingSoonFeature = null })\n'
        '    }\n'
        '\n'
        '    if (isGuest) GuestInviteHost()  // GUEST_P2B2: invite confirm popup\n',
        "conv host")
plan_patch(os.path.join(BASE, "screens/ConversationListScreen.kt"), "ConversationListScreen.kt", p_conv)

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
print("\nGuest Phase 2B-2 patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
