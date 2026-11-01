package com.muwan.muwanchat.screens

import android.provider.Settings
import android.graphics.SweepGradient
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
import androidx.compose.ui.graphics.asFrameworkPaint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muwan.muwanchat.data.BubbleFx
import com.muwan.muwanchat.data.BubbleTheme
import kotlin.math.PI
import kotlin.math.sin

// Animated bubble effects. SAFE BY DESIGN: sirf DRAW modifiers hain (layout, size, padding,
// gestures ko kabhi touch nahi karte), isliye bubble ki position/size bilkul same rehti hai.

/** Frame clock. enabled=false ya "animations off" hone par ruka rehta hai (static look). */
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

// 0f..1f, global time se => saare bubbles ek saath sync rehte hain
private fun phase(t: Long, periodMs: Int): Float =
    ((t / 1_000_000L) % periodMs).toFloat() / periodMs

private fun wave(t: Long, periodMs: Int, shift: Float = 0f): Float =
    0.5f + 0.5f * sin((2 * PI * phase(t, periodMs) + shift).toFloat())

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

/** Bubble ke BAHAR ka glow. `.clip(...)` se PEHLE lagana (clip glow ko kaat deta hai). */
fun Modifier.bubbleGlow(
    shape: Shape, theme: BubbleTheme, enabled: Boolean, time: State<Long>
): Modifier {
    if (!enabled || !theme.animated) return this
    return drawBehind {
        val t = time.value
        val color: Color
        val radiusDp: Float
        when (theme.fx) {
            BubbleFx.FIRE -> {
                val f = (wave(t, 700) + wave(t, 430, 1f)) / 2f
                color = lerp(Color(0xFFFF3D00), Color(0xFFFFB300), f)
                radiusDp = 9f + 9f * f
            }
            BubbleFx.RAINBOW -> {
                color = Color.hsv(phase(t, 4000) * 360f, 0.85f, 1f)
                radiusDp = 13f
            }
            BubbleFx.NEON -> {
                color = Color(0xFF29F3FF)
                radiusDp = 7f + 9f * wave(t, 1800)
            }
            BubbleFx.GOLD -> {
                color = Color(0xFFF6D365)
                radiusDp = 7f + 8f * wave(t, 2400)
            }
            else -> return@drawBehind
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val fp = paint.asFrameworkPaint()
            fp.isAntiAlias = true
            fp.color = android.graphics.Color.TRANSPARENT
            // API 28+ pe glow dikhta hai; purane phones pe bas glow nahi, bubble normal.
            fp.setShadowLayer(radiusDp.dp.toPx(), 0f, 0f, color.toArgb())
            canvas.drawOutline(outline, paint)
        }
    }
}

/** Bubble ke ANDAR ka effect (flames, ring, shimmer). `.background(...)` ke BAAD lagana. */
fun Modifier.bubbleFill(
    shape: Shape, theme: BubbleTheme, enabled: Boolean, time: State<Long>
): Modifier {
    if (!enabled || !theme.animated) return this
    return drawBehind {
        val t = time.value
        val w = size.width
        val h = size.height
        val outline = shape.createOutline(size, layoutDirection, this)
        val ring = Stroke(width = 5.dp.toPx()) // aadha clip ho jata hai => ~2.5dp dikhta hai
        when (theme.fx) {
            BubbleFx.FIRE -> {
                val f = (wave(t, 700) + wave(t, 430, 1f)) / 2f
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFFFFB300).copy(alpha = 0.30f + 0.25f * f)),
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
                drawOutline(
                    outline, Color(0xFF29F3FF).copy(alpha = 0.7f + 0.3f * wave(t, 1800)), style = ring
                )
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
