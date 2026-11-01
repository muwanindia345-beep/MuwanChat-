package com.muwan.muwanchat.screens

// AnimatedWallpapers.kt
// Animated chat wallpapers: Code Rain, Aurora, Firefly Night, Synthwave, Sakura Drift, Deep Space.
//
// SAFE BY DESIGN (same idea as BubbleFx.kt): sirf DRAW karta hai. Layout / size / padding /
// gestures ko touch nahi karta, isliye chat UI, bubbles aur text bilkul same rehte hain.
// Stored as ChatWallpaperEntity(type = "animated", value = "<preset id>") -- koi DB migration nahi.
//
// Har scene time `t` (seconds) ka pure function hai (koi per-frame mutable state nahi), isliye
// still thumbnail aur live animation same code se chalte hain.
// Pause hota hai: app background me, battery saver me, aur animator scale 0 (reduced motion) me.

import android.content.Context
import android.graphics.Typeface
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muwan.muwanchat.data.AnimatedPreset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val AW_TAU = 6.2831855f
private const val AW_STILL_TIME = 8.3f
private const val AW_FRAME_STEP_NS = 33_000_000L // ~30 fps, battery friendly

// ───────────────────────── public API ─────────────────────────

/**
 * Animated wallpaper background. Fills the given modifier (default: whole parent).
 * animate = false  -> sirf ek still frame (thumbnails / paused).
 * designW/H > 0    -> scene us design size pe banta hai aur box me "cover" hota hai (thumbnails ke liye).
 */
@Composable
fun AnimatedWallpaper(
    id: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    animate: Boolean = true,
    designW: Float = 0f,
    designH: Float = 0f
) {
    val clock = rememberWallpaperClock(animate)
    key(id) {
        Spacer(
            modifier.drawWithCache {
                val fit = designW > 0f && designH > 0f
                val u = density
                val lw = if (fit) designW else size.width / u
                val lh = if (fit) designH else size.height / u
                val sc = if (fit) max(size.width / designW, size.height / designH) else u
                val ox = if (fit) (size.width - designW * sc) / 2f else 0f
                val oy = if (fit) (size.height - designH * sc) / 2f else 0f
                val scene: AwScene? =
                    if (size.width > 1f && size.height > 1f) awCreateScene(id, lw, lh) else null
                onDrawBehind {
                    val sceneNow = scene ?: return@onDrawBehind
                    val t = if (animate) clock.value else AW_STILL_TIME
                    clipRect {
                        translate(ox, oy) {
                            scale(sc, Offset.Zero) {
                                sceneNow.draw(this, t)
                            }
                        }
                    }
                }
            }
        )
    }
}

/** Swatch content for the "Animated" section in WallpaperScreen: still frame + small name label. */
@Composable
fun AnimatedWallpaperThumb(preset: AnimatedPreset, running: Boolean) {
    Box(Modifier.fillMaxSize()) {
        AnimatedWallpaper(
            id = preset.id,
            modifier = Modifier.fillMaxSize(),
            animate = running,
            designW = 360f,
            designH = 640f
        )
        Text(
            text = preset.name,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x77000000))
                .padding(vertical = 2.dp, horizontal = 2.dp)
        )
    }
}

// ───────────────────────── clock (30fps, lifecycle + battery aware) ─────────────────────────

private fun awMotionReduced(context: Context): Boolean {
    val animOff = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }
    val saver = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    } catch (e: Exception) {
        false
    }
    return animOff || saver
}

@Composable
private fun rememberWallpaperClock(run: Boolean): State<Float> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var started by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) started = true
            else if (event == Lifecycle.Event.ON_STOP) started = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val time = remember { mutableFloatStateOf(AW_STILL_TIME) }
    val allowed = run && started && !awMotionReduced(context)
    LaunchedEffect(allowed) {
        if (!allowed) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = now - last
                if (dt >= AW_FRAME_STEP_NS) {
                    time.floatValue += dt / 1_000_000_000f
                    last = now
                }
            }
        }
    }
    return time
}

// ───────────────────────── helpers ─────────────────────────

