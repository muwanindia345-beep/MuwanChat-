package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.autofill.AutofillType
import com.muwan.muwanchat.ui.autofill
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.network.LoginRequest
import com.muwan.muwanchat.network.PhoneSendRequest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun handleLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMsg = "Email and password required"
            return
        }
        focusManager.clearFocus()
        isLoading = true
        errorMsg = ""
        scope.launch {
            try {
                val res = RetrofitClient.authApi.login(LoginRequest(email.trim(), password))
                if (res.isSuccessful && res.body()?.token != null) {
                    val body = res.body()!!
                    AuthDataStore.saveAuth(
                        context,
                        username  = body.user?.username ?: email.substringBefore("@"),
                        email     = email.trim(),
                        token     = body.token ?: "",
                        uid       = body.user?.uid ?: "",
                        anonKey   = "",
                        secretKey = "",
                        dbName    = "",
                        loginType = "email"
                    )
                    navController.navigate(Screen.ConversationList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                } else {
                    errorMsg = res.body()?.error ?: "Login failed"
                }
            } catch (e: Exception) {
                errorMsg = "Error: ${e.message}"
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Text("M", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = DarkAccent)
            Text("MuwanChat", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Welcome back", fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1D2E), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf("Email", "Phone").forEachIndexed { index, label ->
                    Button(
                        onClick = { selectedTab = index; errorMsg = "" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == index) DarkAccent else Color.Transparent,
                            contentColor = Color.White
                        ),
                        elevation = null,
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = "" },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofill(listOf(AutofillType.EmailAddress)) { email = it; errorMsg = "" },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccent,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = DarkAccent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = "" },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofill(listOf(AutofillType.Password)) { password = it; errorMsg = "" },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleLogin() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccent,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = DarkAccent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { handleLogin() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Login", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                PhoneLoginSection(navController = navController)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                Text("Don't have an account? ", color = Color.Gray)
                Text("Register", color = DarkAccent, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun PhoneLoginSection(navController: NavController) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun sendOtp() {
        if (phone.isBlank()) {
            errorMsg = "Phone number required"
            return
        }
        focusManager.clearFocus()
        isLoading = true
        scope.launch {
            try {
                val res = RetrofitClient.authApi.phoneSend(PhoneSendRequest(phone.trim()))
                if (res.isSuccessful) {
                    navController.navigate(Screen.PhoneOTP.createRoute(phone.trim()))
                } else {
                    errorMsg = res.body()?.error ?: "Failed to send OTP"
                }
            } catch (e: Exception) {
                errorMsg = "Error: ${e.message}"
            }
            isLoading = false
        }
    }

    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it; errorMsg = "" },
        label = { Text("Phone Number") },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
        placeholder = { Text("+91XXXXXXXXXX") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { sendOtp() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DarkAccent,
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = DarkAccent
        ),
        shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (errorMsg.isNotEmpty()) {
        Text(errorMsg, color = Color.Red, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { sendOtp() },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
        shape = RoundedCornerShape(14.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text("Send OTP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
