package org.kutner.dofpro.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

/**
 * One gesture handler for all four scales: one finger drags (a marker, or the scale
 * itself), two fingers pan and zoom, and a tap that never moved is reported separately.
 *
 * @param onDown lets the caller decide what this gesture will drag, before it starts.
 * @param onDrag position, delta, and how fast the finger is going in pixels per second.
 *   Speed is measured from the event clock rather than inferred from the size of a delta,
 *   which would make the same physical gesture mean different things on devices that
 *   report pointer movement at different rates.
 * @param onTransform vertical pan and pinch zoom factor, with the centroid to zoom about.
 */
suspend fun PointerInputScope.scaleGestures(
    onDown: (Offset) -> Unit = {},
    onDrag: (Offset, Offset, Float) -> Unit = { _, _, _ -> },
    onTransform: (centroidY: Float, panY: Float, zoom: Float) -> Unit = { _, _, _ -> },
    onTap: (Offset) -> Unit = {},
    onUp: () -> Unit = {},
) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false)
    onDown(first.position)

    var anchor = first.position
    var anchorTime = first.uptimeMillis
    var prevSpread = 0f
    var moved = false
    var wasMulti = false

    while (true) {
        val event = awaitPointerEvent()
        // End the gesture as soon as the finger that started it lifts. Watching only for
        // "nothing is pressed" can miss the release and leave the gesture open, which
        // then swallows later events and drags the value along with them.
        val self = event.changes.firstOrNull { it.id == first.id }
        if (self != null && !self.pressed) break
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break

        if (pressed.size >= 2) {
            var sum = Offset.Zero
            pressed.forEach { sum += it.position }
            val centroid = sum / pressed.size.toFloat()
            var spread = 0f
            pressed.forEach { spread += abs(it.position.y - centroid.y) }
            spread /= pressed.size

            if (wasMulti) {
                val zoom = if (prevSpread > 1f && spread > 1f) spread / prevSpread else 1f
                onTransform(centroid.y, centroid.y - anchor.y, zoom)
            }
            anchor = centroid
            prevSpread = spread
            wasMulti = true
            moved = true
        } else {
            val p = pressed.first()
            if (wasMulti) {
                // Coming back down to one finger: re-anchor instead of jumping.
                wasMulti = false
                anchor = p.position
                anchorTime = p.uptimeMillis
            } else {
                val delta = p.position - anchor
                if (delta.getDistance() > 0f) {
                    val elapsed = (p.uptimeMillis - anchorTime).coerceAtLeast(1L)
                    val speed = delta.getDistance() * 1000f / elapsed
                    onDrag(p.position, delta, speed)
                    if (delta.getDistance() > 3f) moved = true
                }
                anchor = p.position
                anchorTime = p.uptimeMillis
            }
            prevSpread = 0f
        }
        event.changes.forEach { it.consume() }
    }

    if (!moved) onTap(first.position)
    onUp()
}
