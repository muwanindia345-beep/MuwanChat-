package com.muwan.muwanchat.data

// Single source of truth for wallpaper presets.
// "value" stored in ChatWallpaperEntity for color/gradient/pattern is always
// the `id` field below — colors are resolved from these lists at render time.

data class ColorPreset(val id: String, val hex: String)
data class GradientPreset(val id: String, val hexFrom: String, val hexTo: String)
data class PatternPreset(val id: String, val baseHex: String, val dotHex: String)

object WallpaperPresets {

    val colors = listOf(
        ColorPreset("color_1", "#1a1a2e"), // DarkBg default
        ColorPreset("color_2", "#16213e"), // DarkHeader shade
        ColorPreset("color_3", "#0f3460"), // DarkInputBg shade
        ColorPreset("color_4", "#22223b"),
        ColorPreset("color_5", "#2d2d44"),
        ColorPreset("color_6", "#1e1e2f"),
        ColorPreset("color_7", "#26314d"),
        ColorPreset("color_8", "#3a2e39")
    )

    val gradients = listOf(
        GradientPreset("grad_1", "#1a1a2e", "#16213e"),
        GradientPreset("grad_2", "#0f3460", "#1a1a2e"),
        GradientPreset("grad_3", "#2d1e3e", "#1a1a2e"),
        GradientPreset("grad_4", "#1e2a3e", "#0f3460"),
        GradientPreset("grad_5", "#3a2e39", "#1a1a2e"),
        GradientPreset("grad_6", "#16213e", "#22223b")
    )

    val patterns = listOf(
        PatternPreset("pattern_1", "#1a1a2e", "#ff6b3522"), // subtle accent dots
        PatternPreset("pattern_2", "#16213e", "#ffffff14"), // faint white dots
        PatternPreset("pattern_3", "#0f3460", "#ff6b3518"), // faint accent lines
        PatternPreset("pattern_4", "#1e1e2f", "#ffffff10")  // very subtle grid
    )

    fun colorById(id: String) = colors.find { it.id == id }
    fun gradientById(id: String) = gradients.find { it.id == id }
    fun patternById(id: String) = patterns.find { it.id == id }
}
