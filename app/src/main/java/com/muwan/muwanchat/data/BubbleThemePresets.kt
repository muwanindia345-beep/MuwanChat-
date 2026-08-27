package com.muwan.muwanchat.data

import androidx.compose.ui.graphics.Color
import com.muwan.muwanchat.DarkBubbleSent

// Ek "Message Theme" sirf SENT bubble ka color aur size control karta hai.
// Received bubble ka color hamesha same (DarkBubbleReceived) rehta hai —
// sirf uska size (padding/corner) bhi compact themes ke saath chhota hota hai,
// taaki dono bubbles visually consistent lagein.
data class BubbleTheme(
    val id: String,
    val label: String,
    val sentColor: Color,
    val compact: Boolean
)

object BubbleThemePresets {
    val ORIGINAL = BubbleTheme(
        id = "original",
        label = "Original",
        sentColor = DarkBubbleSent, // current orange, unchanged
        compact = false
    )
    val EMERALD = BubbleTheme(
        id = "emerald",
        label = "Emerald",
        sentColor = Color(0xFF128C7E),
        compact = true
    )
    val FOREST = BubbleTheme(
        id = "forest",
        label = "Forest",
        sentColor = Color(0xFF075E54),
        compact = true
    )

    val ALL = listOf(ORIGINAL, EMERALD, FOREST)

    fun fromId(id: String?): BubbleTheme = ALL.find { it.id == id } ?: ORIGINAL
}
