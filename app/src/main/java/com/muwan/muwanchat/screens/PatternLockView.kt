package com.muwan.muwanchat.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.muwan.muwanchat.DarkAccent
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PatternErrorColor = Color(0xFFFF3B30)
private val PatternIdleDot = Color(0xFF888888)

/**
 * 3x3 pattern grid. Dots are numbered 0..8 (row-major).
 *
 * - Passing over an unvisited dot picks it up automatically (like the system
 *   lock screen), so 0 -> 2 also selects 1.
 * - After the finger lifts, onPatternComplete is called once, the drawn pattern
 *   stays visible briefly (red if isError), then the grid clears itself.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    onPatternStart: () -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    var fingerPos by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var finished by remember { mutableStateOf(false) }

    val currentEnabled by rememberUpdatedState(enabled)
    val currentIsError by rememberUpdatedState(isError)
    val currentOnStart by rememberUpdatedState(onPatternStart)
    val currentOnComplete by rememberUpdatedState(onPatternComplete)

    // Clear the grid shortly after the pattern is finished.
    LaunchedEffect(finished) {
        if (finished) {
            delay(if (currentIsError) 700L else 350L)
            selected.clear()
            fingerPos = null
            finished = false
        }
    }

    // If the view gets disabled mid-gesture (e.g. lockout starts), reset it.
    LaunchedEffect(enabled) {
        if (!enabled) {
            selected.clear()
            fingerPos = null
        }
    }

    fun center(index: Int, cell: Float) =
        Offset((index % 3 + 0.5f) * cell, (index / 3 + 0.5f) * cell)

    fun nodeAt(pos: Offset): Int? {
        val cell = size.width / 3f
        if (cell <= 0f) return null
        val radius = cell * 0.32f
        for (i in 0..8) {
            if ((pos - center(i, cell)).getDistance() <= radius) return i
        }
        return null
    }

    fun addNode(node: Int) {
        if (node in selected) return
        val last = selected.lastOrNull()
        if (last != null) {
            val dr = node / 3 - last / 3
            val dc = node % 3 - last % 3
            // Straight line over a middle dot (distance 2 on row/col/diagonal)
            if (abs(dr) % 2 == 0 && abs(dc) % 2 == 0) {
                val mid = ((last / 3 + node / 3) / 2) * 3 + (last % 3 + node % 3) / 2
                if (mid !in selected) selected.add(mid)
            }
        }
        selected.add(node)
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentEnabled || finished) return@awaitEachGesture
                    down.consume()
                    selected.clear()
                    currentOnStart()
                    nodeAt(down.position)?.let { addNode(it) }
                    fingerPos = down.position

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        change.consume()
                        if (!change.pressed) break
                        nodeAt(change.position)?.let { addNode(it) }
                        fingerPos = change.position
                    }

                    fingerPos = null
                    if (selected.isNotEmpty()) {
                        finished = true
                        currentOnComplete(selected.toList())
                    }
                }
            }
    ) {
        val cell = this.size.width / 3f
        val color = if (isError) PatternErrorColor else DarkAccent
        val stroke = 6.dp.toPx()

        for (k in 0 until selected.size - 1) {
            drawLine(
                color = color,
                start = center(selected[k], cell),
                end = center(selected[k + 1], cell),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        val tip = fingerPos
        if (tip != null && selected.isNotEmpty()) {
            drawLine(
                color = color.copy(alpha = 0.6f),
                start = center(selected.last(), cell),
                end = tip,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        for (i in 0..8) {
            val c = center(i, cell)
            if (i in selected) {
                drawCircle(color.copy(alpha = 0.2f), radius = cell * 0.28f, center = c)
                drawCircle(color, radius = 9.dp.toPx(), center = c)
            } else {
                drawCircle(PatternIdleDot, radius = 7.dp.toPx(), center = c)
            }
        }
    }
}
