# -*- coding: utf-8 -*-
# Broadcast channel members (non-admin) ke liye ab bottom mein KUCH bhi
# nahi dikhega -- na input bar, na "Only admins can send messages" wala
# banner (wo announcement-group jaisa cheez hai, channel ke liye nahi
# chahiye tha). Admin ke liye kuch nahi badla -- unhe normal ChatInputBar
# hi dikhta rehta hai, jaisa pehle dikhta tha.
#
# Fix: channel+non-admin ka naya check "onlyAdminsCanSend && !isAdmin"
# wale generic banner-check se PEHLE aata hai, isliye channel members is
# naye khaali branch mein match ho jaate hain aur purana banner unhe kabhi
# nahi dikhta.

path = "app/src/main/java/com/muwan/muwanchat/screens/GroupChatScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''        } else if (onlyAdminsCanSend && !isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .clickable { showAdminsSheet = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val onlyText = buildAnnotatedString {
                    append("Only ")
                    withStyle(SpanStyle(color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)) {
                        append("admins")
                    }
                    append(" can send messages")
                }
                Text(onlyText, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {'''

new = '''        } else if ((group?.isChannel ?: false) && !isAdmin) {
            // Broadcast channel member: bilkul kuch nahi -- na input bar,
            // na "only admins" banner. Screen seedha last message pe khatam
            // ho jaati hai.
        } else if (onlyAdminsCanSend && !isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkInputBg)
                    .clickable { showAdminsSheet = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val onlyText = buildAnnotatedString {
                    append("Only ")
                    withStyle(SpanStyle(color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)) {
                        append("admins")
                    }
                    append(" can send messages")
                }
                Text(onlyText, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] input-bar conditional block: found {n} matches (expected 1)")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] Channel members: no input bar, no 'only admins' banner. Admins unaffected.")
