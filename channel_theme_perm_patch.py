import sys, os

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
P = os.path.join(ROOT, "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt")
s = open(P, encoding="utf-8").read()

if "CHANNEL_THEME_PERM_FIX" in s:
    print("Already patched, kuch nahi kiya.")
    sys.exit(0)

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        print("FAIL %s: matched %d times" % (name, n))
        sys.exit(1)
    return s.replace(old, new)

s = rep(s,
'''    isChannel: Boolean = false,
    onShareLink: () -> Unit = {},''',
'''    isChannel: Boolean = false,
    canChangeMessageTheme: Boolean = true, // CHANNEL_THEME_PERM_FIX
    onShareLink: () -> Unit = {},''', "header param")

s = rep(s,
'''                        // [channel-message-theme]
                        DropdownMenuItem(''',
'''                        // [channel-message-theme]
                        // CHANNEL_THEME_PERM_FIX: normal member ko sirf Profile / Share / Leave
                        if (canChangeMessageTheme) DropdownMenuItem(''', "menu item")

s = rep(s,
'''                isChannel = group?.isChannel ?: false,
                onSendInviteInApp = {''',
'''                isChannel = group?.isChannel ?: false,
                canChangeMessageTheme = isAdmin || group?.owner == myUid, // CHANNEL_THEME_PERM_FIX
                onSendInviteInApp = {''', "call site")

open(P, "w", encoding="utf-8").write(s)
print("OK: GroupChatScreen.kt")
print("DONE")
