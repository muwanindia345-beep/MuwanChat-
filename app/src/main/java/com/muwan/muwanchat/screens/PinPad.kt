package com.muwan.muwanchat.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.DarkSheet

private val KeySize = 68.dp
private val PinErrorRed = Color(0xFFFF3B30)

/** Row of dots showing how many PIN digits have been entered. */
@Composable
fun PinDots(
    filled: Int,
    total: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val color = when {
                isError -> PinErrorRed
                i < filled -> DarkAccent
                else -> Color(0xFF444444)
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** 3x4 numeric keypad: 1-9, blank, 0, backspace. State is held by the caller. */
@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                row.forEach { d -> PinKey(enabled, onClick = { onDigit(d) }) { DigitLabel(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Spacer(Modifier.size(KeySize))
            PinKey(enabled, onClick = { onDigit('0') }) { DigitLabel('0') }
            PinKey(enabled, onClick = onBackspace) {
                Icon(
                    Icons.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun DigitLabel(d: Char) {
    Text(d.toString(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun PinKey(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(DarkSheet)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
