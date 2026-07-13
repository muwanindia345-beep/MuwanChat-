package com.muwan.muwanchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.muwan.muwanchat.screens.*

sealed class Screen(val route: String) {
    object Splash          : Screen("splash")
    object Login           : Screen("login")
    object Register        : Screen("register")
    object PhoneOTP        : Screen("phone_otp/{phone}") {
        fun createRoute(phone: String) = "phone_otp/$phone"
    }
    object ConversationList: Screen("conversations")
    object UserSearch      : Screen("user_search")
    object Requests        : Screen("requests")
    object Profile         : Screen("profile/{mode}") {
        fun createRoute(mode: String) = "profile/$mode"
    }
    object AvatarCrop      : Screen("avatar_crop")
    object CreateGroup     : Screen("create_group")
    object AddFromContacts : Screen("add_from_contacts")
    object SearchMembersForGroup : Screen("search_members_for_group")
    object UserProfile     : Screen("user_profile/{uid}") {
        fun createRoute(uid: String) = "user_profile/$uid"
    }
    object Chat            : Screen("chat/{uid}/{username}/{roomId}") {
        fun createRoute(uid: String, username: String, roomId: String) =
            "chat/$uid/$username/$roomId"
    }
    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
    object GroupChat       : Screen("group_chat/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) =
            "group_chat/$groupId/${android.net.Uri.encode(groupName)}"
    }
    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object GroupSettings   : Screen("group_settings/{groupId}") {
        fun createRoute(groupId: String) = "group_settings/$groupId"
    }
    object ApprovalRequests: Screen("approval_requests/{groupId}") {
        fun createRoute(groupId: String) = "approval_requests/$groupId"
    }
    object JoinGroup       : Screen("join/{code}") {
        fun createRoute(code: String) = "join/$code"
    }
    object Settings        : Screen("settings")
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
        composable(Screen.PhoneOTP.route) { back ->
            PhoneOTPScreen(navController, back.arguments?.getString("phone") ?: "")
        }
        composable(Screen.ConversationList.route) { ConversationListScreen(navController) }
        composable(Screen.UserSearch.route) { UserSearchScreen(navController) }
        composable(Screen.Requests.route) { RequestsScreen(navController) }
        composable(Screen.Profile.route) { back ->
            ProfileScreen(navController, back.arguments?.getString("mode") ?: "edit")
        }
        composable(Screen.AvatarCrop.route) { AvatarCropScreen(navController) }
        composable(Screen.CreateGroup.route) { CreateGroupScreen(navController) }
        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }
        composable(Screen.SearchMembersForGroup.route) { SearchMembersForGroupScreen(navController) }
        composable(Screen.UserProfile.route) { back ->
            UserProfileScreen(navController, back.arguments?.getString("uid") ?: "")
        }
        composable(Screen.Chat.route) { back ->
            ChatScreen(
                navController = navController,
                receiverUid = back.arguments?.getString("uid") ?: "",
                receiverUsername = back.arguments?.getString("username") ?: "",
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.Wallpaper.route) { back ->
            WallpaperScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.GroupChat.route) { back ->
            GroupChatScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: "",
                groupName = back.arguments?.getString("groupName") ?: "New Group",
                groupAvatar = null
            )
        }
        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.GroupSettings.route) { back ->
            GroupSettingsScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.ApprovalRequests.route) { back ->
            ApprovalRequestsScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(
            Screen.JoinGroup.route,
            deepLinks = listOf(navDeepLink { uriPattern = "muwanchat://join/{code}" })
        ) { back ->
            JoinGroupScreen(
                navController = navController,
                code = back.arguments?.getString("code") ?: ""
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
