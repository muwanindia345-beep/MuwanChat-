#!/usr/bin/env python3
"""
guest_ui_patch.py -- MuwanChat app: Guest registration flow (UI only)

  Register screen "Continue Guest"  ->  Terms & Policy (same screen reuse)
  -> GuestInfoScreen (new: data kaha, risk, warning, English)  -> Profile screen (same reuse)
  -> Confirm/Continue  ->  built-in ComingSoonDialog

Usage (project root se, jahan app/ folder hai):
    python3 guest_ui_patch.py --dry-run
    python3 guest_ui_patch.py
    python3 guest_ui_patch.py --undo
Ya path ke saath: python3 guest_ui_patch.py ~/MuwanChat [--dry-run|--undo]

Safe: har anchor exactly 1 baar milna chahiye warna kuch nahi likhta, backup .guest_ui_backup/,
brace check fail to auto-rollback, dobara chalane par "Already applied".
Auth login/register ka koi behaviour nahi badalta.
"""
import os, shutil, sys

args = [a for a in sys.argv[1:] if not a.startswith("--")]
DRY = "--dry-run" in sys.argv
UNDO = "--undo" in sys.argv
ROOT = os.path.abspath(args[0]) if args else os.getcwd()
PKG = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat")
BK = os.path.join(ROOT, ".guest_ui_backup")
MARK = "GUEST_UI"

NEW_FILE = ("screens/GuestInfoScreen.kt", r'''package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.navigation.Screen

// GUEST_UI -- Guest mode ke bare mein info screen: data kaha save hota hai, risk aur warning.
// Sirf English. Confirm tick ke baad hi Continue enable hota hai.
@Composable
fun GuestInfoScreen(navController: NavController) {
    var understood by remember { mutableStateOf(false) }

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
            Text("About Guest Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                "Please read before you continue",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            GuestInfoSection(
                title = "Where is my data stored?",
                body = "Everything lives only on this device: your name, photo, contacts, messages, media and " +
                    "encryption keys. Nothing is linked to an email, phone number or username, and there is " +
                    "no account on our servers."
            )
            GuestInfoSection(
                title = "What do our servers do?",
                body = "The server only relays end-to-end encrypted messages and call signals. It cannot read " +
                    "them. If the person you message is offline, the encrypted message is held temporarily " +
                    "(for a few days, up to 7) and deleted as soon as it is delivered or expires. " +
                    "No chat history is kept on the server."
            )
            GuestInfoSection(
                title = "How do I connect with people?",
                body = "Guests are not listed in search. You connect only by sharing your QR code or an " +
                    "invite link, or by scanning or opening someone else's. Anyone who has your QR code or " +
                    "link can send you a connection request, so share them carefully."
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip4()
                    .background(Color(0x1AFF5555))
                    .border(1.dp, Color(0xFFFF5555), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("Risks and warnings", color = Color(0xFFFF5555), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "• If you clear app data or uninstall the app, your guest ID and all chats are deleted " +
                        "permanently. This cannot be undone.\n" +
                        "• There is no backup, restore or password reset for guests. Nobody, including us, " +
                        "can recover a lost guest ID.\n" +
                        "• If you lose or change your phone, you lose everything.\n" +
                        "• Messages that stay undelivered for more than 7 days expire and are lost.\n" +
                        "• The other person may keep copies of what you send, such as screenshots.\n" +
                        "• Need a backup or a database of your chats? Use a registered account instead.",
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { understood = !understood }
            ) {
                Checkbox(
                    checked = understood,
                    onCheckedChange = { understood = it },
                    colors = CheckboxDefaults.colors(checkedColor = DarkAccent, uncheckedColor = Color(0xFF888888))
                )
                Text(
                    "I understand that my guest data exists only on this device and can be lost permanently.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { navController.navigate(Screen.Profile.createRoute("guest")) },
                enabled = understood,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkAccent,
                    disabledContainerColor = Color(0xFF444466)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GuestInfoSection(title: String, body: String) {
    Text(title, color = DarkAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    Text(body, color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(18.dp))
}

private fun Modifier.clip4(): Modifier =
    this.then(Modifier.clip(RoundedCornerShape(12.dp)))
''')

