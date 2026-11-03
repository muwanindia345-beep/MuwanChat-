import sys, os

# Usage (Termux, repo root MuwanChat se):
#   python guest_phase2b1_patch.py .
# PEHLE guest_phase1_patch.py aur guest_phase2a_patch.py lage hone chahiye.
#
# GUEST PHASE 2B-1 -- "Invite People" screen (QR code + link).
#  * Guest ke FAB mein "Invite People" tap -> nayi screen: QR code, invite link, Copy aur Share.
#  * Header/colours Settings jaisi hi (DarkHeader, back arrow, same fonts) -- baaki screens ke saath consistent.
#  * Invite backend par banta hai (POST /guest/invite): server sirf secret ka SHA-256 hash rakhta hai.
#    Link/QR = https://<backend>/g#<payload>  (payload: guestId + secret + naam + country, base64url).
#    Ek invite 7 din, 10 log tak. Wahi invite dobara use hota hai (har baar naya nahi banta).
#  * "Scan a QR code" button abhi Coming Soon (scan + link kholna agla step, 2B-2).
#
# Avatar QR/link mein NAHI jaata (QR scan na ho paye itna bada ho jata hai) -- wo connect hone ke baad
# encrypted message se jayega (Phase 2C).
#
# Naye files : data/GuestInvite.kt, screens/InvitePeopleScreen.kt
# Patch      : app/build.gradle.kts (ZXing), network/GuestApi.kt, data/GuestStore.kt,
#              navigation/NavGraph.kt, screens/ConversationListScreen.kt
# Dobara chalane par "already patched". Koi anchor na mile to KUCH NAHI likhta.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
MARK = "GUEST_P2B1"

INVITE_KT = r'''package com.muwan.muwanchat.data

// GUEST_P2B1
// Guest invite: ek secret banao -> uska SHA-256 hash server par (POST /guest/invite) -> link/QR mein
// guestId + secret + naam + country. Server secret kabhi nahi dekhta, sirf hash. Redeem karne wala secret
// link se leta hai aur POST /guest/invite/redeem mein bhejta hai (2B-2).
// Wahi invite dobara use hota hai jab tak ~1 ghanta se zyada bacha ho (server pe dobara POST karne se
// "used" counter zero ho jata hai, isliye har baar naya nahi banate).
import android.content.Context
import android.util.Base64
import com.muwan.muwanchat.network.GuestInviteRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.security.MessageDigest
import java.security.SecureRandom

object GuestInvite {

    class Info(val link: String, val expiresAtMs: Long)

    sealed class Result {
        class Ok(val info: Info) : Result()
        class Fail(val reason: String) : Result()
    }

    private const val USES = 10
    private const val TTL_HOURS = 168          // 7 din
    private const val REUSE_MIN_LEFT_MS = 60L * 60 * 1000

    private val lock = Mutex()

    suspend fun ensure(context: Context): Result = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                doEnsure(context.applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Fail(friendlyErrorMessage(e))
            }
        }
    }

    private suspend fun doEnsure(context: Context): Result {
        val session = GuestSession.ensure(context)
        val guestId = when (session) {
            is GuestSession.Result.Ok -> session.guestId
            is GuestSession.Result.Fail -> return Result.Fail(session.reason)
        }
        val token = GuestStore.token(context) ?: return Result.Fail("Guest session missing")

        val now = System.currentTimeMillis()
        val storedSecret = GuestStore.inviteSecret(context)
        val storedExpiry = GuestStore.inviteExpiryMs(context)

        val secret: String
        val expiry: Long
        if (storedSecret != null && storedExpiry - now >= REUSE_MIN_LEFT_MS) {
            secret = storedSecret
            expiry = storedExpiry
        } else {
            val fresh = newSecret()
            val res = RetrofitClient.guestApi.createInvite(
                "Bearer " + token,
                com.muwan.muwanchat.network.GuestInviteRequest(sha256Hex(fresh), USES, TTL_HOURS)
            )
            val body = res.body()
            if (!res.isSuccessful || body == null || body.success != true) {
                return Result.Fail("Invite failed (" + errorOf(res) + ")")
            }
            secret = fresh
            expiry = now + TTL_HOURS * 3600L * 1000L - 60_000L
            GuestStore.saveInvite(context, fresh, expiry)
        }

        val profile = try {
            MuwanChatDb.get(context, guestId).myProfileDao().get()
        } catch (_: Exception) {
            null
        }

        val json = JSONObject()
            .put("v", 1)
            .put("g", guestId)
            .put("s", secret)
            .put("n", (profile?.name ?: "").take(40))
            .put("c", (profile?.country ?: "").take(40))
            .toString()
        val payload = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val base = com.muwan.muwanchat.BuildConfig.BACKEND_URL.trimEnd('/')
        return Result.Ok(Info(base + "/g#" + payload, expiry))
    }

    private fun newSecret(): String {
        val b = ByteArray(24)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // Backend: crypto.createHash('sha256').update(secret) -- secret string ke UTF-8 bytes ka hash
    private fun sha256Hex(s: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (x in h) sb.append("%02x".format(x.toInt() and 0xff))
        return sb.toString()
    }

    private fun errorOf(res: Response<*>): String {
        val raw = try { res.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { JSONObject(raw ?: "").optString("error") } catch (_: Exception) { "" }
        return if (msg.isNotEmpty()) "HTTP " + res.code() + ": " + msg else "HTTP " + res.code()
    }
}
'''

