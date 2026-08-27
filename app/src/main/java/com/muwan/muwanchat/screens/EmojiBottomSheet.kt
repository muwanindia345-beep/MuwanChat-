package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkHeader

private val FULL_EMOJI_SET = listOf(
    "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊","😋","😎","😍","😘","🥰","😗",
    "😙","😚","🙂","🤗","🤩","🤔","🤨","😐","😑","😶","🙄","😏","😣","😥","😮","🤐",
    "😯","😪","😫","🥱","😴","😌","😛","😜","😝","🤤","😒","😓","😔","😕","🙃","🤑",
    "😲","☹️","🙁","😖","😞","😟","😤","😢","😭","😦","😧","😨","😩","🤯","😬","😰",
    "😱","🥵","🥶","😳","🤪","😵","🥴","😠","😡","🤬","😷","🤒","🤕","🤢","🤮","🥳",
    "🥺","🤠","🤡","🤥","🤫","🤭","🧐","🤓","😇","🥹","🫠","🫡","🫢","🫣","🫤","🫥",
    "👍","👎","👏","🙌","🙏","🤝","👊","✊","🤛","🤜","💪","🫶","👋","🤙","✌️","🤟",
    "🤘","👌","🤌","🤏","☝️","👆","👇","👈","👉","✋","🖐️","🖖","👐","🤲","🫱","🫲",
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🔥","❤️‍🩹","💕","💞","💓","💗",
    "💖","💘","💝","💟","💯","💢","💥","💫","💦","💨","🕳️","💣","💬","👁️‍🗨️","🗨️","🗯️",
    "🔥","✨","🎉","🎊","🎁","🎈","🏆","🥇","⭐","🌟","💡","📌","📍","🔔","🔕","📢",
    "🍎","🍕","🍔","🍟","🌭","🍿","🍩","🍪","🎂","🍰","🍫","🍬","🍭","☕","🍵","🥤",
    "⚽","🏀","🏈","⚾","🎾","🏐","🏓","🎮","🎲","🎯","🎵","🎶","🎤","🎧","📷","🎬",
    "🚀","✈️","🚗","🚲","⛵","🌍","🌙","☀️","⛅","🌈","⚡","❄️","🌸","🌹","🌻","🍀"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiBottomSheet(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkHeader
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(FULL_EMOJI_SET) { emoji ->
                Text(
                    emoji,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) }
                        .padding(6.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
