package com.muwan.muwanchat.screens

// CALL_HISTORY_V2
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import com.muwan.muwanchat.data.parseCallInfo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.navigation.Screen

// Call History: Conversation List jaisa hi look -- avatar, naam, aur right side
// pe call button. Ek user ki ek hi row (sabse recent call ke hisaab se upar).
// Koi alag table nahi: chat ki "call" type bubbles se hi list banti hai.
private data class CallContact(
    val roomId: String,
    val uid: String,
    val username: String,
    val avatar: String?,
    val lastStatus: String,   // CALL_HISTORY_LIVE
    val lastCreatedAt: String
)

@Composable
fun CallHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val myUid = remember { AuthDataStore.getUidBlocking(context) }
    val db = remember { MuwanChatDb.get(context, myUid) }

    val callMessages by db.messageDao().observeCallMessages().collectAsState(initial = emptyList())
    val conversations by db.conversationDao().observeConversations().collectAsState(initial = emptyList())

    // callMessages naye se purane order mein aate hain, isliye distinctBy
    // har user ki sabse recent call rakhta hai aur order bhi wahi rehta hai.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000); nowMs = System.currentTimeMillis() }
    }

    val contacts = remember(callMessages, conversations) {
        val byRoom = conversations.filter { !it.isGroup }.associateBy { it.roomId }
        callMessages
            .distinctBy { it.roomId }
            .mapNotNull { m ->
                val conv = byRoom[m.roomId] ?: return@mapNotNull null
                CallContact(
                    roomId = conv.roomId,
                    uid = conv.uid,
                    username = conv.username,
                    avatar = conv.avatar,
                    lastStatus = parseCallInfo(m.content).status,
                    lastCreatedAt = m.createdAt
                )
            }
    }

    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Calls", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }

            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = null,
                            tint = Color(0xFF444466),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No calls yet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Your voice calls will show up here.",
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Bottom padding: beta mein floating nav bar list ko dhak na le
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(contacts, key = { it.roomId }) { c ->
                        CallContactRow(
                            contact = c,
                            nowMs = nowMs,
                            onAvatarClick = {
                                AvatarViewerSelection.set(c.avatar, c.username)
                                navController.navigate(Screen.ViewAvatar.route)
                            },
                            onCall = {
                                navController.navigate(
                                    Screen.Call.createRoute(
                                        uid = c.uid,
                                        username = c.username,
                                        callType = "voice",
                                        isIncoming = false
                                    )
                                )
                            }
                        )
                        HorizontalDivider(color = Color(0xFF1E2040), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// Metrics ConversationRow ke bilkul same: 50dp avatar, 14dp/10dp padding,
// naam Bold 16sp white.
@Composable
private fun CallContactRow(
    contact: CallContact,
    nowMs: Long,
    onAvatarClick: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            avatarBase64 = contact.avatar,
            fallbackText = contact.username,
            size = 50.dp,
            fontSize = 20.sp,
            onClick = onAvatarClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.username,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val createdMs = callCreatedMs(contact.lastCreatedAt)
            val ageMs = if (createdMs != null) nowMs - createdMs else Long.MAX_VALUE
            // 6 ghante se purana "answered" = stale, ongoing nahi maante
            val ongoing = contact.lastStatus == "answered" && ageMs < 6L * 60 * 60 * 1000
            val dateTime = callDateTimeText(contact.lastCreatedAt)
            Spacer(modifier = Modifier.height(2.dp))
            when {
                ongoing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ongoing call", color = Color(0xFF25D366), fontSize = 13.sp)
                }
                contact.lastStatus == "missed" ->
                    Text("Missed call \u00B7 $dateTime", color = Color(0xFFFF5252), fontSize = 13.sp, maxLines = 1)
                else ->
                    Text(dateTime, color = Color(0xFF888888), fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = DarkAccent)
        }
    }
}

// CALL_HISTORY_LIVE: createdAt UTC ISO hota hai -> local "05 Oct, 1:43 PM"
private fun callCreatedMs(raw: String): Long? = try {
    val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    p.timeZone = TimeZone.getTimeZone("UTC")
    p.parse(raw.take(19))?.time
} catch (_: Exception) { null }

private fun callDateTimeText(raw: String): String {
    val ms = callCreatedMs(raw) ?: return raw.take(16).replace("T", " ")
    val f = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
    f.timeZone = TimeZone.getDefault()
    return f.format(java.util.Date(ms))
}
