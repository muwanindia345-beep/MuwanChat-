package com.muwan.muwanchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.muwan.muwanchat.screens.*

sealed class Screen(val route: String) {
    object Splash          : Screen("splash")
    object Login           : Screen("login")
    object Register        : Screen("register")
    object OTP             : Screen("otp/{email}") {
        fun createRoute(email: String) = "otp/$email"
    }
    object PhoneOTP        : Screen("phone_otp/{phone}") {
        fun createRoute(phone: String) = "phone_otp/$phone"
    }
    object ConversationList: Screen("conversations")
    object UserSearch      : Screen("user_search")
    object Requests        : Screen("requests")
    object Chat            : Screen("chat/{uid}/{username}/{roomId}") {
        fun createRoute(uid: String, username: String, roomId: String) =
            "chat/$uid/$username/$roomId"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) { SplashScreen(navController) }
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.OTP.route) { back ->
            OTPScreen(navController, back.arguments?.getString("email") ?: "")
        }
        composable(Screen.PhoneOTP.route) { back ->
            PhoneOTPScreen(navController, back.arguments?.getString("phone") ?: "")
        }
        composable(Screen.ConversationList.route) { ConversationListScreen(navController) }
        composable(Screen.UserSearch.route) { UserSearchScreen(navController) }
        composable(Screen.Requests.route) { RequestsScreen(navController) }
        composable(Screen.Chat.route) { back ->
            ChatScreen(
                navController = navController,
                receiverUid = back.arguments?.getString("uid") ?: "",
                receiverUsername = back.arguments?.getString("username") ?: "",
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
    }
}