SCREEN_KT = r'''package com.muwan.muwanchat.screens

// GUEST_P2B1
// Guest "Invite People": QR code + link + Copy + Share. Header Settings/GuestInfo jaisa (consistency).
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.GuestInvite
import kotlinx.coroutines.launch

private fun makeQr(text: String, size: Int): Bitmap? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val w = m.width
    val h = m.height
    val px = IntArray(w * h)
    for (y in 0 until h) {
        val off = y * w
        for (x in 0 until w) {
            px[off + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
} catch (_: Exception) {
    null
}

@Composable
fun InvitePeopleScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var link by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanSoon by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            when (val r = GuestInvite.ensure(context)) {
                is GuestInvite.Result.Ok -> {
                    link = r.info.link
                    expiresAt = r.info.expiresAtMs
                }
                is GuestInvite.Result.Fail -> {
                    error = r.reason
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    if (scanSoon) {
        ComingSoonDialog(feature = "📷 Scan QR Code", onDismiss = { scanSoon = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Invite People", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentLink = link

            if (loading) {
                Spacer(Modifier.height(80.dp))
                CircularProgressIndicator(color = DarkAccent)
            } else if (currentLink != null) {
                Text(
                    "Ask your friend to scan this QR code or open your link to connect with you.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                val qr = remember(currentLink) { makeQr(currentLink, 640) }
                if (qr != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(14.dp)
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Invite QR code",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text("Couldn't draw the QR code. Use the link below.", color = Color(0xFFFF5555), fontSize = 13.sp)
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSheet)
                        .padding(14.dp)
                ) {
                    Text(
                        currentLink,
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Invite link", currentLink))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSheet),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Copy link", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Chat with me on TalkWave (guest mode): " + currentLink)
                            }
                            val chooser = Intent.createChooser(send, "Share invite")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Share", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                val days = ((expiresAt - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(1L)
                Text(
                    "This invite works for about " + days + " day(s) and up to 10 people.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Anyone who has your QR code or link can send you a connection request, so share it carefully.",
                    color = Color(0xFFFF5555),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { scanSoon = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Scan a QR code", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(60.dp))
                Text(
                    "Couldn't create your invite. Check your internet and try again.",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", color = Color(0xFF888888), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { load() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
'''

API_DATA = r'''data class GuestInviteRequest(
    val hash: String,
    val uses: Int,
    val ttlHours: Int
)

data class GuestInviteResponse(
    val success: Boolean?,
    val uses: Int?,
    val expiresAt: String?,
    val error: String?
)

'''

