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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
// v3: Aurora, Galaxy, Electric, Lava, Matrix, Sakura, Cyber, Holo (sab draw-only).

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

private val PI_F = PI.toFloat()

private fun phase(t: Long, periodMs: Int): Float =
    ((t / 1_000_000L) % periodMs).toFloat() / periodMs

private fun wave(t: Long, periodMs: Int, shift: Float = 0f): Float =
    0.5f + 0.5f * sin((2 * PI * phase(t, periodMs) + shift).toFloat())

// Electric: chhoti si double-flash (bijli), baaki time shaant. 1 = flash, 0 = normal.
private fun electricFlash(t: Long): Float {
    val ph = phase(t, 1900)
    return if ((ph in 0.60f..0.64f) || (ph in 0.68f..0.73f)) 1f else 0f
}

// 0f..1f pulse (fire = flicker, baaki = smooth breathing)
private fun fxPulse(fx: BubbleFx, t: Long): Float = when (fx) {
    BubbleFx.FIRE -> (wave(t, 700) + wave(t, 430, 1f)) / 2f
    BubbleFx.RAINBOW -> wave(t, 2000)
    BubbleFx.NEON -> wave(t, 1800)
    BubbleFx.GOLD -> wave(t, 2400)
    BubbleFx.AURORA -> wave(t, 3200)
    BubbleFx.GALAXY -> wave(t, 2600)
    BubbleFx.ELECTRIC -> 0.2f + 0.8f * electricFlash(t)
    BubbleFx.LAVA -> (wave(t, 1800) + wave(t, 1100, 2f)) / 2f
    BubbleFx.MATRIX -> wave(t, 1500)
    BubbleFx.SAKURA -> wave(t, 3000)
    BubbleFx.CYBER -> wave(t, 1200)
    BubbleFx.HOLO -> wave(t, 2400)
    else -> 0f
}

private fun fxColor(fx: BubbleFx, t: Long): Color = when (fx) {
    BubbleFx.FIRE -> lerp(Color(0xFFFF3D00), Color(0xFFFFB300), fxPulse(fx, t))
    BubbleFx.RAINBOW -> Color.hsv(phase(t, 4000) * 360f, 0.85f, 1f)
    BubbleFx.NEON -> Color(0xFF29F3FF)
    BubbleFx.GOLD -> Color(0xFFF6D365)
    BubbleFx.AURORA -> Color.hsv(140f + 130f * wave(t, 6000), 0.75f, 1f)
    BubbleFx.GALAXY -> Color(0xFFB388FF)
    BubbleFx.ELECTRIC -> lerp(Color(0xFFFFE600), Color.White, electricFlash(t))
    BubbleFx.LAVA -> lerp(Color(0xFFFF1744), Color(0xFFFFAB00), fxPulse(fx, t))
    BubbleFx.MATRIX -> Color(0xFF00FF5A)
    BubbleFx.SAKURA -> Color(0xFFFF8AB8)
    BubbleFx.CYBER -> if (phase(t, 1200) < 0.5f) Color(0xFFFF00C8) else Color(0xFF00F0FF)
    BubbleFx.HOLO -> Color.hsv(phase(t, 5000) * 360f, 0.5f, 1f)
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
private val AURORA_RING = intArrayOf(
    0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFB388FF.toInt(),
    0xFF00B0FF.toInt(), 0xFF00E676.toInt()
)
private val HOLO_RING = intArrayOf(
    0xFFFF9BD2.toInt(), 0xFF9BE7FF.toInt(), 0xFFC9FF9B.toInt(),
    0xFFFFE79B.toInt(), 0xFFD29BFF.toInt(), 0xFFFF9BD2.toInt()
)

// Galaxy stars (x, y = bubble ka fraction; period/shift = twinkle)
private val STAR_X = floatArrayOf(0.08f, 0.22f, 0.37f, 0.50f, 0.63f, 0.74f, 0.86f, 0.95f, 0.30f)
private val STAR_Y = floatArrayOf(0.30f, 0.78f, 0.22f, 0.70f, 0.28f, 0.80f, 0.35f, 0.72f, 0.52f)
private val STAR_P = intArrayOf(1300, 1700, 1100, 1900, 1500, 1250, 1800, 1400, 2100)
private val STAR_S = floatArrayOf(0f, 1.1f, 2.0f, 0.6f, 3.1f, 1.7f, 2.6f, 0.3f, 4.0f)

// Sakura petals
private val PETAL_X = floatArrayOf(0.12f, 0.32f, 0.55f, 0.74f, 0.90f)
private val PETAL_MS = intArrayOf(3400, 4100, 3700, 4500, 3900)
private val PETAL_OFF = floatArrayOf(0f, 0.35f, 0.60f, 0.15f, 0.80f)

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
            BubbleFx.AURORA -> 14f + 10f * p
            BubbleFx.GALAXY -> 12f + 10f * p
            BubbleFx.ELECTRIC -> 8f + 22f * p
            BubbleFx.LAVA -> 12f + 16f * p
            BubbleFx.MATRIX -> 10f + 8f * p
            BubbleFx.SAKURA -> 12f + 8f * p
            BubbleFx.CYBER -> 12f
            BubbleFx.HOLO -> 16f
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
            BubbleFx.AURORA -> fxAurora(t, w, h, outline, ring)
            BubbleFx.GALAXY -> fxGalaxy(t, w, h, p, outline, ring)
            BubbleFx.ELECTRIC -> fxElectric(t, w, h, outline, ring)
            BubbleFx.LAVA -> fxLava(t, w, h, p, outline, ring)
            BubbleFx.MATRIX -> fxMatrix(t, w, h, p, outline, ring)
            BubbleFx.SAKURA -> fxSakura(t, w, h, outline, ring)
            BubbleFx.CYBER -> fxCyber(t, w, h, outline, ring)
            BubbleFx.HOLO -> fxHolo(t, w, h, outline, ring)
            else -> Unit
        }
    }
}
// ---------------- Naye effects (sab draw-only, alpha kam taaki text readable rahe) ----------------

