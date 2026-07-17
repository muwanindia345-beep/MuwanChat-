import re

path = "app/src/main/java/com/muwan/muwanchat/screens/RegisterScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old_import = "import androidx.compose.foundation.text.KeyboardActions\n"
new_import = "import androidx.compose.foundation.text.KeyboardActions\nimport androidx.compose.foundation.text.ClickableText\n"

old_import2 = "import androidx.compose.ui.text.font.FontWeight\n"
new_import2 = "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.SpanStyle\nimport androidx.compose.ui.text.buildAnnotatedString\nimport androidx.compose.ui.text.withStyle\n"

old_block = """            TextButton(onClick = { navController.popBackStack() }) {
                Text("Already have an account? ", color = Color(0xFF888888))
                Text("Login", color = DarkAccent, fontWeight = FontWeight.Bold)
            }"""

new_block = """            val registerFooterText = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF888888))) {
                    append("Already have an account? ")
                }
                withStyle(SpanStyle(color = DarkAccent, fontWeight = FontWeight.Bold)) {
                    append("Login")
                }
            }
            ClickableText(
                text = registerFooterText,
                onClick = { navController.popBackStack() }
            )"""

if content.count(old_import) == 0:
    print("ERROR: import anchor 1 not found")
else:
    content = content.replace(old_import, new_import, 1)

if content.count(old_import2) == 0:
    print("ERROR: import anchor 2 not found")
else:
    content = content.replace(old_import2, new_import2, 1)

count = content.count(old_block)
if count == 0:
    print("ERROR: old_block not found, file already patched or changed manually")
else:
    content = content.replace(old_block, new_block)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Patched RegisterScreen.kt Login footer ({count} block)")
