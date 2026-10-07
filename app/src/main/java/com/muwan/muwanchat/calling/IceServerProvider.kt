package com.muwan.muwanchat.calling

import android.content.Context
import com.muwan.muwanchat.data.AuthDataStore
import com.muwan.muwanchat.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

/** Backend (/ice) se TURN creds laata hai aur cache karta hai. Fail ho to STUN-only fallback. */
object IceServerProvider {
    private const val REFRESH_MS = 5 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fallback = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    @Volatile private var cached: List<PeerConnection.IceServer>? = null
    @Volatile private var fetchedAt = 0L
    @Volatile private var fetching = false

    fun servers(): List<PeerConnection.IceServer> = cached ?: fallback

    fun prefetch(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (fetching) return
        if (cached != null && System.currentTimeMillis() - fetchedAt < REFRESH_MS) return
        fetching = true
        scope.launch {
            try {
                val token = AuthDataStore.getTokenBlocking(appCtx)
                if (token.isEmpty()) return@launch
                val res = RetrofitClient.appApi.getIceServers("Bearer $token")
                val list = res.iceServers.mapNotNull { dto ->
                    val urls = when (val u = dto.urls) {
                        is String -> listOf(u)
                        is List<*> -> u.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    if (urls.isEmpty()) return@mapNotNull null
                    val b = PeerConnection.IceServer.builder(urls)
                    if (!dto.username.isNullOrEmpty()) b.setUsername(dto.username)
                    if (!dto.credential.isNullOrEmpty()) b.setPassword(dto.credential)
                    b.createIceServer()
                }
                if (list.isNotEmpty()) {
                    cached = list
                    fetchedAt = System.currentTimeMillis()
                    android.util.Log.d("IceServerProvider", "loaded ${list.size} ICE servers, turn=${res.turn}")
                }
            } catch (e: Exception) {
                android.util.Log.w("IceServerProvider", "fetch failed: ${e.message}")
            } finally {
                fetching = false
            }
        }
    }
}