private fun DrawScope.fxAurora(t: Long, w: Float, h: Float, outline: Outline, ring: Stroke) {
    val x = -w + 2f * w * phase(t, 5200)
    drawRect(
        brush = Brush.linearGradient(
            listOf(
                Color.Transparent,
                Color(0xFF00E676).copy(alpha = 0.32f),
                Color(0xFF00B0FF).copy(alpha = 0.32f),
                Color(0xFFB388FF).copy(alpha = 0.32f),
                Color.Transparent
            ),
            start = Offset(x, 0f), end = Offset(x + w * 0.9f, h * 0.5f)
        )
    )
    drawOutline(outline, sweepBrush(AURORA_RING, phase(t, 4500) * 360f), style = ring)
}

private fun DrawScope.fxGalaxy(
    t: Long, w: Float, h: Float, p: Float, outline: Outline, ring: Stroke
) {
    for (i in STAR_X.indices) {
        val a = wave(t, STAR_P[i], STAR_S[i])
        drawCircle(
            color = Color.White.copy(alpha = 0.15f + 0.75f * a),
            radius = (0.8f + 0.7f * a).dp.toPx(),
            center = Offset(w * STAR_X[i], h * STAR_Y[i])
        )
    }
    drawOutline(outline, Color(0xFFB388FF).copy(alpha = 0.55f + 0.30f * p), style = ring)
}

private fun DrawScope.fxElectric(t: Long, w: Float, h: Float, outline: Outline, ring: Stroke) {
    val f = electricFlash(t)
    drawOutline(outline, Color(0xFFFFE600).copy(alpha = 0.65f + 0.35f * f), style = ring)
    if (f > 0.5f) {
        val bolt = Path().apply {
            moveTo(w * 0.08f, h * 0.20f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.40f, h * 0.38f)
            lineTo(w * 0.58f, h * 0.80f)
            lineTo(w * 0.72f, h * 0.30f)
            lineTo(w * 0.92f, h * 0.70f)
        }
        drawPath(bolt, Color(0xFF3D7BFF).copy(alpha = 0.45f), style = Stroke(width = 4f.dp.toPx()))
        drawPath(bolt, Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.5f.dp.toPx()))
    }
}

