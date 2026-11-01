package com.muwan.muwanchat.screens

import android.graphics.SweepGradient
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muwan.muwanchat.data.BubbleFx
import com.muwan.muwanchat.data.BubbleTheme
import kotlin.math.PI
import kotlin.math.sin

// Animated bubble effects. SAFE BY DESIGN: sirf DRAW modifiers (layout/size/padding/gestures
// ko touch nahi karte) => bubble ki position bilkul same rehti hai.
// v2: stronger outer glow (2 layers) + INNER glow (center + edge rim).

@Composable
fun rememberFxTime(enabled: Boolean): State<Long> {
    val context = LocalContext.current
    val reduceMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        } catch (e: Exception) { false }
    }
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled, reduceMotion) {
        if (enabled && !reduceMotion) {
            while (true) {
                androidx.compose.runtime.withFrameNanos { time.longValue = it }
            }
        }
    }
    return time
}

private fun phase(t: Long, periodMs: Int): Float =
    ((t / 1_000_000L) % periodMs).toFloat() / periodMs

private fun wave(t: Long, periodMs: Int, shift: Float = 0f): Float =
    0.5f + 0.5f * sin((2 * PI * phase(t, periodMs) + shift).toFloat())

// 0f..1f pulse (fire = flicker, baaki = smooth breathing)
private fun fxPulse(fx: BubbleFx, t: Long): Float = when (fx) {
    BubbleFx.FIRE -> (wave(t, 700) + wave(t, 430, 1f)) / 2f
    BubbleFx.RAINBOW -> wave(t, 2000)
    BubbleFx.NEON -> wave(t, 1800)
    BubbleFx.GOLD -> wave(t, 2400)
    else -> 0f
}

private fun fxColor(fx: BubbleFx, t: Long): Color = when (fx) {
    BubbleFx.FIRE -> lerp(Color(0xFFFF3D00), Color(0xFFFFB300), fxPulse(fx, t))
    BubbleFx.RAINBOW -> Color.hsv(phase(t, 4000) * 360f, 0.85f, 1f)
    BubbleFx.NEON -> Color(0xFF29F3FF)
    BubbleFx.GOLD -> Color(0xFFF6D365)
    else -> Color.Transparent
}

private fun sweepBrush(colors: IntArray, deg: Float): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val shader = SweepGradient(cx, cy, colors, null)
        val m = android.graphics.Matrix()
        m.postRotate(deg, cx, cy)
        shader.setLocalMatrix(m)
        return shader
    }
}

private val RAINBOW = intArrayOf(
    0xFFFF00FF.toInt(), 0xFF00E5FF.toInt(), 0xFF00FF88.toInt(),
    0xFFFFEA00.toInt(), 0xFFFF6D00.toInt(), 0xFFFF00FF.toInt()
)
private val GOLD_RING = intArrayOf(
    0xFFF6D365.toInt(), 0xFF8A6A12.toInt(), 0xFFFFF1B0.toInt(),
    0xFFC99A1A.toInt(), 0xFFF6D365.toInt()
)

/** Bubble ke BAHAR ka glow (2 layers: wide + tight). `.clip(...)` se PEHLE lagana. */
fun Modifier.bubbleGlow(
    shape: Shape, theme: BubbleTheme, enabled: Boolean, time: State<Long>
): Modifier {
    if (!enabled || !theme.animated) return this
    return drawBehind {
        val t = time.value
        val p = fxPulse(theme.fx, t)
        val glowColor = fxColor(theme.fx, t)
        val radiusDp = when (theme.fx) {
            BubbleFx.FIRE -> 14f + 14f * p
            BubbleFx.RAINBOW -> 20f
            BubbleFx.NEON -> 12f + 14f * p
            BubbleFx.GOLD -> 12f + 12f * p
            else -> return@drawBehind
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        drawIntoCanvas { canvas ->
            // Layer 1: wide, soft
            val wide = Paint()
            wide.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(radiusDp.dp.toPx(), 0f, 0f, glowColor.toArgb())
            }
            canvas.drawOutline(outline, wide)
            // Layer 2: tight, bright (bubble ke kinare ke paas)
            val tight = Paint()
            tight.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer((radiusDp * 0.45f).dp.toPx(), 0f, 0f, glowColor.toArgb())
            }
            canvas.drawOutline(outline, tight)
        }
    }
}

/** Bubble ke ANDAR ka effect: inner glow + flames/ring/shimmer. `.background(...)` ke BAAD lagana. */
fun Modifier.bubbleFill(
    shape: Shape, theme: BubbleTheme, enabled: Boolean, time: State<Long>
): Modifier {
    if (!enabled || !theme.animated) return this
    return drawBehind {
        val t = time.value
        val w = size.width
        val h = size.height
        val p = fxPulse(theme.fx, t)
        val c = fxColor(theme.fx, t)
        val outline = shape.createOutline(size, layoutDirection, this)
        val ring = Stroke(width = 4.dp.toPx()) // aadha clip ho jata hai => ~2dp dikhta hai

        // ---- INNER GLOW (sab themes) ----
        val centerAlpha = (if (theme.fx == BubbleFx.FIRE) 0.14f else 0.30f) + 0.12f * p
        drawRect(
            brush = Brush.radialGradient(
                listOf(c.copy(alpha = centerAlpha), Color.Transparent),
                center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f
            )
        )
        // kinare ke andar soft rim (do layers => smooth fade)
        drawOutline(outline, c.copy(alpha = 0.14f + 0.10f * p), style = Stroke(18.dp.toPx()))
        drawOutline(outline, c.copy(alpha = 0.20f + 0.10f * p), style = Stroke(9.dp.toPx()))

        // ---- Theme-specific ----
        when (theme.fx) {
            BubbleFx.FIRE -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFFFFB300).copy(alpha = 0.30f + 0.25f * p)),
                        startY = h * 0.15f, endY = h
                    )
                )
                val cx = w * (0.1f + 0.8f * wave(t, 2600))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFD54F).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(cx, h), radius = h * 1.1f
                    ),
                    radius = h * 1.1f, center = Offset(cx, h)
                )
            }
            BubbleFx.RAINBOW ->
                drawOutline(outline, sweepBrush(RAINBOW, phase(t, 3000) * 360f), style = ring)
            BubbleFx.NEON ->
                drawOutline(outline, Color(0xFF29F3FF).copy(alpha = 0.75f + 0.25f * p), style = ring)
            BubbleFx.GOLD -> {
                val x = -0.3f * w + 1.6f * w * phase(t, 2600)
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + 0.3f * w, h * 0.6f)
                    )
                )
                drawOutline(outline, sweepBrush(GOLD_RING, phase(t, 5000) * 360f), style = ring)
            }
            else -> Unit
        }
    }
}
