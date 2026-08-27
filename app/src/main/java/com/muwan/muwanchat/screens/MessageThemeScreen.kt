package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkBg
import com.muwan.muwanchat.DarkBubbleReceived
import com.muwan.muwanchat.DarkHeader
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.BubbleTheme
import com.muwan.muwanchat.data.BubbleThemePresets
import com.muwan.muwanchat.data.ChatBubbleThemeEntity
import com.muwan.muwanchat.data.MuwanChatDb
import kotlinx.coroutines.launch

@Composable
fun MessageThemeScreen(navController: NavController, roomId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context)) }

    val currentEntity by db.chatBubbleThemeDao().observeByRoomId(roomId).collectAsState(initial = null)
    val selectedId = currentEntity?.themeId ?: BubbleThemePresets.ORIGINAL.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding()
    ) {
        // Header — same fixed style/color reused from Wallpaper screen, nothing new
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkHeader)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "Message Theme",
                color = DarkAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        // Scrollable body so it works fine on any screen size
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Choose how messages look in this chat",
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp
            )

            BubbleThemePresets.ALL.forEach { theme ->
                ThemeOptionCard(
                    theme = theme,
                    isSelected = theme.id == selectedId,
                    onClick = {
                        scope.launch {
                            db.chatBubbleThemeDao().upsert(ChatBubbleThemeEntity(roomId, theme.id))
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ThemeOptionCard(theme: BubbleTheme, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111111))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) DarkAccent else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(theme.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = DarkAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Live preview — built from the SAME theme values MessageBubble.kt uses,
        // so this is an accurate preview, not just a static mockup image.
        val corner = if (theme.compact) 14.dp else 18.dp
        val tail = 4.dp
        val hPad = if (theme.compact) 10.dp else 14.dp
        val vPad = if (theme.compact) 7.dp else 10.dp
        val fSize = if (theme.compact) 14.sp else 15.sp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = corner, topEnd = corner,
                                bottomEnd = corner, bottomStart = tail
                            )
                        )
                        .background(DarkBubbleReceived)
                        .padding(horizontal = hPad, vertical = vPad)
                ) {
                    Text("Kya haal hai?", color = Color.White, fontSize = fSize)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = corner, topEnd = corner,
                                bottomEnd = tail, bottomStart = corner
                            )
                        )
                        .background(theme.sentColor)
                        .padding(horizontal = hPad, vertical = vPad)
                ) {
                    Text("Sab badhiya bhai 😄", color = Color.White, fontSize = fSize)
                }
            }
        }
    }
}
