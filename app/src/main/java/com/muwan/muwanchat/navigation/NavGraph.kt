package com.muwan.muwanchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.muwan.muwanchat.screens.ChatScreen
import com.muwan.muwanchat.screens.RegisterScreen
import com.muwan.muwanchat.screens.OTPScreen
import com.muwan.muwanchat.screens.LoginScreen
import com.muwan.muwanchat.screens.PhoneOTPScreen
import com.muwan.muwanchat.screens.SplashScreen

sealed class Screen(val route: String) {
    object Splash   : Screen("splash")
    object Login    : Screen("login")
    object PhoneOTP : Screen("phone_otp/{phone}") {
        fun createRoute(phone: String) = "phone_otp/$phone"
    }
    object Chat     : Screen("chat")
    object Register : Screen("register")
    object OTP      : Screen("otp/{email}") {
        fun createRoute(email: String) = "otp/$email"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(navController)
        }
        composable(Screen.PhoneOTP.route) { backStack ->
            val phone = backStack.arguments?.getString("phone") ?: ""
            PhoneOTPScreen(navController, phone)
        }
        composable(Screen.Chat.route) {
            ChatScreen()
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController)
        }
        composable(Screen.OTP.route) { backStack ->
            val email = backStack.arguments?.getString("email") ?: ""
            OTPScreen(navController, email)
        }
    }
}
