package com.muwan.muwanchat.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.MuwanLoginRequest
import com.muwan.muwanchat.network.MuwanRegisterRequest
import com.muwan.muwanchat.network.PhoneSendRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.launch

// ─── DPI Helper ───────────────────────────────────────────────────
@Composable
fun adaptivePadding(): PaddingValues {
    val density = LocalDensity.current.density
    return when {
        density >= 4.0f -> PaddingValues(horizontal = 32.dp, vertical = 24.dp) // xxxhdpi
        density >= 3.0f -> PaddingValues(horizontal = 28.dp, vertical = 20.dp) // xxhdpi
        density >= 2.0f -> PaddingValues(horizontal = 24.dp, vertical = 18.dp) // xhdpi
        density >= 1.5f -> PaddingValues(horizontal = 20.dp, vertical = 16.dp) // hdpi
        else            -> PaddingValues(horizontal = 16.dp, vertical = 14.dp) // mdpi
    }
}

@Composable
fun adaptiveFontSize(base: Float): Float {
    val density = LocalDensity.current.density
    return when {
        density >= 4.0f -> base * 1.1f
        density >= 3.0f -> base * 1.05f
        density >= 2.0f -> base
        density >= 1.5f -> base * 0.95f
        else            -> base * 0.9f
    }
}

@Composable
fun adaptiveButtonHeight(): Float {
    val density = LocalDensity.current.density
    return when {
        density >= 4.0f -> 56f
        density >= 3.0f -> 52f
        density >= 2.0f -> 50f
        else            -> 46f
    }
}