private interface AwScene {
    fun draw(s: DrawScope, t: Float)
}

private fun awCreateScene(id: String, w: Float, h: Float): AwScene = when (id) {
    "anim_code_rain" -> AwCodeRainScene(w, h)
    "anim_aurora" -> AwAuroraScene(w, h)
    "anim_fireflies" -> AwFirefliesScene(w, h)
    "anim_synthwave" -> AwSynthwaveScene(w, h)
    "anim_sakura" -> AwSakuraScene(w, h)
    "anim_deep_space" -> AwDeepSpaceScene(w, h)
    else -> AwPlainScene(w, h)
}

private fun awWrap(v: Float, m: Float): Float {
    val r = v % m
    return if (r < 0f) r + m else r
}

private fun awHash(n: Int): Float {
    val v = sin(n * 12.9898f) * 43758.547f
    return v - floor(v)
}

private fun Random.awRange(a: Float, b: Float): Float = a + nextFloat() * (b - a)

private fun awRgb(r: Int, g: Int, b: Int, a: Float): Color = Color(r / 255f, g / 255f, b / 255f, a)

private fun awGlow(center: Offset, radius: Float, r: Int, g: Int, b: Int, a: Float): Brush =
    Brush.radialGradient(
        0f to awRgb(r, g, b, a),
        1f to awRgb(r, g, b, 0f),
        center = center,
        radius = radius
    )

// Unknown id -> calm dark fill (app ke default dark jaisa), crash nahi.
private class AwPlainScene(private val w: Float, private val h: Float) : AwScene {
    override fun draw(s: DrawScope, t: Float) {
        with(s) { drawRect(Color(0xFF1A1A2E), size = Size(w, h)) }
    }
}

// ───────────────────────── 1. Code Rain ─────────────────────────

private class AwCodeRainScene(private val w: Float, private val h: Float) : AwScene {
    private val cell = 15f
    private val cols = ceil(w / cell).toInt()
    private val rows = ceil(h / cell).toInt()
    private val rnd = Random(7)
    private val speed = FloatArray(cols) { rnd.awRange(5f, 16f) }
    private val offset = FloatArray(cols) { rnd.awRange(0f, 60f) }
    private val trail = IntArray(cols) { 8 + rnd.nextInt(14) }
    private val glyphs = "01アイウエオカキクケコサシスセソ{}<>/=+\$#;".map { it.toString() }
    private val bg = Color(0xFF030A06)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = cell * 0.95f
    }

    override fun draw(s: DrawScope, t: Float) {
        with(s) {
            drawRect(bg, size = Size(w, h))
            val canvas = drawContext.canvas.nativeCanvas
            for (i in 0 until cols) {
                val tl = trail[i]
                val period = (rows + tl + 6).toFloat()
                val head = (offset[i] + speed[i] * t) % period
                val headRow = floor(head).toInt()
                val x = i * cell
                for (k in 0 until tl) {
                    val row = headRow - k
                    if (row < 0 || row >= rows) continue
                    val seed = i * 31 + row * 17 + (t * 2f).toInt() * ((i + row) % 3)
                    val g = glyphs[seed % glyphs.size]
                    if (k == 0) {
                        paint.color = 0xFFE2FFE9.toInt()
                    } else {
                        val f = 1f - k / tl.toFloat()
                        val a = (f * f * 215f).toInt().coerceIn(0, 255)
                        paint.color = android.graphics.Color.argb(a, 34, 216, 106)
                    }
                    canvas.drawText(g, x, (row + 1) * cell - 2f, paint)
                }
            }
        }
    }
}

// ───────────────────────── 2. Aurora ─────────────────────────

private class AwAuroraScene(private val w: Float, private val h: Float) : AwScene {
    private class Layer(val y: Float, val amp: Float, val sp: Float, val ph: Float, val hh: Float, val brush: Brush)
    private class Star(val x: Float, val y: Float, val r: Float, val p: Float, val f: Float)

