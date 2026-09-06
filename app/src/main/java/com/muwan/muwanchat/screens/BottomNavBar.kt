package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkSheet
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val navTabs = listOf(
    NavTab(Screen.ConversationList.route, "Chats", Icons.Filled.Chat),
    NavTab(Screen.BroadcastChannels.route, "Broadcast", Icons.Filled.Campaign),
    NavTab(Screen.Status.route, "Status", Icons.Filled.DonutLarge),
    NavTab(Screen.CallHistory.route, "Calls", Icons.Filled.Call)
)

// Beta-only floating bottom nav. Deliberately compact (small icons, tight
// padding) so it doesn't dominate the screen. Chats tab gets a small red
// dot when any conversation has unread messages — same red used for the
// unread badges elsewhere in the app.
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }
    val conversations by db.conversationDao().observeConversations().collectAsState(initial = emptyList())
    val hasUnread = conversations.any { it.unreadCount > 0 }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(DarkSheet)
            .padding(vertical = 9.dp)
    ) {
        navTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !selected) { onNavigate(tab.route) }
                    .padding(vertical = 4.dp)
            ) {
                Box {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) DarkAccent else Color(0xFF888888),
                        modifier = Modifier.size(24.dp)
                    )
                    if (tab.route == Screen.ConversationList.route && hasUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3B30))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tab.label,
                    color = if (selected) DarkAccent else Color(0xFF888888),
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