// ─── Login Screen ─────────────────────────────────────────────────
@Composable
fun LoginScreen(navController: NavController) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val padding      = adaptivePadding()
    val btnHeight    = adaptiveButtonHeight()

    var selectedTab     by remember { mutableStateOf(0) } // 0=Email, 1=Phone
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var googleLoading   by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf("") }

    // ─── Google CredentialManager ──────────────────────────────
    suspend fun launchGoogleSignIn() {
        googleLoading = true
        errorMsg = ""
        try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("YOUR_WEB_CLIENT_ID") // Render env mein daalna
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context as Activity
            )

            val credential = result.credential
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken    = googleCred.idToken
            val emailG     = googleCred.id
            val nameG      = googleCred.displayName ?: emailG.substringBefore("@")

            // MuwanDB mein register/login
            // Username = email prefix, password = idToken ka pehla 32 char (fixed)
            val muwanUsername = emailG.substringBefore("@").lowercase()
                .replace(Regex("[^a-z0-9]"), "")
            val muwanPassword = idToken.take(32)
            val muwanDb       = "${muwanUsername}_db"

            // Pehle register try karo
            try {
                val regRes = RetrofitClient.muwanDbApi.register(
                    MuwanRegisterRequest(muwanUsername, muwanPassword, muwanDb)
                )
                if (regRes.isSuccessful && regRes.body()?.anonKey != null) {
                    val body = regRes.body()!!
                    AuthDataStore.saveAuth(
                        context, nameG, emailG,
                        body.anonKey, body.secretKey!!,
                        body.dbName, "google"
                    )
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                    return
                }
            } catch (_: Exception) {}

            // Register fail = already exists, login karo
            val loginRes = RetrofitClient.muwanDbApi.login(
                MuwanLoginRequest(muwanUsername, muwanPassword)
            )
            if (loginRes.isSuccessful && loginRes.body()?.anonKey != null) {
                val body = loginRes.body()!!
                AuthDataStore.saveAuth(
                    context, nameG, emailG,
                    body.anonKey, body.secretKey!!,
                    body.dbName, "google"
                )
                navController.navigate(Screen.Chat.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            } else {
                errorMsg = loginRes.body()?.error ?: "Google login failed"
            }

        } catch (e: GetCredentialException) {
            errorMsg = "Google sign-in cancelled"
        } catch (e: Exception) {
            errorMsg = "Error: ${e.message}"
        }
        googleLoading = false
    }

    // ─── Email + Password → MuwanDB ───────────────────────────
    suspend fun handleEmailLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMsg = "Email and password required"
            return
        }
        isLoading = true
        errorMsg  = ""

        val muwanUsername = email.substringBefore("@").lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        val muwanDb = "${muwanUsername}_db"

        try {
            // Pehle register try
            val regRes = RetrofitClient.muwanDbApi.register(
                MuwanRegisterRequest(muwanUsername, password, muwanDb)
            )
            if (regRes.isSuccessful && regRes.body()?.anonKey != null) {
                val body = regRes.body()!!
                AuthDataStore.saveAuth(
                    context, muwanUsername, email,
                    body.anonKey, body.secretKey!!,
                    body.dbName, "email"
                )
                navController.navigate(Screen.Chat.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
                isLoading = false
                return
            }

            // Already exists → login
            val loginRes = RetrofitClient.muwanDbApi.login(
                MuwanLoginRequest(muwanUsername, password)
            )
            if (loginRes.isSuccessful && loginRes.body()?.anonKey != null) {
                val body = loginRes.body()!!
                AuthDataStore.saveAuth(
                    context, muwanUsername, email,
                    body.anonKey, body.secretKey!!,
                    body.dbName, "email"
                )
                navController.navigate(Screen.Chat.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            } else {
                errorMsg = loginRes.body()?.error ?: "Wrong password"
            }
        } catch (e: Exception) {
            errorMsg = "Network error: ${e.message}"
        }
        isLoading = false
    }

    // ─── Phone OTP ────────────────────────────────────────────
    suspend fun handlePhoneLogin() {
        if (phone.isBlank()) {
            errorMsg = "Phone number required"
            return
        }
        isLoading = true
        errorMsg  = ""
        try {
            val res = RetrofitClient.authApi.phoneSend(
                PhoneSendRequest(phone.trim())
            )
            if (res.isSuccessful && res.body()?.success == true) {
                navController.navigate(Screen.PhoneOTP.createRoute(phone.trim()))
            } else {
                errorMsg = res.body()?.error ?: "Failed to send OTP"
            }
        } catch (e: Exception) {
            errorMsg = "Network error: ${e.message}"
        }
        isLoading = false
    }

    // ─── UI ───────────────────────────────────────────────────
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Logo
            Text(
                "M",
                color = DarkAccent,
                fontSize = adaptiveFontSize(64f).sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "MuwanChat",
                color = Color.White,
                fontSize = adaptiveFontSize(24f).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Connect. Chat. Grow.",
                color = Color(0xFF888888),
                fontSize = adaptiveFontSize(13f).sp
            )

            Spacer(Modifier.height(28.dp))

            // ── Google Button ──────────────────────────────────
            Button(
                onClick = { scope.launch { launchGoogleSignIn() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(btnHeight.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F1F1F)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color(0xFF444444)
                )
            ) {
                if (googleLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Continue with Google",
                        color = Color.White,
                        fontSize = adaptiveFontSize(15f).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF333333)
                )
                Text(
                    "  or  ",
                    color = Color(0xFF666666),
                    fontSize = adaptiveFontSize(12f).sp
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF333333)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Tab Selector ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                listOf("Email", "Phone").forEachIndexed { index, title ->
                    Button(
                        onClick = { selectedTab = index; errorMsg = "" },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == index)
                                DarkAccent else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(
                            title,
                            fontSize = adaptiveFontSize(14f).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Email Tab ──────────────────────────────────────
            if (selectedTab == 0) {

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = "" },
                    label = {
                        Text("Email", color = Color(0xFF888888),
                            fontSize = adaptiveFontSize(14f).sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Email, null, tint = DarkAccent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = DarkAccent,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = DarkAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = "" },
                    label = {
                        Text("Password", color = Color(0xFF888888),
                            fontSize = adaptiveFontSize(14f).sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, null, tint = DarkAccent)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.Visibility
                                else Icons.Filled.VisibilityOff,
                                null, tint = Color(0xFF888888)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = DarkAccent,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = DarkAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "⚠ Pehli baar jo password daalo wahi hamesha use hoga",
                    color = Color(0xFF888888),
                    fontSize = adaptiveFontSize(11f).sp,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        errorMsg,
                        color = Color(0xFFFF5252),
                        fontSize = adaptiveFontSize(13f).sp
                    )
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = { scope.launch { handleEmailLogin() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(btnHeight.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Login / Register",
                            fontSize = adaptiveFontSize(16f).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            // ── Phone Tab ──────────────────────────────────────
            } else {

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = "" },
                    label = {
                        Text("Phone Number", color = Color(0xFF888888),
                            fontSize = adaptiveFontSize(14f).sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Phone, null, tint = DarkAccent)
                    },
                    placeholder = {
             Text("+91XXXXXXXXXX", color = Color(0xFF555555))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = DarkAccent,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = DarkAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        errorMsg,
                        color = Color(0xFFFF5252),
                        fontSize = adaptiveFontSize(13f).sp
                    )
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = { scope.launch { handlePhoneLogin() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(btnHeight.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Send OTP",
                            fontSize = adaptiveFontSize(16f).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "No registration needed — just login directly",
                color = Color(0xFF555555),
                fontSize = adaptiveFontSize(12f).sp
            )
        }
    }
}