    private fun layer(
        r: Int, g: Int, b: Int, a: Float,
        y: Float, amp: Float, sp: Float, ph: Float, hh: Float
    ): Layer {
        val brush = Brush.verticalGradient(
            0f to awRgb(r, g, b, 0f),
            0.55f to awRgb(r, g, b, a * 0.5f),
            0.82f to awRgb(r, g, b, a),
            1f to awRgb(r, g, b, 0f),
            startY = 0f,
            endY = hh
        )
        return Layer(y, amp, sp, ph, hh, brush)
    }

    private val layers = listOf(
        layer(31, 224, 165, 0.55f, 0.20f, 44f, 0.45f, 0f, 250f),
        layer(123, 77, 255, 0.45f, 0.29f, 54f, 0.33f, 2f, 230f),
        layer(42, 140, 255, 0.40f, 0.37f, 38f, 0.55f, 4f, 210f)
    )
    private val bg = Brush.verticalGradient(
        0f to Color(0xFF030814),
        0.6f to Color(0xFF06142A),
        1f to Color(0xFF02060E),
        startY = 0f,
        endY = h
    )
    private val rnd = Random(3)
    private val stars = List(55) {
        Star(rnd.awRange(0f, w), rnd.awRange(0f, h * 0.65f), rnd.awRange(0.4f, 1.1f), rnd.awRange(0f, AW_TAU), rnd.awRange(0.6f, 1.8f))
    }

    override fun draw(s: DrawScope, t: Float) {
        with(s) {
            drawRect(bg, size = Size(w, h))
            for (st in stars) {
                val a = 0.25f + 0.5f * (0.5f + 0.5f * sin(t * st.f + st.p))
                drawCircle(Color.White, radius = st.r, center = Offset(st.x, st.y), alpha = a)
            }
            for (l in layers) {
                var x = 0f
                while (x < w) {
                    val y = h * l.y +
                        sin(x * 0.012f + t * l.sp + l.ph) * l.amp +
                        sin(x * 0.027f - t * l.sp * 1.6f) * l.amp * 0.4f
                    val a = (0.55f + 0.45f * sin(x * 0.02f + t * 0.7f + l.ph * 1.3f)).coerceIn(0f, 1f)
                    translate(top = y) {
                        drawRect(
                            l.brush,
                            topLeft = Offset(x, 0f),
                            size = Size(4.5f, l.hh),
                            alpha = a,
                            blendMode = BlendMode.Plus
                        )
                    }
                    x += 4f
                }
            }
        }
    }
}

// ───────────────────────── 3. Firefly Night ─────────────────────────

private class AwFirefliesScene(private val w: Float, private val h: Float) : AwScene {
    private class Fly(
        val bx: Float, val by: Float, val a1: Float, val a2: Float,
        val w1: Float, val w2: Float, val p1: Float, val p2: Float,
        val fr: Float, val ph: Float, val size: Float
    )

