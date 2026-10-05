import sys

BASE = "app/src/main/java/com/muwan/muwanchat/"
SCREEN = BASE + "screens/CallHistoryScreen.kt"
DAO = BASE + "data/MessageDao.kt"

# ---------- 1) MessageDao: call messages ki query (pehle se ho to skip) ----------
dao = open(DAO, encoding="utf-8").read()
if "observeCallMessages" not in dao:
    anchor = '''    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    fun observeMessages(roomId: String): Flow<List<MessageEntity>>
'''
    if dao.count(anchor) != 1:
        print("ERROR: MessageDao mein anchor nahi mila. Kuch change nahi kiya.")
        sys.exit(1)
    add = anchor + '''
    // Call History screen: saari 1-1 call bubbles (naye se purane), max 200
    @Query("SELECT * FROM messages WHERE type = 'call' AND deleted = 0 ORDER BY createdAt DESC LIMIT 200")
    fun observeCallMessages(): Flow<List<MessageEntity>>
'''
    open(DAO, "w", encoding="utf-8").write(dao.replace(anchor, add))
    print("MessageDao: observeCallMessages() add ho gaya")
else:
    print("MessageDao: pehle se patched")

# ---------- 2) CallHistoryScreen.kt (Conversation List jaisa look) ----------
old = open(SCREEN, encoding="utf-8").read()
if "CALL_HISTORY_V2" in old:
    print("CallHistoryScreen: v2 pehle se laga hua hai")
    sys.exit(0)
if "fun CallHistoryScreen" not in old:
    print("ERROR: CallHistoryScreen.kt expected jaisi nahi hai. Kuch change nahi kiya.")
    sys.exit(1)

KT = r'''package com.muwan.muwanchat.screens

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
    val avatar: String?
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
                    avatar = conv.avatar
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
                            onClick = {
                                navController.navigate(
                                    Screen.Chat.createRoute(c.uid, c.username, c.roomId)
                                )
                            },
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
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
        Text(
            contact.username,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = DarkAccent)
        }
    }
}
'''

open(SCREEN, "w", encoding="utf-8").write(KT)
print("CallHistoryScreen: v2 (Conversation List style) laga diya")
print("Done. Ab commit + push karo.")
