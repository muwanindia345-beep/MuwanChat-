package com.muwan.muwanchat.data

import androidx.compose.ui.graphics.Color
import com.muwan.muwanchat.DarkBubbleSent

// Animated effect type. NONE = purane static themes (koi change nahi).
enum class BubbleFx {
    NONE, FIRE, RAINBOW, NEON, GOLD,
    AURORA, GALAXY, ELECTRIC, LAVA, MATRIX, SAKURA, CYBER, HOLO
}

// Ek "Message Theme" sirf SENT bubble ka color aur size control karta hai.
// Received bubble ka color hamesha same (DarkBubbleReceived) rehta hai —
// sirf uska size (padding/corner) bhi compact themes ke saath chhota hota hai,
// taaki dono bubbles visually consistent lagein.
data class BubbleTheme(
    val id: String,
    val label: String,
    val sentColor: Color,
    val compact: Boolean,
    val fx: BubbleFx = BubbleFx.NONE // default NONE => purana code as-is chalta hai
) {
    val animated: Boolean get() = fx != BubbleFx.NONE
}

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

    // ---- Animated themes (base color dark rakha hai taaki white text hamesha readable ho) ----
    val FIRE = BubbleTheme(
        id = "fire", label = "Fire", sentColor = Color(0xFFD84315),
        compact = false, fx = BubbleFx.FIRE
    )
    val RAINBOW = BubbleTheme(
        id = "rainbow", label = "Rainbow", sentColor = Color(0xFF1B1B2F),
        compact = false, fx = BubbleFx.RAINBOW
    )
    val NEON = BubbleTheme(
        id = "neon", label = "Neon", sentColor = Color(0xFF062A33),
        compact = false, fx = BubbleFx.NEON
    )
    val GOLD = BubbleTheme(
        id = "gold", label = "Gold", sentColor = Color(0xFF2A2008),
        compact = false, fx = BubbleFx.GOLD
    )

    // ---- Naye animated themes (compact = false => size/position bilkul purane animated jaisa) ----
    val AURORA = BubbleTheme(
        id = "aurora", label = "Aurora", sentColor = Color(0xFF0B1F2A),
        compact = false, fx = BubbleFx.AURORA
    )
    val GALAXY = BubbleTheme(
        id = "galaxy", label = "Galaxy", sentColor = Color(0xFF14093D),
        compact = false, fx = BubbleFx.GALAXY
    )
    val ELECTRIC = BubbleTheme(
        id = "electric", label = "Electric", sentColor = Color(0xFF0E1236),
        compact = false, fx = BubbleFx.ELECTRIC
    )
    val LAVA = BubbleTheme(
        id = "lava", label = "Lava", sentColor = Color(0xFF2B0505),
        compact = false, fx = BubbleFx.LAVA
    )
    val MATRIX = BubbleTheme(
        id = "matrix", label = "Matrix", sentColor = Color(0xFF03140A),
        compact = false, fx = BubbleFx.MATRIX
    )
    val SAKURA = BubbleTheme(
        id = "sakura", label = "Sakura", sentColor = Color(0xFF3A0F24),
        compact = false, fx = BubbleFx.SAKURA
    )
    val CYBER = BubbleTheme(
        id = "cyber", label = "Cyber", sentColor = Color(0xFF12002A),
        compact = false, fx = BubbleFx.CYBER
    )
    val HOLO = BubbleTheme(
        id = "holo", label = "Holo", sentColor = Color(0xFF1A1233),
        compact = false, fx = BubbleFx.HOLO
    )

    val CLASSIC = listOf(ORIGINAL, EMERALD, FOREST)
    val ANIMATED = listOf(
        FIRE, RAINBOW, NEON, GOLD,
        AURORA, GALAXY, ELECTRIC, LAVA, MATRIX, SAKURA, CYBER, HOLO
    )
    val ALL = CLASSIC + ANIMATED

    // Unknown id (e.g. app downgrade) => ORIGINAL, crash nahi.
    fun fromId(id: String?): BubbleTheme = ALL.find { it.id == id } ?: ORIGINAL
}
