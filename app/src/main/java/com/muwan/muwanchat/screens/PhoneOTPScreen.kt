package com.muwan.muwanchat.screens

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkInputBg
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.PhoneSendRequest
import com.muwan.muwanchat.network.PhoneVerifyRequest
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneOTPScreen(navController: NavController, phone: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf("") }
    var resendTimer by remember { mutableStateOf(60) }
    var canResend by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (resendTimer > 0) {
            delay(1000)
            resendTimer--
        }
        canResend = true
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkAccent)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Icon(
                Icons.Filled.Phone,
                contentDescription = null,
                tint = DarkAccent,
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Verify Phone", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            Text("OTP sent to", color = Color(0xFF888888), fontSize = 14.sp)
            Text(phone, color = DarkAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = otp,
                onValueChange = {
                    if (it.length <= 6) {
                        otp = it
                        errorMsg = ""
                    }
                },
                label = { Text("Enter OTP", color = Color(0xFF888888)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DarkAccent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    letterSpacing = 8.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = errorMsg.isNotEmpty()) {
                Text(errorMsg, color = Color.Red, fontSize = 13.sp)
            }
            AnimatedVisibility(visible = successMsg.isNotEmpty()) {
                Text(successMsg, color = Color.Green, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (otp.length < 4) {
                        errorMsg = "Please enter valid OTP"
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        try {
                            val res = RetrofitClient.authApi.phoneVerify(
                                PhoneVerifyRequest(phone, otp)
                            )
                            if (res.isSuccessful && res.body()?.success == true) {
                                val body = res.body()!!
                                AuthDataStore.saveAuth(
                                    context,
                                    username  = body.user?.username ?: "",
                                    email     = body.user?.email ?: "",
                                    token     = body.token ?: "",
                                    uid       = body.user?.uid ?: "",
                                    anonKey   = "",
                                    secretKey = "",
                                    dbName    = "",
                                    loginType = "phone"
                                )
                                navController.navigate(Screen.ConversationList.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            } else {
                                errorMsg = res.body()?.error ?: "Invalid OTP"
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
                    Text("Verify OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (canResend) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val res = RetrofitClient.authApi.phoneSend(PhoneSendRequest(phone))
                            if (res.isSuccessful) {
                                successMsg = "OTP resent!"
                                resendTimer = 60
                                canResend = false
                            }
                        } catch (e: Exception) {
                            errorMsg = "Failed to resend OTP"
                        }
                    }
                }) {
                    Text("Resend OTP", color = DarkAccent, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Resend OTP in ${resendTimer}s", color = Color(0xFF888888), fontSize = 13.sp)
            }
        }
    }
}
