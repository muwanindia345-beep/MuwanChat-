import sys, os

# Usage (repo root se):  python guest_paste_link_patch.py .
# Invite People screen mein "scan" ke neeche naya button: "Add a shared link".
# Link paste karo -> Continue -> Chats home par wahi "Add contact?" popup -> Add -> contact judta hai
# (aur dusre ke phone mein bhi hello se contact aa jata hai). Sirf guest screen, auth ko koi asar nahi.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
P = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens/InvitePeopleScreen.kt")
MARK = "GUEST_PASTE"

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: anchor %d baar mila (1 hona chahiye). Kuch nahi likha." % (name, n))
        sys.exit(1)
    return s.replace(old, new)

if not os.path.isfile(P):
    print("FAIL: file nahi mili: " + P); sys.exit(1)
s = open(P, encoding="utf-8").read()
if MARK in s:
    print("Already patched, kuch nahi kiya."); sys.exit(0)
if "GUEST_P2B2" not in s:
    print("FAIL: pehle guest_phase2b2_patch.py lagao."); sys.exit(1)

s = rep(s,
    '    var scanSoon by remember { mutableStateOf(false) }\n',
    '    var scanSoon by remember { mutableStateOf(false) }\n'
    '    var showLinkBox by remember { mutableStateOf(false) }  // GUEST_PASTE\n'
    '    var pastedLink by remember { mutableStateOf("") }\n',
    "state")

s = rep(s,
    '    if (scanSoon) {\n        ComingSoonDialog(feature = "📷 Scan QR Code", onDismiss = { scanSoon = false })\n    }\n',
    '    if (scanSoon) {\n        ComingSoonDialog(feature = "📷 Scan QR Code", onDismiss = { scanSoon = false })\n    }\n'
    '\n'
    '    if (showLinkBox) {  // GUEST_PASTE: link paste karke add\n'
    '        AlertDialog(\n'
    '            onDismissRequest = { showLinkBox = false },\n'
    '            containerColor = DarkSheet,\n'
    '            title = { Text("Add a shared link", color = Color.White, fontWeight = FontWeight.Bold) },\n'
    '            text = {\n'
    '                Column {\n'
    '                    Text(\n'
    '                        "Paste the invite link your friend sent you.",\n'
    '                        color = Color(0xFFCCCCCC),\n'
    '                        fontSize = 13.sp\n'
    '                    )\n'
    '                    Spacer(Modifier.height(10.dp))\n'
    '                    OutlinedTextField(\n'
    '                        value = pastedLink,\n'
    '                        onValueChange = { pastedLink = it },\n'
    '                        placeholder = { Text("https://…/g#…", color = Color(0xFF666666)) },\n'
    '                        maxLines = 4,\n'
    '                        modifier = Modifier.fillMaxWidth(),\n'
    '                        colors = OutlinedTextFieldDefaults.colors(\n'
    '                            focusedTextColor = Color.White,\n'
    '                            unfocusedTextColor = Color.White,\n'
    '                            focusedBorderColor = DarkAccent,\n'
    '                            unfocusedBorderColor = Color(0xFF444444),\n'
    '                            cursorColor = DarkAccent\n'
    '                        )\n'
    '                    )\n'
    '                    TextButton(onClick = {\n'
    '                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager\n'
    '                        val clip = cm?.primaryClip\n'
    '                        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""\n'
    '                        if (t.isNotBlank()) pastedLink = t.trim()\n'
    '                        else Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()\n'
    '                    }) {\n'
    '                        Text("Paste from clipboard", color = DarkAccent)\n'
    '                    }\n'
    '                }\n'
    '            },\n'
    '            confirmButton = {\n'
    '                Button(\n'
    '                    onClick = {\n'
    '                        val v = pastedLink.trim()\n'
    '                        if (v.isEmpty()) {\n'
    '                            Toast.makeText(context, "Paste a link first", Toast.LENGTH_SHORT).show()\n'
    '                        } else {\n'
    '                            showLinkBox = false\n'
    '                            pastedLink = ""\n'
    '                            GuestInviteInbox.offer(v)\n'
    '                            navController.popBackStack()\n'
    '                        }\n'
    '                    },\n'
    '                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),\n'
    '                    shape = RoundedCornerShape(12.dp)\n'
    '                ) { Text("Continue", color = Color.White) }\n'
    '            },\n'
    '            dismissButton = {\n'
    '                TextButton(onClick = { showLinkBox = false }) { Text("Cancel", color = Color(0xFF888888)) }\n'
    '            }\n'
    '        )\n'
    '    }\n',
    "dialog")

s = rep(s,
    '                    Text("Scan a QR code", color = Color.White, fontWeight = FontWeight.Bold)\n'
    '                }\n'
    '            } else {\n',
    '                    Text("Scan a QR code", color = Color.White, fontWeight = FontWeight.Bold)\n'
    '                }\n'
    '\n'
    '                Spacer(Modifier.height(12.dp))  // GUEST_PASTE\n'
    '                OutlinedButton(\n'
    '                    onClick = { showLinkBox = true },\n'
    '                    modifier = Modifier.fillMaxWidth(),\n'
    '                    shape = RoundedCornerShape(12.dp)\n'
    '                ) {\n'
    '                    Text("Add a shared link", color = DarkAccent, fontWeight = FontWeight.Bold)\n'
    '                }\n'
    '                Spacer(Modifier.height(8.dp))\n'
    '                Text(\n'
    '                    "Can\'t scan or open the link? Paste it here instead.",\n'
    '                    color = Color(0xFF888888),\n'
    '                    fontSize = 12.sp,\n'
    '                    textAlign = TextAlign.Center\n'
    '                )\n'
    '                Spacer(Modifier.height(24.dp))\n'
    '            } else {\n',
    "button")

open(P, "w", encoding="utf-8").write(s)
print("OK  screens/InvitePeopleScreen.kt\nPaste-link patch lag gaya. Ab: git add -A && git commit && git push")
