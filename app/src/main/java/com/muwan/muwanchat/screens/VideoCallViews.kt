package com.muwan.muwanchat.screens

// VIDEO_P3: video renderers for the call screen
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import com.muwan.muwanchat.calling.ActiveCall
import com.muwan.muwanchat.calling.WebRtcEngine
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

/**
 * One WebRTC renderer. Hands itself to ActiveCall as the video sink and takes
 * itself away again (sink = null) BEFORE it is released, so a frame can never
 * be delivered to a released renderer.
 */
@Composable
fun CallVideoView(
    isLocal: Boolean,
    mirror: Boolean,
    overlay: Boolean,
    roundedCornersDp: Int,
    modifier: Modifier = Modifier
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(WebRtcEngine.eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                // The small preview must be drawn above the full-screen remote surface
                setZOrderMediaOverlay(overlay)
                setMirror(mirror)
                if (roundedCornersDp > 0) {
                    val radiusPx = roundedCornersDp * ctx.resources.displayMetrics.density
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                        }
                    }
                    clipToOutline = true
                }
                renderer = this
                if (isLocal) ActiveCall.setLocalVideoSink(this) else ActiveCall.setRemoteVideoSink(this)
            }
        },
        update = { it.setMirror(mirror) }
    )

    DisposableEffect(Unit) {
        onDispose {
            if (isLocal) ActiveCall.setLocalVideoSink(null) else ActiveCall.setRemoteVideoSink(null)
            try { renderer?.release() } catch (_: Exception) { }
            renderer = null
        }
    }
}

/**
 * Our own camera, as a small draggable window. It stays inside the screen
 * (boundsWidth/boundsHeight are the size of the call screen in pixels).
 */
@Composable
fun DraggableSelfPreview(
    mirror: Boolean,
    boundsWidth: Float,
    boundsHeight: Float,
    topInsetPx: Float,
    bottomInsetPx: Float
) {
    val density = LocalDensity.current
    val widthDp = 108
    val heightDp = 144
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val minY = topInsetPx + with(density) { 8.dp.toPx() } // VIDEO_P5: just below the top bar
    val maxY = (boundsHeight - bottomInsetPx - heightPx - with(density) { 8.dp.toPx() }).coerceAtLeast(minY) // VIDEO_P5
    val maxX = (boundsWidth - widthPx - marginPx).coerceAtLeast(marginPx)

    // Starts at the top right corner
    var position by remember(boundsWidth, boundsHeight) {
        mutableStateOf(Offset(maxX, minY))
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .size(widthDp.dp, heightDp.dp)
            .pointerInput(maxX, maxY, minY) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    position = Offset(
                        (position.x + dragAmount.x).coerceIn(marginPx, maxX),
                        (position.y + dragAmount.y).coerceIn(minY, maxY)
                    )
                }
            }
    ) {
        CallVideoView(
            isLocal = true,
            mirror = mirror,
            overlay = true,
            roundedCornersDp = 14,
            modifier = Modifier.size(widthDp.dp, heightDp.dp)
        )
    }
}
