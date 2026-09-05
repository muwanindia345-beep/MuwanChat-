# AcceptedUsersScreen.kt — "Remove user?" confirmation dialog ka body text
# Hinglish se English mein. UI/colors/layout kuch bhi nahi badla, sirf text.
#
# Termux mein repo root (jahan app/ folder hai) se run karo:
#   python patch_english_remove_user_dialog.py

f = "app/src/main/java/com/muwan/muwanchat/screens/AcceptedUsersScreen.kt"
s = open(f).read()
old = '''                    "Yeh permanent hai — connection hat jaayega aur chat history dono taraf se saaf ho jaayegi. " +
                        "${user.username} search screen me wapas as a new request dikhega.",'''
new = '''                    "This is permanent — the connection will be removed and chat history will be cleared on both sides. " +
                        "${user.username} will show up again in search as a new request.",'''
assert old in s, "AcceptedUsersScreen.kt: pattern not found"
open(f, "w").write(s.replace(old, new, 1))
print("✅ AcceptedUsersScreen.kt patched — remove-user dialog text ab English mein")
