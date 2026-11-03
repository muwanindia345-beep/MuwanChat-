import sys, os

# Usage (Termux, repo root MuwanChat se):
#   python guest_settings_backup_patch.py .
# PEHLE guest_phase2a_patch.py lag chuka hona chahiye.
#
# Settings ki pehli row: auth user ko "Account Settings" (same as before).
# Guest ko wahi row "Backup & Restore" dikhti hai (Google Drive icon, abhi Coming Soon popup) --
# Account Settings guest ke liye hat gayi, aur Drive backup ke liye jagah ready hai.

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
F = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens/SettingsScreen.kt")
MARK = "GUEST_BACKUP_ROW"

if not os.path.isfile(F):
    print("FAIL: file nahi mili: %s (repo root se chalao)" % F)
    sys.exit(1)

s = open(F, encoding="utf-8").read()
if MARK in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)
if "GUEST_P2A" not in s:
    print("FAIL: pehle guest_phase2a_patch.py lagao. Kuch nahi likha.")
    sys.exit(1)

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: anchor %d baar mila (1 hona chahiye). Kuch nahi likha." % (name, n))
        sys.exit(1)
    return s.replace(old, new)

s = rep(s,
'''    if (guestLogoutSoon) {  // GUEST_P2A
        ComingSoonDialog(feature = "🚪 Logout / Delete Guest Data", onDismiss = { guestLogoutSoon = false })
    }
''',
'''    if (guestLogoutSoon) {  // GUEST_P2A
        ComingSoonDialog(feature = "🚪 Logout / Delete Guest Data", onDismiss = { guestLogoutSoon = false })
    }
    var guestBackupSoon by remember { mutableStateOf(false) }  // GUEST_BACKUP_ROW
    if (guestBackupSoon) {  // GUEST_BACKUP_ROW
        ComingSoonDialog(feature = "☁️ Google Drive Backup", onDismiss = { guestBackupSoon = false })
    }
''', "state+dialog")

s = rep(s,
'                .clickable { navController.navigate(Screen.AccountSettings.route) }\n',
'                .clickable { if (isGuestSession) guestBackupSoon = true else navController.navigate(Screen.AccountSettings.route) }  // GUEST_BACKUP_ROW\n',
"row click")

s = rep(s,
'Icon(Icons.Filled.ManageAccounts, contentDescription = "Account Settings", tint = Color.White)',
'Icon(if (isGuestSession) Icons.Filled.CloudUpload else Icons.Filled.ManageAccounts, contentDescription = "Account Settings", tint = Color.White)',
"row icon")

s = rep(s,
'Text("Account Settings", color = Color.White, fontSize = 16.sp)',
'Text(if (isGuestSession) "Backup & Restore" else "Account Settings", color = Color.White, fontSize = 16.sp)',
"row title")

s = rep(s,
'                    "Password, email, username, delete account",\n',
'                    if (isGuestSession) "Google Drive backup of your guest data" else "Password, email, username, delete account",\n',
"row subtitle")

open(F, "w", encoding="utf-8").write(s)
print("OK: SettingsScreen.kt -- guest ko Backup & Restore row (Coming Soon), auth ko Account Settings.")