# (file, old, new)  -- old file mein exactly 1 baar hona chahiye
EDITS = [
 ("navigation/NavGraph.kt",
  '    object TermsPolicy     : Screen("terms_policy")\n',
  '    object TermsPolicy     : Screen("terms_policy")\n'
  '    object GuestTerms      : Screen("guest_terms")  // GUEST_UI\n'
  '    object GuestInfo       : Screen("guest_info")   // GUEST_UI\n'),
 ("navigation/NavGraph.kt",
  '        composable(Screen.TermsPolicy.route) { TermsPolicyScreen(navController) }\n',
  '        composable(Screen.TermsPolicy.route) { TermsPolicyScreen(navController) }\n'
  '        composable(Screen.GuestTerms.route) { TermsPolicyScreen(navController, guest = true) }  // GUEST_UI\n'
  '        composable(Screen.GuestInfo.route) { GuestInfoScreen(navController) }  // GUEST_UI\n'),

 ("screens/RegisterScreen.kt",
  'onClick = { showGuestComingSoon = true },',
  'onClick = { navController.navigate(Screen.GuestTerms.route) },  // GUEST_UI'),

 ("screens/TermsPolicyScreen.kt",
  'fun TermsPolicyScreen(navController: NavController) {',
  'fun TermsPolicyScreen(navController: NavController, guest: Boolean = false) {  // GUEST_UI'),
 ("screens/TermsPolicyScreen.kt",
  '            Text("Terms & Policy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)\n',
  '            if (guest) {  // GUEST_UI\n'
  '                IconButton(onClick = { navController.popBackStack() }) {\n'
  '                    Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)\n'
  '                }\n'
  '            }\n'
  '            Text("Terms & Policy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)\n'),
 ("screens/TermsPolicyScreen.kt",
  '                "• Your data is transmitted securely, and encrypted at rest on our servers.",\n',
  '                if (guest) "• Guest chats are end-to-end encrypted and kept on your device, not on our servers."  // GUEST_UI\n'
  '                else "• Your data is transmitted securely, and encrypted at rest on our servers.",\n'),
 ("screens/TermsPolicyScreen.kt",
  '                    navController.navigate(Screen.Profile.createRoute("onboarding")) {\n'
  '                        popUpTo(Screen.TermsPolicy.route) { inclusive = true }\n'
  '                    }\n',
  '                    if (guest) {  // GUEST_UI\n'
  '                        navController.navigate(Screen.GuestInfo.route)\n'
  '                    } else navController.navigate(Screen.Profile.createRoute("onboarding")) {\n'
  '                        popUpTo(Screen.TermsPolicy.route) { inclusive = true }\n'
  '                    }\n'),

 ("screens/ProfileScreen.kt",
  '    val isOnboarding = mode == "onboarding"\n'
  '    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }\n',
  '    val isGuest = mode == "guest"  // GUEST_UI\n'
  '    val isOnboarding = mode == "onboarding" || isGuest\n'
  '    val db by remember { lazy { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) } }  // GUEST_UI: guest mein auth DB nahi banta\n'),
 ("screens/ProfileScreen.kt",
  '    var errorMsg by remember { mutableStateOf("") }\n',
  '    var errorMsg by remember { mutableStateOf("") }\n'
  '    var showGuestComingSoon by remember { mutableStateOf(false) }  // GUEST_UI\n'),
 ("screens/ProfileScreen.kt",
  '        val finalGender = if (gender == "Custom") customGender.trim() else gender\n',
  '        if (isGuest) { showGuestComingSoon = true; return }  // GUEST_UI\n'
  '        val finalGender = if (gender == "Custom") customGender.trim() else gender\n'),
 ("screens/ProfileScreen.kt",
  '    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {\n',
  '    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {\n'
  '        if (showGuestComingSoon) {  // GUEST_UI\n'
  '            ComingSoonDialog(feature = "👤 Guest Mode", onDismiss = { showGuestComingSoon = false })\n'
  '        }\n'),
 ("screens/ProfileScreen.kt",
  '                    if (!isOnboarding) {\n'
  '                        IconButton(onClick = { navController.popBackStack() }) {\n',
  '                    if (!isOnboarding || isGuest) {  // GUEST_UI\n'
  '                        IconButton(onClick = { navController.popBackStack() }) {\n'),
]


def die(m):
    print("[STOP] " + m); sys.exit(1)

def rd(p):
    with open(p, encoding="utf-8", newline="") as f: return f.read()

