package com.muwan.muwanchat.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val QUICK_EMOJIS = listOf(
    "😀","😂","🥹","😍","🤩","😎","🥳","😭","😤","🤯",
    "❤️","🔥","💯","👍","👏","🙏","💀","😈","🫡","✅",
    "😴","🤔","🫂","🥺","😅","😬","🤣","😇","🤗","😏"
)

@Composable
fun EmojiPickerRow(onEmojiSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0f1428))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QUICK_EMOJIS.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .clickable { onEmojiSelected(emoji) }
                        .padding(4.dp)
                )
            }
        }
    }
}
