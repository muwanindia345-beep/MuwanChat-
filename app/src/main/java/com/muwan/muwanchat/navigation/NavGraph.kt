package com.muwan.muwanchat.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import com.muwan.muwanchat.screens.*

sealed class Screen(val route: String) {
    object Splash          : Screen("splash")
    object Login           : Screen("login")
    object Register        : Screen("register")
    object TermsPolicy     : Screen("terms_policy")
    object TermsPrivacy    : Screen("terms_privacy")
    object ApplicationRules : Screen("application_rules")
    object AppLock          : Screen("app_lock")
    object PhoneOTP        : Screen("phone_otp/{phone}") {
        fun createRoute(phone: String) = "phone_otp/$phone"
    }
    object ConversationList: Screen("conversations")
    object BroadcastChannels: Screen("broadcast_channels")
    object Status           : Screen("status")
    object NewStatus        : Screen("new_status")  // STATUS_V1
    object StatusViewer     : Screen("status_viewer/{uid}") {
        fun createRoute(uid: String) = "status_viewer/" + android.net.Uri.encode(uid)
    }
    object CallHistory      : Screen("call_history")
    object UserSearch      : Screen("user_search")
    object Requests        : Screen("requests")
    object Profile         : Screen("profile/{mode}") {
        fun createRoute(mode: String) = "profile/$mode"
    }
    object AvatarCrop      : Screen("avatar_crop")
    object CreateGroup     : Screen("create_group")
    object CreateChannel   : Screen("create_channel")
    object AddFromContacts : Screen("add_from_contacts")
    object AddMembersForChannel : Screen("add_members_for_channel/{channelId}/{channelName}") {
        fun createRoute(channelId: String, channelName: String) =
            "add_members_for_channel/$channelId/${android.net.Uri.encode(channelName)}"
    }
    object SearchMembersForGroup : Screen("search_members_for_group")
    object UserProfile     : Screen("user_profile/{uid}?fromChat={fromChat}") {
        fun createRoute(uid: String, fromChat: Boolean = false) = "user_profile/$uid?fromChat=$fromChat"
    }
    object Chat            : Screen("chat/{uid}/{username}/{roomId}") {
        fun createRoute(uid: String, username: String, roomId: String) =
            "chat/$uid/$username/$roomId"
    }
    object Call             : Screen("call/{uid}/{username}/{callType}/{mode}") {
        fun createRoute(uid: String, username: String, callType: String, isIncoming: Boolean) =
            "call/$uid/${android.net.Uri.encode(username)}/$callType/${if (isIncoming) "incoming" else "outgoing"}"
    }
    object Wallpaper       : Screen("wallpaper/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper/$roomId"
    }
    object WallpaperPreview : Screen("wallpaper_preview/{roomId}") {
        fun createRoute(roomId: String) = "wallpaper_preview/$roomId"
    }
    object MessageTheme    : Screen("message_theme/{roomId}") {
        fun createRoute(roomId: String) = "message_theme/$roomId"
    }
    object GroupChat       : Screen("group_chat/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) =
            "group_chat/$groupId/${android.net.Uri.encode(groupName)}"
    }
    object GroupInfo       : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object ChannelProfile  : Screen("channel_profile/{groupId}") {
        fun createRoute(groupId: String) = "channel_profile/$groupId"
    }
    object EditChannel     : Screen("edit_channel/{groupId}") {
        fun createRoute(groupId: String) = "edit_channel/$groupId"
    }
    object EditGroup       : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: String) = "edit_group/$groupId"
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
    object AccountSettings : Screen("account_settings")
    object AcceptedUsers   : Screen("accepted_users")
    object CheckUpdates    : Screen("check_updates")
    object Forward          : Screen("forward")
    object ViewAvatar       : Screen("view_avatar")
    object Media           : Screen("media/{uid}") {
        fun createRoute(uid: String) = "media/$uid"
    }
}

