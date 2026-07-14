import re

path = "app/src/main/java/com/muwan/muwanchat/screens/RegisterScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_block = """colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),"""

new_block = """colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent,
                    errorBorderColor = Color(0xFFFF5555),
                    errorTextColor = Color.White,
                    errorLabelColor = Color(0xFFFF5555),
                    errorCursorColor = DarkAccent,
                    errorLeadingIconColor = DarkAccent,
                    errorTrailingIconColor = Color(0xFF888888)
                ),"""

count = content.count(old_block)
if count == 0:
    print("ERROR: old_block not found, file already patched or changed manually")
else:
    content = content.replace(old_block, new_block)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Patched {count} OutlinedTextField color block(s) in RegisterScreen.kt")
