package com.muwan.muwanchat.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import com.muwan.muwanchat.network.PhoneCancelRequest
import com.muwan.muwanchat.network.PhoneSendRequest
import com.muwan.muwanchat.network.PhoneVerifyRequest
import com.muwan.muwanchat.network.RetrofitClient
import com.muwan.muwanchat.util.friendlyErrorMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// PHONE_CALL_V1 -- phone login via call (replaces the old OTP screen).
// Flow: Login -> /auth/phone/send -> this screen places a short call to the server number,
// ends it automatically, and polls /auth/phone/verify until the backend has seen the call.
object PhoneVerifySession {
    @Volatile var phone: String = ""
    @Volatile var sessionId: String = ""
    @Volatile var callNumber: String = ""
    fun set(phone: String, sessionId: String, callNumber: String) {
        this.phone = phone; this.sessionId = sessionId; this.callNumber = callNumber
    }
    fun clear() { phone = ""; sessionId = ""; callNumber = "" }
}

@Suppress("DEPRECATION")
private fun endCallQuietly(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        ) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
        }
    } catch (_: Throwable) {}
}

@Composable
fun PhoneVerifyScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var errorMsg by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }
    var permsDone by remember { mutableStateOf(false) }
    val shownPhone = remember { PhoneVerifySession.phone }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permsDone = true }

    fun cancelAndExit() {
        val sid = PhoneVerifySession.sessionId
        endCallQuietly(context)
        PhoneVerifySession.clear()
        scope.launch {
            withTimeoutOrNull(1500) {
                try {
                    if (sid.isNotBlank()) RetrofitClient.authApi.phoneCancel(PhoneCancelRequest(sid))
                } catch (_: Exception) {}
            }
            navController.popBackStack()
        }
    }

    fun retry() {
        val ph = PhoneVerifySession.phone
        scope.launch {
            errorMsg = ""
            try {
                val res = RetrofitClient.authApi.phoneSend(PhoneSendRequest(ph))
                val b = res.body()
                val sid = b?.sessionId
                val num = b?.callNumber
                if (res.isSuccessful && b?.success == true && !sid.isNullOrBlank() && !num.isNullOrBlank()) {
                    PhoneVerifySession.set(ph, sid, num)
                    attempt++
                } else {
                    errorMsg = b?.error ?: "Verification failed. Please try again."
                }
            } catch (e: Exception) {
                errorMsg = friendlyErrorMessage(e)
            }
        }
    }

    BackHandler { cancelAndExit() }

    // 1) ask for call permissions (CALL_PHONE to place the call, ANSWER_PHONE_CALLS to end it)
    LaunchedEffect(Unit) {
        if (PhoneVerifySession.sessionId.isBlank()) {
            navController.popBackStack()
            return@LaunchedEffect
        }
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 26 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ANSWER_PHONE_CALLS)
        if (needed.isEmpty()) permsDone = true else permLauncher.launch(needed.toTypedArray())
    }

    // 2) place the call, auto-end it, poll the backend until verified
    LaunchedEffect(permsDone, attempt) {
        if (!permsDone) return@LaunchedEffect
        val sid = PhoneVerifySession.sessionId
        val ph = PhoneVerifySession.phone
        val num = PhoneVerifySession.callNumber.filter { it.isDigit() || it == '+' }
        if (sid.isBlank() || num.isBlank()) {
            navController.popBackStack()
            return@LaunchedEffect
        }
        errorMsg = ""
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        hint = if (canCall) "" else "Tap the call button on your dialer, then wait."
        try {
            val intent = Intent(
                if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                Uri.parse("tel:$num")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            errorMsg = "Could not start the call. Please try again."
            return@LaunchedEffect
        }

        val startedAt = System.currentTimeMillis()
        var callEnded = false
        while (true) {
            delay(2000)
            val elapsed = System.currentTimeMillis() - startedAt
            // end the call ourselves after ~8s (ringing is enough for the server to log it)
            if (!callEnded && elapsed >= 8000) {
                endCallQuietly(context)
                callEnded = true
            }
            if (elapsed > 190_000) {
                errorMsg = "Verification timed out. Please try again."
                return@LaunchedEffect
            }
            try {
                val res = RetrofitClient.authApi.phoneVerify(PhoneVerifyRequest(ph, sid))
                val b = res.body()
                if (res.isSuccessful && b?.success == true) {
                    endCallQuietly(context)
                    AuthDataStore.saveAuth(
                        context,
                        username  = b.user?.username ?: "",
                        email     = b.user?.email ?: "",
                        token     = b.token ?: "",
                        uid       = b.user?.uid ?: "",
                        anonKey   = "",
                        secretKey = "",
                        dbName    = "",
                        loginType = "phone"
                    )
                    PhoneVerifySession.clear()
                    if (b.isNewUser == true) {
                        // same path as email registration: Terms & Privacy, then profile setup
                        navController.navigate(Screen.TermsPolicy.route) {
                            popUpTo(Screen.PhoneVerify.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.ConversationList.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                    return@LaunchedEffect
                }
                if (b?.expired == true || (b != null && b.pending != true && !b.error.isNullOrBlank())) {
                    errorMsg = b?.error ?: "Verification failed. Please try again."
                    return@LaunchedEffect
                }
            } catch (_: Exception) {
                // network blip: keep polling until the timeout above
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { cancelAndExit() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Phone,
                    contentDescription = null,
                    tint = DarkAccent,
                    modifier = Modifier.size(60.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Verifying your number", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                Text(shownPhone, color = DarkAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(32.dp))

                if (errorMsg.isEmpty()) {
                    CircularProgressIndicator(color = DarkAccent, modifier = Modifier.size(36.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "We are verifying your number. Please wait, the call will end automatically and your phone number will be verified.",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (hint.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(hint, color = Color(0xFF888888), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(errorMsg, color = Color.Red, fontSize = 14.sp, textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { retry() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Try again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
