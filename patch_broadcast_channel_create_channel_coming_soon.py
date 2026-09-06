# -*- coding: utf-8 -*-
# Patch 2/2 -- "Create Channel" tap par existing ComingSoonDialog dikhata
# hai (wahi component jo PhoneOTP screen wagera me already reuse ho raha
# hai -- English text, DarkAccent/DarkSheet styling, koi naya dialog nahi
# banaya). Requires patch_broadcast_channel_menu.py pehle laga hona.

path = "app/src/main/java/com/muwan/muwanchat/screens/BroadcastChannelsScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    src = f.read()

old = '''                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {'''

new = '''                }
            }

            if (showCreateComingSoon) {
                ComingSoonDialog(
                    feature = "Create Channel",
                    onDismiss = { showCreateComingSoon = false }
                )
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {'''

n = src.count(old)
if n != 1:
    raise SystemExit(f"[FAIL] coming-soon wiring anchor: found {n} matches (expected 1) -- did patch_broadcast_channel_menu.py run first?")
src = src.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print("[OK] 'Create Channel' now shows the existing Coming Soon dialog")
