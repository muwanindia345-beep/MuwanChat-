package com.muwan.muwanchat.screens

import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.LoginRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Text("M", color = DarkAccent, fontSize = 60.sp, fontWeight = FontWeight.Bold)
            Text("MuwanChat", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Login karo", color = Color(0xFF888888), fontSize = 14.sp)

            Spacer(modifier = Modifier.height(40.dp))

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = "" },
                label = { Text("Email", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = DarkAccent) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
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
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Error message
            AnimatedVisibility(visible = errorMsg.isNotEmpty()) {
                Text(errorMsg, color = Color.Red, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login button
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMsg = "Email aur password dalo"
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        try {
                            val res = RetrofitClient.authApi.login(
                                LoginRequest(email.trim(), password)
                            )
                            if (res.isSuccessful && res.body()?.success == true) {
                                val body = res.body()!!
                                AuthDataStore.saveAuth(
                                    context,
                                    body.token!!,
                                    body.user!!.username,
                                    body.user.email,
                                    body.user.uid
                                )
                                navController.navigate(Screen.Chat.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            } else {
                                val body = res.body()
                                if (body?.needsVerification == true) {
                                    navController.navigate(Screen.OTP.createRoute(email.trim()))
                                } else {
                                    errorMsg = body?.error ?: "Login failed"
                                }
                            }
                        } catch (e: Exception) {
                            errorMsg = "Network error: ${e.message}"
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Register link
            TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                Text("Account nahi hai? ", color = Color(0xFF888888))
                Text("Register karo", color = DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