def wr(p, t):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p + ".tmp", "w", encoding="utf-8", newline="") as f: f.write(t)
    os.replace(p + ".tmp", p)

def files():
    out = []
    for f, _, _ in EDITS:
        if f not in out: out.append(f)
    return out

def restore():
    for f in files():
        b = os.path.join(BK, f)
        if os.path.exists(b): shutil.copy2(b, os.path.join(PKG, f))
    n = os.path.join(PKG, NEW_FILE[0])
    if os.path.exists(n): os.remove(n)

def balanced(t):
    # braces/parens balance, strings aur comments chhodkar
    d = {"{": 0, "(": 0}
    i, n = 0, len(t)
    while i < n:
        c = t[i]
        if t.startswith("//", i):
            i = t.find("\n", i); i = n if i < 0 else i; continue
        if t.startswith("/*", i):
            j = t.find("*/", i + 2); i = n if j < 0 else j + 2; continue
        if c == '"':
            if t.startswith('"""', i):
                j = t.find('"""', i + 3); i = n if j < 0 else j + 3; continue
            i += 1
            while i < n and t[i] != '"':
                i += 2 if t[i] == "\\" else 1
            i += 1; continue
        if c == "'":
            i += 4 if t[i+1:i+2] == "\\" else 3; continue
        if c == "{": d["{"] += 1
        elif c == "}": d["{"] -= 1
        elif c == "(": d["("] += 1
        elif c == ")": d["("] -= 1
        i += 1
    return d["{"] == 0 and d["("] == 0

def main():
    if not os.path.isdir(PKG): die("Project root nahi mila (app/src/main/java/com/muwan/muwanchat chahiye): " + ROOT)
    if UNDO:
        if not os.path.isdir(BK): die("Backup nahi mila, undo nahi ho sakta.")
        restore(); shutil.rmtree(BK); print("Undo done. Guest UI hata diya."); return

    present = sum(1 for f, o, n in EDITS if n in rd(os.path.join(PKG, f)))
    has_new = os.path.exists(os.path.join(PKG, NEW_FILE[0]))
    if present == len(EDITS) and has_new:
        print("Already applied (GUEST_UI). Kuch nahi karna."); return
    if present or has_new:
        die("Patch aadha laga hua hai. Pehle: python3 guest_ui_patch.py --undo")

    staged = {}
    for f, old, new in EDITS:
        p = os.path.join(PKG, f)
        if not os.path.exists(p): die("File nahi mili: " + f)
        t = staged.get(f) or rd(p)
        if "\r\n" in t: die(f + " mein CRLF hai, LF chahiye.")
        c = t.count(old)
        if c != 1: die("Anchor %s mein %d baar mila (1 chahiye), kuch nahi badla:\n%s" % (f, c, old.strip()[:90]))
        staged[f] = t.replace(old, new)

    print(("[dry-run] " if DRY else "") + "plan:")
    print("  %-34s created" % NEW_FILE[0])
    for f in files(): print("  %-34s patched (%d edits)" % (f, sum(1 for e in EDITS if e[0] == f)))
    if DRY: print("\nDry run complete -- kuch likha nahi gaya."); return

    for f in files():
        b = os.path.join(BK, f); os.makedirs(os.path.dirname(b), exist_ok=True)
        shutil.copy2(os.path.join(PKG, f), b)
    try:
        wr(os.path.join(PKG, NEW_FILE[0]), NEW_FILE[1])
        for f, t in staged.items(): wr(os.path.join(PKG, f), t)
    except Exception as e:
        restore(); die("Likhte waqt error (%s), sab wapas." % e)

    bad = [f for f in files() + [NEW_FILE[0]] if not balanced(rd(os.path.join(PKG, f)))]
    if bad:
        restore(); shutil.rmtree(BK, ignore_errors=True)
        die("Brace/bracket balance galat (%s). Rollback kar diya, kuch nahi badla." % ", ".join(bad))

    print("\nDone. Ab build karo:  ./gradlew assembleDebug   (ya jo tum chalate ho)")
    print("Flow: Register -> Continue Guest -> Terms & Policy -> About Guest Mode -> Profile -> Coming Soon")
    print("Undo: python3 guest_ui_patch.py --undo   (.guest_ui_backup/ ko git mein mat daalna)")

main()