    private val rnd = Random(21)
    private val bg = Brush.verticalGradient(
        0f to Color(0xFF030C09),
        0.6f to Color(0xFF06180F),
        1f to Color(0xFF02110A),
        startY = 0f,
        endY = h
    )
    private val mist = Brush.verticalGradient(
        0f to awRgb(120, 255, 170, 0f),
        1f to awRgb(120, 255, 170, 0.07f),
        startY = h * 0.6f,
        endY = h
    )
    private val hill1 = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            lineTo(x, h * 0.80f + sin(x * 0.012f + 1f) * 22f + sin(x * 0.031f) * 8f)
            x += 8f
        }
        lineTo(w, h)
        close()
    }
    private val hill2 = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            lineTo(x, h * 0.88f + sin(x * 0.018f + 3f) * 16f)
            x += 8f
        }
        lineTo(w, h)
        close()
    }
    private val pines = Path().apply {
        var x = -10f
        while (x < w + 20f) {
            val ph = rnd.awRange(60f, 150f)
            val pw = rnd.awRange(14f, 22f)
            val by = h * 0.80f + sin(x * 0.012f + 1f) * 22f + 14f
            moveTo(x - pw, by)
            lineTo(x, by - ph)
            lineTo(x + pw, by)
            close()
            x += rnd.awRange(20f, 34f)
        }
    }
    private val flies = List(46) {
        Fly(
            rnd.awRange(0f, w), rnd.awRange(h * 0.25f, h * 0.95f),
            rnd.awRange(20f, 50f), rnd.awRange(8f, 22f),
            rnd.awRange(0.15f, 0.4f), rnd.awRange(0.4f, 0.9f),
            rnd.awRange(0f, AW_TAU), rnd.awRange(0f, AW_TAU),
            rnd.awRange(0.6f, 1.6f), rnd.awRange(0f, AW_TAU),
            rnd.awRange(14f, 30f)
        )
    }

    override fun draw(s: DrawScope, t: Float) {
        with(s) {
            drawRect(bg, size = Size(w, h))
            drawPath(pines, Color(0xFF02080A))
            drawPath(hill1, Color(0xFF03120D))
            drawPath(hill2, Color(0xFF020B08))
            drawRect(mist, topLeft = Offset(0f, h * 0.6f), size = Size(w, h * 0.4f))
            for (f in flies) {
                val sv = sin(t * f.fr + f.ph)
                val a = 0.12f + 0.88f * (if (sv > 0f) sv * sv else 0f)
                if (a < 0.05f) continue
                val x = f.bx + f.a1 * sin(f.w1 * t + f.p1) + f.a2 * sin(f.w2 * t * 1.3f + f.p2)
                val y = (f.by + f.a1 * 0.6f * cos(f.w1 * t * 1.2f + f.p2) + f.a2 * 0.7f * sin(f.w2 * t + f.p1))
                    .coerceIn(h * 0.2f, h)
                val c = Offset(x, y)
                val brush = Brush.radialGradient(
                    0f to Color(0xFFE1FF78),
                    0.25f to Color(0x73BEFF5A),
                    1f to Color(0x00BEFF5A),
                    center = c,
                    radius = f.size / 2f
                )
                drawCircle(brush, radius = f.size / 2f, center = c, alpha = a, blendMode = BlendMode.Plus)
            }
        }
    }
}

// ───────────────────────── 4. Synthwave ─────────────────────────

private class AwSynthwaveScene(private val w: Float, private val h: Float) : AwScene {
    private class Star(val x: Float, val y: Float, val p: Float, val r: Float)

    private val hz = h * 0.5f
    private val sunSize = min(230f, w * 0.64f)
    private val sunTop = hz - sunSize * 0.62f
    private val sky = Brush.verticalGradient(
        0f to Color(0xFF0E0128),
        0.55f to Color(0xFF3B0A62),
        1f to Color(0xFFC42B7C),
        startY = 0f,
        endY = hz
    )
    private val sunBrush = Brush.verticalGradient(
        0f to Color(0xFFFFE65A),
        0.55f to Color(0xFFFF7A3D),
        1f to Color(0xFFFF2F8E),
        startY = sunTop,
        endY = sunTop + sunSize
    )
    private val floor = Brush.verticalGradient(
        0f to Color(0xFF1A0338),
        1f to Color(0xFF05000F),
        startY = hz,
        endY = h
    )
    private val horizon = Brush.verticalGradient(
        0f to awRgb(255, 80, 190, 0f),
        0.5f to awRgb(255, 120, 220, 0.95f),
        1f to awRgb(255, 80, 190, 0f),
        startY = hz - 3f,
        endY = hz + 4f
    )
    private val line = Color(0xFFFF2FD6)
    private val rnd = Random(5)
    private val stars = List(40) {
        Star(rnd.awRange(0f, w), rnd.awRange(0f, hz * 0.7f), rnd.awRange(0f, AW_TAU), rnd.awRange(0.4f, 1f))
    }

