package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AppSocketManager
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        notificationsEnabled = AuthDataStore.getNotificationsEnabled(context).first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // 1. Account Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.AccountSettings.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ManageAccounts, contentDescription = "Account Settings", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Account Settings", color = Color.White, fontSize = 16.sp)
                Text(
                    "Password, email, username, delete account",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        // 2. Notifications toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Notifications", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    notificationsEnabled = enabled
                    scope.launch { AuthDataStore.setNotificationsEnabled(context, enabled) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = DarkAccent,
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF2a2a4e)
                )
            )
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Accepted Users
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.AcceptedUsers.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PeopleAlt, contentDescription = "Accepted Users", tint = DarkAccent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Accepted Users", color = Color.White, fontSize = 16.sp)
                Text(
                    "Manage connections, remove permanently",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 3.5 Check Updates
        var hasUpdate by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val info = com.muwan.muwanchat.data.UpdateManager.checkForUpdate(context)
            hasUpdate = info != null && com.muwan.muwanchat.data.UpdateManager.hasUnseenUpdate(context, info)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Screen.CheckUpdates.route) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(Icons.Filled.SystemUpdate, contentDescription = "Check Updates", tint = DarkAccent)
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .background(Color(0xFFFF3B30), shape = androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Check Updates", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF888888))
        }
        Divider(color = Color(0xFF1E2040), thickness = 0.5.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Logout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        AppSocketManager.disconnect()
                        AuthDataStore.clearAuth(context)
                        // Room ab clear nahi karte — har account ki apni alag DB
                        // file hai ("muwanchat_db_<uid>"), toh isi account ka
                        // cache safe hai.
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.ConversationList.route) { inclusive = true }
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Logout, contentDescription = "Logout", tint = Color(0xFFFF3B30))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Logout", color = Color(0xFFFF3B30), fontSize = 16.sp)
        }
    }
}
