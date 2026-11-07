package com.lamp.os.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange

/**
 * Tap, or long-press then (optionally) drag. Used for desktop icons so one
 * gesture can open the app, show its menu, or move it around the grid.
 *
 * onLongEnd gets the total drag distance: near zero means "show menu".
 */
suspend fun PointerInputScope.iconGestures(
    onTap: () -> Unit,
    onLongStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onLongEnd: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var released = false
        var moved = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    released = true
                    break
                }
                if ((c.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
        }
        if (released) {
            onTap()
            return@awaitEachGesture
        }
        if (moved) return@awaitEachGesture

        onLongStart()
        var total = Offset.Zero
        while (true) {
            val ev = awaitPointerEvent()
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            total += c.positionChange()
            c.consume()
            onDrag(total)
        }
        onLongEnd(total)
    }
}