    override fun draw(s: DrawScope, t: Float) {
        with(s) {
            drawRect(sky, size = Size(w, hz))
            for (st in stars) {
                val a = 0.2f + 0.5f * (0.5f + 0.5f * sin(t * 1.4f + st.p))
                drawCircle(Color.White, radius = st.r, center = Offset(st.x, st.y), alpha = a)
            }
            // sun, with horizontal slits cut in the sky colour
            drawCircle(
                sunBrush,
                radius = sunSize / 2f - 2f,
                center = Offset(w / 2f, sunTop + sunSize / 2f),
                alpha = 0.92f + 0.08f * sin(t * 1.2f)
            )
            for (i in 0..6) {
                val y = sunTop + sunSize * 0.52f + i * sunSize * 0.065f
                val th = min(2f + i * 1.7f, hz - y)
                if (th > 0f) {
                    drawRect(sky, topLeft = Offset(w / 2f - sunSize / 2f - 2f, y), size = Size(sunSize + 4f, th))
                }
            }
            // floor + moving grid
            drawRect(floor, topLeft = Offset(0f, hz), size = Size(w, h - hz))
            val n = 14
            val off = (t * 0.35f) % 1f
            for (k in 0 until n) {
                val p = (k + off) / n
                val y = hz + (h - hz) * p * p
                drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f, alpha = 0.15f + 0.75f * p)
            }
            for (v in -12..12) {
                drawLine(
                    line,
                    Offset(w / 2f + v * w * 0.012f, hz),
                    Offset(w / 2f + v * w * 0.24f, h),
                    strokeWidth = 1.2f,
                    alpha = 0.5f
                )
            }
            drawRect(horizon, topLeft = Offset(0f, hz - 3f), size = Size(w, 7f))
        }
    }
}

// ───────────────────────── 5. Sakura Drift ─────────────────────────

private class AwSakuraScene(private val w: Float, private val h: Float) : AwScene {
    private class Petal(
        val x0: Float, val y0: Float, val r: Float, val sw: Float, val sp: Float, val ph: Float,
        val rot0: Float, val rs: Float, val fl: Float, val a: Float, val color: Color
    )

    private val rnd = Random(9)
    private val bg = Brush.verticalGradient(
        0f to Color(0xFF150B1F),
        0.55f to Color(0xFF2A1228),
        1f to Color(0xFF3A1A2C),
        startY = 0f,
        endY = h
    )
    private val moonCenter = Offset(w * 0.78f, h * 0.17f)
    private val moonGlow = awGlow(moonCenter, w * 0.4f, 255, 205, 225, 0.5f)
    private val branch = Path().apply {
        moveTo(-10f, h * 0.05f)
        quadraticBezierTo(w * 0.25f, h * 0.16f, w * 0.6f, h * 0.10f)
    }
    private val twig = Path().apply {
        moveTo(w * 0.3f, h * 0.125f)
        quadraticBezierTo(w * 0.4f, h * 0.2f, w * 0.5f, h * 0.2f)
    }
    private val blossoms = listOf(
        Offset(w * 0.12f, h * 0.095f), Offset(w * 0.3f, h * 0.125f), Offset(w * 0.45f, h * 0.14f),
        Offset(w * 0.58f, h * 0.105f), Offset(w * 0.5f, h * 0.2f), Offset(w * 0.38f, h * 0.17f)
    )
    private val pink1 = Color(0xFFFF9EC4)
    private val pink2 = Color(0xFFFFC0D8)
    private val petals = List(46) {
        Petal(
            rnd.awRange(-40f, w + 80f), rnd.awRange(-20f, h), rnd.awRange(5f, 10f),
            rnd.awRange(0.6f, 1.6f), rnd.awRange(22f, 50f), rnd.awRange(0f, AW_TAU),
            rnd.awRange(0f, AW_TAU), rnd.awRange(-1.2f, 1.2f), rnd.awRange(1f, 2.4f),
            rnd.awRange(0.55f, 0.95f), if (rnd.nextBoolean()) pink1 else pink2
        )
    }