private fun DrawScope.fxLava(
    t: Long, w: Float, h: Float, p: Float, outline: Outline, ring: Stroke
) {
    val x1 = w * (0.15f + 0.70f * wave(t, 3800))
    val y1 = h * (0.25f + 0.60f * wave(t, 2900, 1.5f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFAB00).copy(alpha = 0.50f), Color.Transparent),
            center = Offset(x1, y1), radius = h * 0.9f
        ),
        radius = h * 0.9f, center = Offset(x1, y1)
    )
    val x2 = w * (0.85f - 0.70f * wave(t, 3300, 0.7f))
    val y2 = h * (0.80f - 0.55f * wave(t, 2500, 2.2f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFF1744).copy(alpha = 0.45f), Color.Transparent),
            center = Offset(x2, y2), radius = h * 0.9f
        ),
        radius = h * 0.9f, center = Offset(x2, y2)
    )
    drawOutline(outline, Color(0xFFFF6D00).copy(alpha = 0.60f + 0.30f * p), style = ring)
}

private fun DrawScope.fxMatrix(
    t: Long, w: Float, h: Float, p: Float, outline: Outline, ring: Stroke
) {
    val y = -0.45f * h + 1.5f * h * phase(t, 2200)
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xFF00FF5A).copy(alpha = 0.34f), Color.Transparent),
            startY = y, endY = y + 0.45f * h
        )
    )
    drawOutline(outline, Color(0xFF00FF5A).copy(alpha = 0.70f + 0.30f * p), style = ring)
}

private fun DrawScope.fxSakura(t: Long, w: Float, h: Float, outline: Outline, ring: Stroke) {
    drawOutline(outline, Color(0xFFFF8AB8).copy(alpha = 0.55f), style = ring)
    for (i in PETAL_X.indices) {
        val prog = (phase(t, PETAL_MS[i]) + PETAL_OFF[i]) % 1f
        val x = w * PETAL_X[i] + sin(prog * 4f * PI_F) * 7f.dp.toPx()
        val y = -6f.dp.toPx() + (h + 12f.dp.toPx()) * prog
        val a = sin(prog * PI_F).coerceIn(0f, 1f)
        rotate(degrees = prog * 540f, pivot = Offset(x, y)) {
            drawOval(
                color = Color(0xFFFFC1DA).copy(alpha = 0.85f * a),
                topLeft = Offset(x - 3.5f.dp.toPx(), y - 2f.dp.toPx()),
                size = Size(7f.dp.toPx(), 4f.dp.toPx())
            )
        }
    }
}

private fun DrawScope.fxCyber(t: Long, w: Float, h: Float, outline: Outline, ring: Stroke) {
    val swap = phase(t, 1200) < 0.5f
    val a = if (swap) Color(0xFFFF00C8) else Color(0xFF00F0FF)
    val b = if (swap) Color(0xFF00F0FF) else Color(0xFFFF00C8)
    translate(left = 2f.dp.toPx(), top = 2f.dp.toPx()) {
        drawOutline(outline, a.copy(alpha = 0.75f), style = ring)
    }
    translate(left = -2f.dp.toPx(), top = -2f.dp.toPx()) {
        drawOutline(outline, b.copy(alpha = 0.75f), style = ring)
    }
    // halki scanline
    drawRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = Offset(0f, h * phase(t, 1800)),
        size = Size(w, 1.5f.dp.toPx())
    )
}

private fun DrawScope.fxHolo(t: Long, w: Float, h: Float, outline: Outline, ring: Stroke) {
    val hue = phase(t, 5000) * 360f
    drawRect(
        brush = Brush.linearGradient(
            listOf(
                Color.hsv(hue % 360f, 0.55f, 1f, 0.30f),
                Color.hsv((hue + 70f) % 360f, 0.55f, 1f, 0.30f),
                Color.hsv((hue + 140f) % 360f, 0.55f, 1f, 0.30f),
                Color.hsv((hue + 210f) % 360f, 0.55f, 1f, 0.30f)
            ),
            start = Offset(0f, 0f), end = Offset(w, h)
        )
    )
    drawOutline(outline, sweepBrush(HOLO_RING, phase(t, 6000) * 360f), style = ring)
}
