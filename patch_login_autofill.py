import sys

path = "app/src/main/java/com/muwan/muwanchat/screens/LoginScreen.kt"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. import add karo
old_import = "import androidx.compose.ui.text.input.*"
new_import = """import androidx.compose.ui.text.input.*
import androidx.compose.ui.autofill.AutofillType
import com.muwan.muwanchat.ui.autofill"""

if content.count(old_import) != 1:
    print("ERROR: import anchor mismatch")
    sys.exit(1)
content = content.replace(old_import, new_import, 1)

# 2. email field
old_email = """                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = "" },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),"""
new_email = """                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = "" },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofill(listOf(AutofillType.EmailAddress)) { email = it; errorMsg = "" },"""

if content.count(old_email) != 1:
    print("ERROR: email anchor mismatch")
    sys.exit(1)
content = content.replace(old_email, new_email, 1)

# 3. password field
old_pass = """                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleLogin() }),"""
new_pass = """                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofill(listOf(AutofillType.Password)) { password = it; errorMsg = "" },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleLogin() }),"""

if content.count(old_pass) != 1:
    print("ERROR: password anchor mismatch")
    sys.exit(1)
content = content.replace(old_pass, new_pass, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ LoginScreen.kt me autofill add ho gaya (email + password).")
