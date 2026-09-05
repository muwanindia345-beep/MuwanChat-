# Build fix — ForwardScreen.kt:77 compile error
#
# Root cause: @mentions feature ne AppSocketManager.sendGroupMessage() mein
# ek naya parameter "mentions: List<String>" add kiya, onAck se THEEK PEHLE:
#   ...replyToId, isForwarded, mentions = emptyList(), onAck = {}
#
# ForwardScreen.kt group-forward call positional args se likha gaya tha:
#   sendGroupMessage(id, roomId, content, type, fileName, mimeType, null, true, cb)
# Yahan 9th positional arg (cb, jo (Boolean)->Unit hai) ab "mentions"
# (List<String>) slot mein chala gaya kyunki naya param usse pehle aa gaya —
# isi wajah se "inferred type is (Boolean) -> Unit but List<String> was
# expected" compile error aaya.
#
# Fix: cb ko named argument (onAck = cb) bana diya, taaki naya mentions
# param apne default (emptyList()) se fill ho jaaye aur cb hamesha sahi
# slot (onAck) mein bindh jaaye, chahe future mein aur params kahin bhi add ho.
#
# Termux mein repo root (jahan app/ folder hai) se run karo:
#   python patch_fix_forward_group_mentions_build.py

f = "app/src/main/java/com/muwan/muwanchat/screens/ForwardScreen.kt"
s = open(f).read()
old = '''                AppSocketManager.sendGroupMessage(id, target.roomId, content, msg.type, msg.fileName, msg.mimeType, null, true, cb)'''
new = '''                AppSocketManager.sendGroupMessage(
                    id, target.roomId, content, msg.type, msg.fileName, msg.mimeType, null, true,
                    onAck = cb
                )'''
assert old in s, "ForwardScreen.kt: pattern not found (file badal chuki hogi — manually check line ~77)"
open(f, "w").write(s.replace(old, new, 1))
print("✅ ForwardScreen.kt patched — sendGroupMessage() ka cb ab onAck= named arg se bind hoga")