STORE_ADD = r'''    fun isGuestActive(context: Context): Boolean = activeGuestId(context).isNotEmpty()

    // ───── GUEST_P2B1: apna invite (secret + expiry). Secret Keystore-encrypted prefs mein. ─────
    fun inviteSecret(context: Context): String? =
        prefs(context).getString("invite_secret", null)?.takeIf { it.isNotEmpty() }

    fun inviteExpiryMs(context: Context): Long = prefs(context).getLong("invite_exp", 0L)

    fun saveInvite(context: Context, secret: String, expiryMs: Long) {
        prefs(context).edit().putString("invite_secret", secret).putLong("invite_exp", expiryMs).apply()
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

def plan_new_file(rel, content):
    p = os.path.join(BASE, rel)
    if os.path.exists(p):
        if MARK in rd(p):
            skipped.append(rel)
            return
        print("FAIL: %s pehle se hai par GUEST_P2B1 wala nahi. Kuch nahi likha." % rel)
        sys.exit(1)
    writes[p] = content

def plan_patch(path, label, fn, need=None):
    if not os.path.isfile(path):
        print("FAIL: file nahi mili: %s (repo root se chalao; Phase 1 aur 2A pehle lagao)" % path)
        sys.exit(1)
    s = rd(path)
    if MARK in s:
        skipped.append(label)
        return
    if need and need not in s:
        print("FAIL: %s mein %s nahi mila -- pehle Phase 1/2A patch lagao. Kuch nahi likha." % (label, need))
        sys.exit(1)
    writes[path] = fn(s)

plan_new_file("data/GuestInvite.kt", INVITE_KT)
plan_new_file("screens/InvitePeopleScreen.kt", SCREEN_KT)

# --- build.gradle.kts : ZXing core (QR banane ke liye)
def p_gradle(s):
    return rep(s,
        '    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")\n',
        '    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")\n'
        '    // GUEST_P2B1 -- QR code banane ke liye (pure Java, chhota)\n'
        '    implementation("com.google.zxing:core:3.5.3")\n',
        "gradle zxing")
plan_patch(os.path.join(ROOT, "app/build.gradle.kts"), "build.gradle.kts", p_gradle, need="GUEST_P1")

# --- GuestApi.kt : createInvite
def p_api(s):
    s = rep(s, 'import retrofit2.http.GET\n', 'import retrofit2.http.GET\nimport retrofit2.http.Header  // GUEST_P2B1\n', "api import")
    s = rep(s, 'interface GuestApi {\n', API_DATA + 'interface GuestApi {\n', "api data")
    s = rep(s,
        '    suspend fun session(@Body request: GuestSessionRequest): Response<GuestSessionResponse>\n',
        '    suspend fun session(@Body request: GuestSessionRequest): Response<GuestSessionResponse>\n'
        '\n'
        '    // GUEST_P2B1: invite = secret ka SHA-256 hash (secret server ko kabhi nahi jaata)\n'
        '    @POST("guest/invite")\n'
        '    suspend fun createInvite(\n'
        '        @Header("Authorization") auth: String,\n'
        '        @Body request: GuestInviteRequest\n'
        '    ): Response<GuestInviteResponse>\n',
        "api method")
    return s
plan_patch(os.path.join(BASE, "network/GuestApi.kt"), "GuestApi.kt", p_api, need="GUEST_P1")

# --- GuestStore.kt : invite secret/expiry
def p_store(s):
    return rep(s,
        '    fun isGuestActive(context: Context): Boolean = activeGuestId(context).isNotEmpty()\n',
        STORE_ADD, "store invite")
plan_patch(os.path.join(BASE, "data/GuestStore.kt"), "GuestStore.kt", p_store, need="GUEST_P2A")

# --- NavGraph.kt : naya route
def p_nav(s):
    s = rep(s,
        '    object GuestInfo       : Screen("guest_info")   // GUEST_UI\n',
        '    object GuestInfo       : Screen("guest_info")   // GUEST_UI\n'
        '    object GuestInvite     : Screen("guest_invite")  // GUEST_P2B1\n',
        "nav screen object")
    s = rep(s,
        '        composable(Screen.GuestInfo.route) { GuestInfoScreen(navController) }  // GUEST_UI\n',
        '        composable(Screen.GuestInfo.route) { GuestInfoScreen(navController) }  // GUEST_UI\n'
        '        composable(Screen.GuestInvite.route) { InvitePeopleScreen(navController) }  // GUEST_P2B1\n',
        "nav composable")
    return s
plan_patch(os.path.join(BASE, "navigation/NavGraph.kt"), "NavGraph.kt", p_nav, need="GUEST_P2A")

# --- ConversationListScreen.kt : Invite People -> nayi screen
def p_conv(s):
    return rep(s,
        '                    FabSheetOption(Icons.Filled.Add, "Invite People") {\n'
        '                        showFabSheet = false\n'
        '                        comingSoonFeature = "👥 Invite People"\n'
        '                    }\n',
        '                    FabSheetOption(Icons.Filled.Add, "Invite People") {\n'
        '                        showFabSheet = false\n'
        '                        navController.navigate(Screen.GuestInvite.route)  // GUEST_P2B1\n'
        '                    }\n',
        "conv invite people")
plan_patch(os.path.join(BASE, "screens/ConversationListScreen.kt"), "ConversationListScreen.kt", p_conv, need="GUEST_P2A")

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
print("\nGuest Phase 2B-1 patch lag gaya. Ab: git add -A && git commit && git push (beta build).")
