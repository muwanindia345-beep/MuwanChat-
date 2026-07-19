package com.muwan.muwanchat.screens

import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.RegisterRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.launch

private val ALLOWED_EMAIL_DOMAINS = setOf("gmail.com", "outlook.com", "hotmail.com", "yahoo.com")
private val USERNAME_REGEX = Regex("^[a-z0-9_.]{3,20}$")
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")

private fun usernameError(u: String): String? {
    if (u.isBlank()) return null
    if (u != u.trim()) return "No leading/trailing spaces"
    if (u.contains(" ")) return "Spaces not allowed"
    if (!USERNAME_REGEX.matches(u)) return "3-20 chars: lowercase letters, numbers, _ . only"
    return null
}

private fun emailError(e: String): String? {
    if (e.isBlank()) return null
    val trimmed = e.trim()
    val match = EMAIL_REGEX.matchEntire(trimmed) ?: return "Invalid email format"
    val domain = trimmed.substringAfter("@").lowercase()
    if (domain !in ALLOWED_EMAIL_DOMAINS) {
        return "Only gmail.com, outlook.com, hotmail.com, yahoo.com allowed"
    }
    return null
}

private fun isStrongPassword(pw: String): Boolean {
    if (pw.length < 8) return false
    var categories = 0
    if (pw.any { it.isDigit() }) categories++
    if (pw.any { it.isUpperCase() }) categories++
    if (pw.any { it.isLowerCase() }) categories++
    if (pw.any { !it.isLetterOrDigit() }) categories++
    return categories >= 3
}

@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val usernameErr = usernameError(username)
    val emailErr = emailError(email)
    val passwordStrong = password.isNotEmpty() && isStrongPassword(password)
    val confirmMismatch = confirmPassword.isNotEmpty() && confirmPassword != password

    fun canSubmit(): Boolean {
        return username.isNotBlank() && usernameErr == null &&
            email.isNotBlank() && emailErr == null &&
            password.length >= 8 &&
            confirmPassword == password
    }

    fun submit() {
        if (username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            errorMsg = "All fields are required"
            return
        }
        if (usernameErr != null) { errorMsg = usernameErr; return }
        if (emailErr != null) { errorMsg = emailErr; return }
        if (password.length < 8) {
            errorMsg = "Password must be at least 8 characters"
            return
        }
        if (confirmPassword != password) {
            errorMsg = "Passwords do not match"
            return
        }
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            try {
                val res = RetrofitClient.authApi.register(
                    RegisterRequest(username.trim(), email.trim(), password)
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    AuthDataStore.saveAuth(
                        context,
                        username  = body.user?.username ?: username.trim(),
                        email     = email.trim(),
                        token     = body.token ?: "",
                        uid       = body.user?.uid ?: "",
                        anonKey   = "",
                        secretKey = "",
                        dbName    = "",
                        loginType = "email"
                    )
                    navController.navigate(Screen.Profile.createRoute("onboarding")) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                } else {
                    errorMsg = res.body()?.error ?: "Registration failed"
                }
            } catch (e: Exception) {
                errorMsg = friendlyErrorMessage(e)
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("M", color = DarkAccent, fontSize = 60.sp, fontWeight = FontWeight.Bold)
            Text("MuwanChat", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Create a new account", color = Color(0xFF888888), fontSize = 14.sp)

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase(); errorMsg = "" },
                label = { Text("Username", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = DarkAccent) },
                isError = usernameErr != null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocusRequester.requestFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
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
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            AnimatedVisibility(visible = usernameErr != null) {
                Text(usernameErr ?: "", color = Color(0xFFFF5555), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = "" },
                label = { Text("Email", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = DarkAccent) },
                isError = emailErr != null,
                modifier = Modifier.fillMaxWidth().focusRequester(emailFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
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
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            AnimatedVisibility(visible = emailErr != null) {
                Text(emailErr ?: "", color = Color(0xFFFF5555), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = "" },
                label = { Text("Password", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = DarkAccent) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF888888)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { confirmPasswordFocusRequester.requestFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
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
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            AnimatedVisibility(visible = password.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = if (passwordStrong) Color(0xFF4CAF50) else Color(0xFFFF5555),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (passwordStrong) "Strong password" else "Weak password (min 8 chars, mix case/number/symbol)",
                        color = if (passwordStrong) Color(0xFF4CAF50) else Color(0xFFFF5555),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMsg = "" },
                label = { Text("Confirm Password", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = DarkAccent) },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF888888)
                        )
                    }
                },
                isError = confirmMismatch,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().focusRequester(confirmPasswordFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
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
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            AnimatedVisibility(visible = confirmMismatch) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF5555),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Passwords do not match", color = Color(0xFFFF5555), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = errorMsg.isNotEmpty()) {
                Text(errorMsg, color = Color.Red, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val registerFooterText = buildAnnotatedString {
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
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
