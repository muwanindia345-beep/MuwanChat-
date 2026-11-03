package com.muwan.muwanchat.screens

// GOLD_DUST_SPLASH
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.muwan.muwanchat.R
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.sin

private val GoldBg = Color(0xFF050400)
private val GoldMid = Color(0xFF1D1707)
private val Gold = Color(0xFFF6D365)
private val GoldPale = Color(0xFFFFF1B0)
private val GoldDeep = Color(0xFFC99A1A)
private val GoldShade = Color(0xFF7A5A10)

private const val ANIM_MS = 2600
private const val HOLD_MS = 3000L
private const val DUST_COUNT = 14

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current

    // Animation off (Developer options / reduce motion) ho to seedha final frame
    val reduceMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        } catch (e: Exception) { false }
    }
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) progress.animateTo(1f, tween(ANIM_MS, easing = LinearEasing))
    }

    LaunchedEffect(Unit) {
        delay(if (reduceMotion) 800L else HOLD_MS)
        val loggedIn = AuthDataStore.isLoggedIn(context).first()
        if (loggedIn) {
            navController.navigate(Screen.ConversationList.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        } else if (com.muwan.muwanchat.data.GuestStore.isGuestActive(context)) {  // GUEST_P2A
            navController.navigate(Screen.ConversationList.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "goldShimmer")
    val shine by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )

    val p = progress.value
    val landScale = when {
        p < 0.48f -> 0f
        p < 0.58f -> 1.25f * smooth((p - 0.48f) / 0.10f)
        p < 0.68f -> 1.25f - 0.25f * smooth((p - 0.58f) / 0.10f)
        else -> 1f
    }
    val landAlpha = if (p < 0.48f) 0f else 1f
    val nameAlpha = smooth((p - 0.62f) / 0.14f)
    val sweep = ((p - 0.68f) / 0.25f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize().background(GoldBg),
        contentAlignment = Alignment.Center
    ) {
        // Background: halka gold radial glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(GoldMid, GoldBg),
                    center = center,
                    radius = size.maxDimension * 0.7f
                )
            )
        }

        // Gold medallion (app icon) — dust jama hone ke baad land hota hai
        Box(
            modifier = Modifier.size(96.dp).scale(landScale).alpha(landAlpha),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Gold.copy(alpha = 0.45f), Color.Transparent),
                        center = center,
                        radius = size.minDimension
                    ),
                    radius = size.minDimension,
                    center = center
                )
            }
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(GoldShade, Gold, GoldPale, GoldDeep, GoldShade)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(74.dp).clip(CircleShape)
                )
                // Light sweep (ek baar, land hone ke baad)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val x = -0.3f * w + 1.6f * w * sweep
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            start = Offset(x, 0f),
                            end = Offset(x + 0.3f * w, h * 0.6f)
                        )
                    )
                }
            }
        }

        // Naam: gold shimmer
        Text(
            text = "TALKWAVE",
            modifier = Modifier.offset(y = 86.dp).alpha(nameAlpha),
            style = TextStyle(
                brush = Brush.horizontalGradient(
                    colors = listOf(GoldShade, GoldPale, GoldDeep, GoldPale, GoldShade),
                    startX = -500f + 1000f * shine,
                    endX = 500f + 1000f * shine,
                    tileMode = TileMode.Mirror
                ),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
        )

        // Gold dust (center ki taraf aate kan) + flash
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val d = density
            val twoPi = (2.0 * Math.PI).toFloat()
            for (i in 0 until DUST_COUNT) {
                val angle = i * (twoPi / DUST_COUNT) + 0.35f
                val radius = (170f + (i % 3) * 55f) * d
                val delay = (i % 5) * 0.025f
                val t = smooth((p - delay) / 0.46f)
                val dist = radius * (1f - t)
                val pos = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist)
                val a = when {
                    p < 0.05f -> p / 0.05f
                    p > 0.5f -> ((0.58f - p) / 0.08f).coerceIn(0f, 1f)
                    else -> 1f
                }
                if (a > 0f && p < 0.58f) {
                    drawCircle(color = Gold.copy(alpha = 0.25f * a), radius = 9f * d, center = pos)
                    drawCircle(color = GoldPale.copy(alpha = a), radius = 2.6f * d, center = pos)
                }
            }
            if (p > 0.48f && p < 0.85f) {
                val ft = (p - 0.48f) / 0.37f
                val r = (30f + 130f * ft) * d
                val fa = (if (ft < 0.1f) ft / 0.1f else 1f - (ft - 0.1f) / 0.9f)
                    .coerceIn(0f, 1f) * 0.9f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldPale.copy(alpha = fa),
                            Gold.copy(alpha = fa * 0.6f),
                            Color.Transparent
                        ),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            }
        }
    }
}
