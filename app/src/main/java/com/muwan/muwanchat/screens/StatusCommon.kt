package com.muwan.muwanchat.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muwan.muwanchat.DarkAccent
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.data.MuwanChatDb
import com.muwan.muwanchat.data.MyProfileEntity
import com.muwan.muwanchat.data.StatusFeed
import com.muwan.muwanchat.data.StatusRepository
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.flow.first

// STATUS_V2 / STATUS_V3 -- Status list, viewer, new-status aur chat list ke beech shared helpers.

val StatusBgColors = listOf("#D85A30", "#534AB7", "#0F6E56", "#185FA5", "#993556", "#854F0B")

const val STATUS_MAX_TEXT = 700

fun parseStatusColor(hex: String?): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex ?: "#534AB7"))
    } catch (_: Exception) {
        Color(0xFF534AB7)
    }
}

// Process-level memory cache: Status tab / chat list dobara khulte hi purana feed TURANT
// dikhta hai (blank ya loading nahi). Phir cache DB aur network chup-chaap update karte hain.
object StatusMemory {
    @Volatile
    var feed: StatusFeed? = null

    @Volatile
    var lastRefreshMs: Long = 0L

    const val REFRESH_EVERY_MS = 60_000L

    // Apna profile pic (base64). Memory mein rakha taaki "My status" par turant dikhe.
    @Volatile
    var myAvatar: String? = null

    @Volatile
    var profileFetched: Boolean = false
}

// Apna avatar: 1) Room cache (my_profile) turant  2) cache mein avatar na ho (Profile screen kabhi
// khuli hi nahi) to ek baar server se laake wahi cache bhar do -- ProfileScreen jaisa hi.
suspend fun loadMyAvatar(context: Context, onAvatar: (String?) -> Unit) {
    val db = MuwanChatDb.get(context, AuthDataStore.getUidBlocking(context))
    val cached = try {
        db.myProfileDao().get()
    } catch (_: Exception) {
        null
    }
    if (cached?.avatar != null) {
        StatusMemory.myAvatar = cached.avatar
        onAvatar(cached.avatar)
        return
    }
    if (StatusMemory.profileFetched) return
    try {
        val token = AuthDataStore.getToken(context).first() ?: return
        val res = RetrofitClient.authApi.me("Bearer $token")
        val user = res.body()?.user
        if (res.isSuccessful && user != null) {
            StatusMemory.profileFetched = true
            db.myProfileDao().upsert(
                MyProfileEntity(
                    name = user.name,
                    bio = user.bio,
                    city = user.city,
                    country = user.country,
                    gender = user.gender,
                    avatar = user.avatar
                )
            )
            StatusMemory.myAvatar = user.avatar
            onAvatar(user.avatar)
        }
    } catch (_: Exception) {
        // offline: initial letter dikhta rahega, agli baar dobara try
    }
}

// 1) Room cache turant  2) network sirf tab jab pichla refresh 60 second se purana ho.
// Offline ho to network step bina kuch kiye khatam (repo.refresh() offline par false deta hai).
suspend fun StatusRepository.loadWithMemory(onUpdate: (StatusFeed) -> Unit) {
    cachedFeed()?.let {
        StatusMemory.feed = it
        onUpdate(it)
    }
    val now = System.currentTimeMillis()
    if (now - StatusMemory.lastRefreshMs >= StatusMemory.REFRESH_EVERY_MS && refresh()) {
        StatusMemory.lastRefreshMs = System.currentTimeMillis()
        cachedFeed()?.let {
            StatusMemory.feed = it
            onUpdate(it)
        }
    }
}

// uid -> har status ka "dekha ya nahi" (chat list ki avatar ring ke liye)
fun StatusFeed.ringMap(): Map<String, List<Boolean>> {
    return users.associate { u -> u.uid to u.items.map { it.seen } }
}

// Avatar ke chaaron taraf tukdon wali ring: har status ka ek hissa.
// seen[i] == true -> grey (dekha hua), false -> orange (naya).
// NOTE: parameter ka naam `diameter` hai (size nahi) kyunki Canvas ke andar
// DrawScope.size se takra jaata.
@Composable
fun StatusRing(
    seen: List<Boolean>,
    diameter: Dp,
    strokeWidth: Dp = 2.5.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = seen.size
            if (n > 0) {
                val sw = strokeWidth.toPx()
                val arcSize = Size(this.size.width - sw, this.size.height - sw)
                val step = 360f / n
                val gap = if (n > 1) 8f else 0f
                for (i in 0 until n) {
                    drawArc(
                        color = if (seen[i]) Color(0xFF555555) else DarkAccent,
                        startAngle = -90f + i * step + gap / 2f,
                        sweepAngle = step - gap,
                        useCenter = false,
                        topLeft = Offset(sw / 2f, sw / 2f),
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Butt)
                    )
                }
            }
        }
        content()
    }
}