    override fun draw(s: DrawScope, t: Float) {
        with(s) {
            drawRect(bg, size = Size(w, h))
            drawCircle(moonGlow, radius = w * 0.4f, center = moonCenter)
            drawCircle(Color(0xEBFFEEF6), radius = 17f, center = moonCenter)
            drawPath(branch, Color(0xFF0D0714), style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawPath(twig, Color(0xFF0D0714), style = Stroke(width = 4f, cap = StrokeCap.Round))
            for ((i, b) in blossoms.withIndex()) {
                val c = Offset(b.x, b.y + sin(t * 0.8f + i) * 1.5f)
                drawCircle(
                    Color(1f, 0.667f, 0.804f, (0.75f + 0.2f * sin(t + i * 2f)).coerceIn(0f, 1f)),
                    radius = 7f,
                    center = c
                )
                drawCircle(Color(0xFFFFE3EE), radius = 2f, center = c)
            }
            for (p in petals) {
                val y = awWrap(p.y0 + p.sp * t, h + 60f) - 30f
                val x = awWrap(p.x0 - 12f * t - (30f / p.sw) * cos(p.sw * t + p.ph), w + 140f) - 70f
                val rx = p.r
                val ry = p.r * (0.55f * abs(cos(t * p.fl + p.ph)) + 0.14f)
                val deg = (p.rot0 + p.rs * t) * 57.29578f
                rotate(deg, Offset(x, y)) {
                    drawOval(
                        p.color,
                        topLeft = Offset(x - rx, y - ry),
                        size = Size(rx * 2f, ry * 2f),
                        alpha = p.a
                    )
                }
            }
        }
    }
}

// ───────────────────────── 6. Deep Space ─────────────────────────

private class AwDeepSpaceScene(private val w: Float, private val h: Float) : AwScene {
    private class Star(val x: Float, val y: Float, val r: Float, val sp: Float, val p: Float, val f: Float)

    private val rnd = Random(11)
    private val bg = Brush.radialGradient(
        0f to Color(0xFF121C4D),
        0.55f to Color(0xFF060A22),
        1f to Color(0xFF02030A),
        center = Offset(w * 0.5f, h * 0.32f),
        radius = h * 0.95f
    )
    private val stars: List<Star> = buildList {
        val defs = listOf(Triple(70, 6f, 0.6f), Triple(45, 12f, 1f), Triple(22, 22f, 1.5f))
        for ((n, sp, r) in defs) {
            repeat(n) {
                add(
                    Star(
                        rnd.awRange(0f, w), rnd.awRange(0f, h), r * rnd.awRange(0.7f, 1.2f), sp,
                        rnd.awRange(0f, AW_TAU), rnd.awRange(0.8f, 2.4f)
                    )
                )
            }
        }
    }

    override fun draw(s: DrawScope, t: Float) {
        with(s) {
            drawRect(bg, size = Size(w, h))
            val n1 = Offset(w * 0.65f + sin(t * 0.12f) * 30f, h * 0.08f + w * 0.55f + cos(t * 0.1f) * 24f)
            drawCircle(awGlow(n1, w * 0.6f, 120, 90, 255, 0.38f), radius = w * 0.6f, center = n1)
            val n2 = Offset(w * 0.3f + cos(t * 0.09f) * 34f, h * 0.5f + w * 0.5f + sin(t * 0.11f) * 26f)
            drawCircle(awGlow(n2, w * 0.55f, 255, 90, 170, 0.24f), radius = w * 0.55f, center = n2)
            for (st in stars) {
                val x = awWrap(st.x - st.sp * 0.5f * t, w + 8f) - 4f
                val y = awWrap(st.y + st.sp * 0.28f * t, h + 8f) - 4f
                val a = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * st.f + st.p))
                drawCircle(Color.White, radius = st.r, center = Offset(x, y), alpha = a)
            }
            // a shooting star every ~4 seconds
            val period = 4.2f
            val e = floor(t / period).toInt()
            val local = t - e * period
            if (local < 0.9f) {
                val x0 = w * (0.4f + 0.65f * awHash(e * 2 + 1))
                val y0 = h * 0.4f * awHash(e * 2 + 2)
                val hx = x0 - 520f * local
                val hy = y0 + 286f * local
                val a = 1f - local / 0.9f
                val head = Offset(hx, hy)
                val tail = Offset(hx + 110f, hy - 60f)
                drawLine(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = a), Color.White.copy(alpha = 0f)),
                        start = head,
                        end = tail
                    ),
                    head,
                    tail,
                    strokeWidth = 1.6f
                )
            }
        }
    }
}
