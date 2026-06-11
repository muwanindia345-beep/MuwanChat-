package com.muwan.muwanchat.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.LoginRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.data.AuthDataStore
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0=Email, 1=Phone
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("M", color = DarkAccent, fontSize = 60.sp, fontWeight = FontWeight.Bold)
            Text("MuwanChat", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Welcome back", color = Color(0xFF888888), fontSize = 14.sp)

            Spacer(modifier = Modifier.height(32.dp))

            // Google button
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://muwan-auth.onrender.com/auth/google"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null,
                    tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue with Google", fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Divider(modifier = Modifier.weight(1f), color = Color(0xFF444444))
                Text("  or  ", color = Color(0xFF888888), fontSize = 13.sp)
                Divider(modifier = Modifier.weight(1f), color = Color(0xFF444444))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf("Email", "Phone").forEachIndexed { index, title ->
                    Button(
                        onClick = { selectedTab = index; errorMsg = "" },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == index) DarkAccent else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                // Email login
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = "" },
                    label = { Text("Email", color = Color(0xFF888888)) },
                    leadingIcon = { Icon(Icons.Filled.Email, null, tint = DarkAccent) },
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

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = "" },
                    label = { Text("Password", color = Color(0xFF888888)) },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, tint = DarkAccent) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                null, tint = Color(0xFF888888)
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

                AnimatedVisibility(visible = errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMsg = "Email and password required"
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

            } else {
                // Phone login
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = "" },
                    label = { Text("Phone Number", color = Color(0xFF888888)) },
                    leadingIcon = { Icon(Icons.Filled.Phone, null, tint = DarkAccent) },
                    placeholder = { Text("+91XXXXXXXXXX", color = Color(0xFF666666)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

                AnimatedVisibility(visible = errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (phone.isBlank()) {
                            errorMsg = "Phone number required"
                            return@Button
                        }
                        scope.launch {
                            isLoading = true
                            try {
                                val res = RetrofitClient.authApi.phoneSend(
                                    com.muwan.muwanchat.network.PhoneSendRequest(phone.trim())
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
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Send OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                Text("Don't have an account? ", color = Color(0xFF888888))
                Text("Register", color = DarkAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