// Wraps Chats/Broadcast/Status with the floating bottom nav bar — only in the
// beta flavor (BuildConfig.ENABLE_NEW_NAV). In the official app this is a
// pure pass-through: same screen, no extra layer, nothing visually changes.
@Composable
private fun MainTabScaffold(
    navController: androidx.navigation.NavController,
    currentRoute: String,
    content: @Composable () -> Unit
) {
    if (com.muwan.muwanchat.BuildConfig.ENABLE_NEW_NAV) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.ConversationList.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            )
        }
    } else {
        content()
    }
}

@Composable
fun NavGraph(openUpdateScreen: Boolean = false) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    // STEP4B_OPEN_CALL: ongoing-call notification pe tap kiya -- chalti call ki screen kholo.
    // Splash ke upar nahi; splash ke baad route badalne par khulti hai.
    // Call nahi chal rahi ho to bas flag saaf ho jaata hai.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            com.muwan.muwanchat.calling.CallControlEvents.openCallRequest,
            navController.currentBackStackEntryFlow
        ) { requested, entry -> requested to entry.destination.route }.collect { (requested, route) ->
            if (requested && route != null && route != Screen.Splash.route) {
                com.muwan.muwanchat.calling.CallControlEvents.openCallRequest.value = false
                val info = com.muwan.muwanchat.calling.ActiveCall.info.value
                if (info != null && route != Screen.Call.route) {
                    navController.navigate(
                        Screen.Call.createRoute(
                            uid = info.otherUid,
                            username = info.otherUsername,
                            callType = info.callType,
                            isIncoming = info.isIncoming
                        )
                    )
                }
            }
        }
    }

    // STEP2_ACTIVE_CALL: incoming call ActiveCall ne pakdi (RINGING_INCOMING) to CallScreen kholo.
    // Splash ke upar nahi kholte -- splash khatam hone ke baad (route badalne par) khul jaata hai.
    // Ek callId ke liye sirf ek baar, taaki back dabane ke baad dobara na uchhal aaye.
    LaunchedEffect(Unit) {
        var lastOpenedCallId: String? = null
        kotlinx.coroutines.flow.combine(
            com.muwan.muwanchat.calling.ActiveCall.phase,
            navController.currentBackStackEntryFlow
        ) { p, entry -> p to entry.destination.route }.collect { (p, route) ->
            val info = com.muwan.muwanchat.calling.ActiveCall.info.value
            if (p == com.muwan.muwanchat.calling.CallPhase.RINGING_INCOMING &&
                info != null &&
                info.callId != lastOpenedCallId &&
                route != null &&
                route != Screen.Call.route &&
                route != Screen.Splash.route
            ) {
                lastOpenedCallId = info.callId
                navController.navigate(
                    Screen.Call.createRoute(
                        uid = info.otherUid,
                        username = info.otherUsername,
                        callType = info.callType,
                        isIncoming = true
                    )
                )
            }
        }
    }

    // Global incoming-call listener -- app kahin bhi ho (koi bhi screen khuli
    // ho), call_offer aate hi CallScreen "incoming" mode mein khul jaayega.
    // SDP yahan PendingIncomingCall mein rakh dete hain (URL args mein itna
    // bada string safely nahi jaata), CallScreen wahan se turant utha lega.
    LaunchedEffect(Unit) {
        com.muwan.muwanchat.data.AppSocketManager.events.collect { event ->
            if (event is com.muwan.muwanchat.data.SocketEvent.CallOfferReceived) {
                // STEP2_ACTIVE_CALL: incoming offer ab ActiveCall (app-level) handle karta hai --
                // ring, notification aur CallScreen kholna upar wale effect se hota hai.
            } else if (event is com.muwan.muwanchat.data.SocketEvent.CallEndReceived) {
                // Caller ne answer se pehle hi hangup kar diya -- ringing
                // notification ab meaningless hai, hata do.
                com.muwan.muwanchat.calling.CallForegroundService.dismiss(context)
            } else if (event is com.muwan.muwanchat.data.SocketEvent.CallMessageUpdate) {
                // CALL_BUBBLE_PATCH: bubble ka status live update (ringing -> missed/declined/ended)
                try {
                    val myUid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.muwan.muwanchat.data.AuthDataStore.getUidBlocking(context)
                    }
                    if (myUid.isNotBlank()) {
                        val db = com.muwan.muwanchat.data.MuwanChatDb.get(context, myUid)
                        val old = db.messageDao().getById(event.id)
                        db.messageDao().insert(
                            com.muwan.muwanchat.data.MessageEntity(
                                id = event.id,
                                roomId = event.roomId,
                                senderUid = event.senderUid,
                                receiverUid = event.receiverUid,
                                content = event.content,
                                type = "call",
                                seen = old?.seen ?: 0,
                                createdAt = event.createdAt,
                                status = old?.status ?: "SENT",
                                deleted = old?.deleted ?: false
                            )
                        )
                        // CALL_UNREAD_PATCH: missed call (sirf callee ko, chat khula ho to nahi) unread +1.
                        // Count server se bhi aata hai (sync) -- wahan overwrite hota hai, jodta nahi, to double count nahi.
                        val newStatus = try { org.json.JSONObject(event.content).optString("status") } catch (_: Exception) { "" }
                        val oldStatus = try { org.json.JSONObject(old?.content ?: "").optString("status") } catch (_: Exception) { "" }
                        if (newStatus == "missed" && oldStatus != "missed" && event.receiverUid == myUid) {
                            val entry = navController.currentBackStackEntry
                            val openRoom = if (entry?.destination?.route == Screen.Chat.route) {
                                entry?.arguments?.getString("roomId")
                            } else null
                            if (openRoom != event.roomId) db.conversationDao().incrementUnread(event.roomId)
                        }
                        com.muwan.muwanchat.data.ChatRepository.refreshLastMessagePreview(db, event.roomId)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) { SplashScreen(navController) }
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.TermsPolicy.route) { TermsPolicyScreen(navController) }
        composable(Screen.TermsPrivacy.route) { TermsPrivacyScreen(navController) }
        composable(Screen.ApplicationRules.route) { ApplicationRulesScreen(navController) }
        composable(Screen.AppLock.route) { AppLockMethodsScreen(navController) }
        composable(Screen.PhoneOTP.route) { back ->
            PhoneOTPScreen(navController, back.arguments?.getString("phone") ?: "")
        }
        composable(Screen.ConversationList.route) {
            MainTabScaffold(navController, Screen.ConversationList.route) {
                ConversationListScreen(navController)
            }
        }
        composable(Screen.BroadcastChannels.route) {
            MainTabScaffold(navController, Screen.BroadcastChannels.route) {
                BroadcastChannelsScreen(navController)
            }
        }
        composable(Screen.Status.route) {
            MainTabScaffold(navController, Screen.Status.route) {
                StatusScreen(navController)
            }
        }
        composable(Screen.CallHistory.route) {
            MainTabScaffold(navController, Screen.CallHistory.route) {
                CallHistoryScreen(navController)
            }
        }
        composable(Screen.NewStatus.route) { NewStatusScreen(navController) }  // STATUS_V1
        composable(Screen.StatusViewer.route) { back ->
            StatusViewerScreen(navController, back.arguments?.getString("uid") ?: "")
        }
        composable(Screen.UserSearch.route) { UserSearchScreen(navController) }
        composable(Screen.Requests.route) { RequestsScreen(navController) }
        composable(Screen.Profile.route) { back ->
            ProfileScreen(navController, back.arguments?.getString("mode") ?: "edit")
        }
        composable(Screen.AvatarCrop.route) { AvatarCropScreen(navController) }
        composable(Screen.CreateGroup.route) { CreateGroupScreen(navController) }
        composable(Screen.CreateChannel.route) { CreateChannelScreen(navController) }
        composable(Screen.AddFromContacts.route) { AddFromContactsScreen(navController) }
        composable(Screen.AddMembersForChannel.route) { back ->
            AddFromContactsScreen(
                navController = navController,
                channelId = back.arguments?.getString("channelId"),
                channelName = android.net.Uri.decode(back.arguments?.getString("channelName") ?: "")
            )
        }
        composable(Screen.Forward.route) { ForwardScreen(navController) }
        composable(Screen.ViewAvatar.route) { ViewAvatarScreen(navController) }
        composable(Screen.Media.route) { back ->
            MediaScreen(
                navController = navController,
                uid = back.arguments?.getString("uid") ?: ""
            )
        }
        composable(Screen.SearchMembersForGroup.route) { SearchMembersForGroupScreen(navController) }
        composable(
            Screen.UserProfile.route,
            arguments = listOf(navArgument("fromChat") { defaultValue = "false" })
        ) { back ->
            val fromChat = back.arguments?.getString("fromChat")?.toBoolean() ?: false
            UserProfileScreen(
                navController = navController,
                uid = back.arguments?.getString("uid") ?: "",
                fromChat = fromChat
            )
        }
        composable(Screen.Chat.route) { back ->
            ChatScreen(
                navController = navController,
                receiverUid = back.arguments?.getString("uid") ?: "",
                receiverUsername = back.arguments?.getString("username") ?: "",
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.Call.route) { back ->
            CallScreen(
                navController = navController,
                otherUid = back.arguments?.getString("uid") ?: "",
                otherUsername = back.arguments?.getString("username") ?: "",
                callType = back.arguments?.getString("callType") ?: "voice",
                isIncoming = back.arguments?.getString("mode") == "incoming"
            )
        }
        composable(Screen.Wallpaper.route) { back ->
            WallpaperScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.WallpaperPreview.route) { back ->
            WallpaperPreviewScreen(
                navController = navController,
                roomId = back.arguments?.getString("roomId") ?: ""
            )
        }
        composable(Screen.MessageTheme.route) { back ->
            MessageThemeScreen(
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
        composable(Screen.ChannelProfile.route) { back ->
            ChannelProfileScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.GroupInfo.route) { back ->
            GroupInfoScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.EditChannel.route) { back ->
            EditChannelScreen(
                navController = navController,
                groupId = back.arguments?.getString("groupId") ?: ""
            )
        }
        composable(Screen.EditGroup.route) { back ->
            EditGroupScreen(
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
        composable(Screen.AccountSettings.route) { AccountSettingsScreen(navController) }
        composable(Screen.AcceptedUsers.route) { AcceptedUsersScreen(navController) }
        composable(Screen.CheckUpdates.route) { CheckUpdatesScreen(navController) }
    }
    var pendingUpdate by remember { mutableStateOf<com.muwan.muwanchat.network.AppVersionInfo?>(null) }
    var sheetDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Notification tap se aaye hain to seedha Check Updates screen —
        // popup ki zaroorat nahi, wahi screen sab dikha degi.
        if (openUpdateScreen) {
            navController.navigate(Screen.CheckUpdates.route)
            return@LaunchedEffect
        }

        // Ye check sirf tab chalta hai jab app already foreground/open hai,
        // isliye notification bhejne ka yahan koi matlab nahi — user already
        // screen dekh raha hai. Sirf in-app popup dikhega.
        val info = com.muwan.muwanchat.data.UpdateManager.checkForUpdate(context)
        if (info != null && com.muwan.muwanchat.data.UpdateManager.hasUnseenUpdate(context, info)) {
            pendingUpdate = info
        }
    }

    if (pendingUpdate != null && !sheetDismissed) {
        com.muwan.muwanchat.screens.UpdateBottomSheet(
            info = pendingUpdate!!,
            onUpdate = {
                sheetDismissed = true
                navController.navigate(Screen.CheckUpdates.route)
            },
            onCancel = { sheetDismissed = true }
        )
    }
}
