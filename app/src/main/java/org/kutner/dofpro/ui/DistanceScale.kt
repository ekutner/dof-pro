package org.kutner.dofpro.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.kutner.dofpro.model.DistanceFormat
import org.kutner.dofpro.model.DistanceWindow
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.formatSig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Which line on the distance scale a drag is moving. */
/** What a drag on the distance scale took hold of. [SCALE] is the view itself. */
enum class DistanceHandle { SUBJECT, NEAR, FAR, SCALE, NONE }

/**
 * Maps distance to a position on the scale: logarithmic, far end at the top, over
 * whatever stretch of distance the window currently covers.
 */
class DistanceAxis(private val window: DistanceWindow) {
    private val lnLo = ln(window.lo)
    private val lnHi = ln(window.hi)

    /** Fraction down the scale, 0 at the far end and 1 at the near end. */
    fun fractionOf(distance: Double): Double {
        if (distance.isInfinite()) return 0.0
        val d = distance.coerceIn(window.lo, window.hi)
        return 1.0 - (ln(d) - lnLo) / (lnHi - lnLo)
    }

    /** The distance at a fraction down the scale. */
    fun distanceAt(fraction: Double): Double =
        exp(lnLo + (1.0 - fraction.coerceIn(0.0, 1.0)) * (lnHi - lnLo))

    /** How much of the scale is in view, in natural logs. */
    val lnSpan: Double get() = lnHi - lnLo

    /**
     * How far a drag has carried its value, in natural logs, given how far the finger has
     * travelled since the gesture began.
     *
     * The rate is not constant. Over the first stretch it is the visible scale's own
     * rate, so the graduations keep pace with the finger and a distance can be placed
     * precisely — which matters most up close, where a shallow depth of field closes the
     * whole view down to a few millimetres. The further the finger travels the faster it
     * runs, until one long drag crosses from arm's length to infinity.
     *
     * Two properties make it behave. The curve is cubic in the travelled distance, so
     * near zero it is indistinguishable from the plain rate — the first tenth of a screen
     * runs within about 5% of it — rather than accelerating out from under the finger
     * immediately. And it is a function of total travel rather than a running sum of
     * rates, so a drag out and back returns to exactly where it started.
     */
    fun travelToLn(travelled: Float, height: Float): Double {
        if (height <= 0f) return 0.0
        val fine = lnSpan / height
        // Never slower than the view, however wide the view already is.
        val coarse = maxOf(COARSE_SPAN / height, fine)
        val reach = height.toDouble()
        val distance = abs(travelled).toDouble()
        val carried = if (distance <= reach) {
            fine * distance + (coarse - fine) * distance * distance * distance /
                (3.0 * reach * reach)
        } else {
            // Beyond a full screen of travel it simply continues at the coarse rate; the
            // pieces meet in both value and slope.
            fine * reach + (coarse - fine) * reach / 3.0 + coarse * (distance - reach)
        }
        return if (travelled < 0f) -carried else carried
    }

    /**
     * The rate at this point in a drag: how much distance one more pixel covers, in
     * natural logs. This is [travelToLn] differentiated, so integrating it over a drag
     * reproduces that curve exactly — and going through the rate rather than the total
     * is what lets speed have a say as well.
     */
    fun slopeAt(travelled: Float, height: Float): Double {
        if (height <= 0f) return 0.0
        val fine = lnSpan / height
        val coarse = maxOf(COARSE_SPAN / height, fine)
        val reach = height.toDouble()
        val distance = abs(travelled).toDouble()
        return if (distance <= reach) {
            fine + (coarse - fine) * distance * distance / (reach * reach)
        } else {
            coarse
        }
    }

    companion object {
        /** How much ground a full screen of travel covers once fully up to speed. */
        private val COARSE_SPAN = ln(1000.0)

        /** A brisk drag, in pixels per second. */
        private const val BRISK = 1400f

        /** The most a flick may multiply the rate by. */
        private const val MOST = 3.0

        /**
         * How much faster the scale runs for a finger moving at [speed].
         *
         * Travel alone decides the rate for a slow, deliberate drag, which is what keeps
         * fine placement possible. Speed is the second half of it: a flick is a request to
         * cover ground, and answering it means a long distance is a flick away rather than
         * several deliberate drags.
         */
        fun speedBoost(speed: Float): Double =
            1.0 + (abs(speed) / BRISK).toDouble().coerceAtMost(MOST)
    }
}

/**
 * A drag in progress. The value is carried here unsnapped and stepped along by each
 * movement, rather than read off the finger's absolute position, because the scale
 * re-scales continuously underneath the drag — an absolute reading would be measured
 * against a mapping that had already moved, and the marker would chase its own tail.
 */
private class DistanceGrab {
    var handle = DistanceHandle.NONE

    /** What the dragged line reads now, carried unrounded through the gesture. */
    var value = 0.0

    /** How far the finger has travelled since it went down. Signed, down positive. */
    var travelled = 0f

    fun start(handle: DistanceHandle, value: Double) {
        this.handle = handle
        this.value = value
        this.travelled = 0f
    }
}

/**
 * Everything the gesture handler needs, refreshed every recomposition without restarting
 * the in-flight gesture — otherwise dragging a marker would cancel itself the moment it
 * changed the value it is dragging.
 */
private class Interaction(
    val axis: DistanceAxis,
    val subjectRange: ClosedFloatingPointRange<Double>,
    val subject: Double,
    val near: Double?,
    val far: Double?,
    val onSubject: (Double) -> Unit,
    val onNear: (Double) -> Unit,
    val onFar: (Double) -> Unit,
    val onSubjectAnchor: (Double) -> Unit,
    val onHoldSpan: (Double) -> Unit,
    val onZoom: (Double, Double) -> Unit,
    val onSettle: () -> Unit,
)

/**
 * The distance scale. Distances read down the right side, blur amounts down the left, and
 * the double cone between them shows combined focus and diffraction blur.
 *
 * Four markers sit on it: a blue line for the subject, which only the user moves; two red
 * lines for the edges of the acceptable depth of field, which the user can drag to choose
 * an aperture instead; and a green line at the hyperfocal distance, which is only ever
 * calculated.
 */
@Composable
fun DistanceScale(
    window: DistanceWindow,
    format: DistanceFormat,
    subject: Double,
    near: Double?,
    far: Double?,
    hyperfocal: Double,
    subjectBlurLabel: String?,
    blurTicks: List<Pair<Double, Double>>,
    /** The blur reading at the depth of field limits — the camera's allowable blur. */
    sharpBlur: Double,
    coneKey: Any,
    blurAt: (Double) -> Double,
    fontPx: Float,
    modifier: Modifier = Modifier,
    /**
     * Where to focus each frame of a focus stack, empty when not stacking. When there is
     * a stack, the cone, the blur graduations and the red limits all describe one frame
     * rather than the set, so they stand down and this takes the scale.
     */
    stackPoints: List<Double> = emptyList(),
    subjectRange: ClosedFloatingPointRange<Double> =
        DofState.MIN_DISTANCE..DofState.MAX_DISTANCE,
    onSubjectChange: (Double) -> Unit = {},
    onNearChange: (Double) -> Unit = {},
    onFarChange: (Double) -> Unit = {},
    onSubjectAnchorChange: (Double) -> Unit = {},
    /** Take the span as it stands, so what follows moves markers rather than the scale. */
    onHoldSpan: (Double) -> Unit = {},
    /** Pinch: above 1 is fingers spreading, which zooms in. */
    onZoom: (Double, Double) -> Unit = { _, _ -> },
    onSettle: () -> Unit = {},
) {
    val axis = remember(window) { DistanceAxis(window) }
    val rightPaint = remember(fontPx) { textPaint(fontPx, Paint.Align.LEFT) }
    val leftPaint = remember(fontPx) { textPaint(fontPx, Paint.Align.RIGHT) }

    // Palette.dark is a key because the cone is pixels, not a draw call: its colours are
    // baked in when it is built and would otherwise survive a change of theme.
    val cone = remember(coneKey, window, Palette.dark) {
        buildCone(
            width = 96,
            height = 420,
            distanceAt = { fraction -> axis.distanceAt(fraction) },
            blurAt = blurAt,
        )
    }

    val latest by rememberUpdatedState(
        Interaction(
            axis, subjectRange, subject, near, far,
            onSubjectChange, onNearChange, onFarChange, onSubjectAnchorChange,
            onHoldSpan, onZoom, onSettle,
        )
    )
    val grab = remember { DistanceGrab() }

    // Fractions of the column width: blur gutter | cone | distance gutter.
    val leftAxisFrac = 0.30f
    val rightAxisFrac = 0.68f

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            // A drag takes hold of whichever line it landed on, and of the view itself
            // if it landed on none of them. The scale used to be divided into zones
            // instead — subject in the middle, near below, far above — so that a shallow
            // depth of field, which can put all three lines within a fingertip of each
            // other, still let you drag a limit. The cost was that a finger anywhere on
            // the column moved something, and there was no way to slide the view without
            // changing a value. Pinching apart is the way to separate lines that are too
            // close to hit.
            val bandPx = MARKER_GRAB_BAND.toPx()

            fun distanceAt(y: Float): Double =
                latest.axis.distanceAt((y / size.height).toDouble())

            fun yOf(d: Double): Float =
                (size.height * latest.axis.fractionOf(d)).toFloat()

            scaleGestures(
                onDown = { pos ->
                    val h = latest
                    // Nearest line wins, so that lines close together still resolve to
                    // one of them rather than to whichever the tests happened to try first.
                    val candidates = buildList {
                        add(DistanceHandle.SUBJECT to h.subject)
                        h.near?.takeIf { it.isFinite() }?.let { add(DistanceHandle.NEAR to it) }
                        h.far?.takeIf { it.isFinite() }?.let { add(DistanceHandle.FAR to it) }
                    }
                    val caught = candidates
                        .map { (kind, value) -> kind to abs(pos.y - yOf(value)) }
                        .filter { it.second <= bandPx }
                        .minByOrNull { it.second }
                        ?.first
                    val kind = caught ?: DistanceHandle.SCALE
                    // A limit marker can only travel with the finger if the scale stops
                    // resizing itself around it, so the span is taken as it stands the
                    // moment one is picked up. The subject needs no such thing: it moves
                    // by its anchor, which the span does not touch.
                    if (kind == DistanceHandle.NEAR || kind == DistanceHandle.FAR) {
                        h.onHoldSpan(h.axis.lnSpan)
                    }
                    grab.start(
                        kind,
                        when (kind) {
                            DistanceHandle.NEAR -> h.near ?: h.subject
                            DistanceHandle.FAR -> h.far?.takeIf { it.isFinite() } ?: h.subject
                            else -> h.subject
                        },
                    )
                },
                onTransform = { _, _, zoom ->
                    val h = latest
                    h.onZoom(zoom.toDouble(), h.axis.lnSpan)
                },
                onDrag = { _, delta, speed ->
                    val h = latest
                    // Sliding the view: the graduations and every line on them travel
                    // with the finger, and not one reading changes.
                    if (grab.handle == DistanceHandle.SCALE) {
                        h.onSubjectAnchor((delta.y / size.height).toDouble())
                        return@scaleGestures
                    }
                    // Accumulated rather than read off the finger's position: a canvas
                    // that moves mid-drag then cannot be mistaken for a drag.
                    grab.travelled += delta.y
                    val range = if (grab.handle == DistanceHandle.SUBJECT) {
                        h.subjectRange
                    } else {
                        DofState.MIN_DISTANCE..DofState.MAX_DISTANCE
                    }
                    val rate = h.axis.slopeAt(grab.travelled, size.height.toFloat()) *
                        DistanceAxis.speedBoost(speed)
                    grab.value = (grab.value * exp(-delta.y * rate))
                        .coerceIn(range.start, range.endInclusive)
                    val value = grab.value

                    when (grab.handle) {
                        DistanceHandle.SUBJECT -> {
                            // The line travels with the finger while the view re-scales
                            // underneath it, which is what lets both happen at once: the
                            // view is positioned from the value and the anchor, so any
                            // pairing of the two is consistent by construction. The line
                            // stops at the edge band, and against the subject's own floor
                            // it stops entirely rather than sliding off its own reading.
                            if (value != h.subject) {
                                h.onSubjectAnchor((delta.y / size.height).toDouble())
                            }
                            h.onSubject(value)
                        }
                        DistanceHandle.NEAR -> h.onNear(value)
                        DistanceHandle.FAR -> h.onFar(value)
                        DistanceHandle.SCALE, DistanceHandle.NONE -> Unit
                    }
                },
                // Only a tap on the subject itself moves the subject. Letting any tap
                // move it meant a drag that started in a limit zone but barely travelled
                // registered as a tap and teleported the blue line to the finger, which
                // looked like the subject wandering on its own.
                onTap = { pos ->
                    if (abs(pos.y - yOf(latest.subject)) <= bandPx) {
                        latest.onSubject(distanceAt(pos.y))
                    }
                },
            )
        }
    ) {
        val h = size.height
        val w = size.width
        if (h <= 0f || w <= 0f) return@Canvas

        val leftAxis = w * leftAxisFrac
        val rightAxis = w * rightAxisFrac

        fun yOf(d: Double): Float = (h * axis.fractionOf(d)).toFloat()

        // ---- the double cone -----------------------------------------------------
        if (stackPoints.isEmpty()) drawImage(
            image = cone,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(cone.width, cone.height),
            dstOffset = IntOffset(leftAxis.roundToInt(), 0),
            dstSize = IntSize((rightAxis - leftAxis).roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.Low,
        )

        val tickLen = w * 0.055f
        val majorLen = w * 0.095f

        // ---- what each side is measuring -----------------------------------------
        // Drawn inside the scale, at the head of the column of numbers each one names, so
        // they read as the headings of those columns. Put up with the read-outs instead
        // they would sit under "near" and "far" and look like two more distances.
        // The graduations give up the strip they occupy; nothing else may be labelled
        // above [labelCeiling].
        val labelCeiling = fontPx * 1.45f
        leftPaint.color = Palette.AxisName.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            if (stackPoints.isEmpty()) "blur" else "frame",
            leftAxis - tickLen - fontPx * 0.2f,
            fontPx * 0.95f,
            leftPaint,
        )
        rightPaint.color = Palette.AxisName.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            "distance",
            rightAxis + majorLen + fontPx * 0.25f,
            fontPx * 0.95f,
            rightPaint,
        )

        // ---- distances, down the right side --------------------------------------
        drawLine(Palette.Tick, Offset(rightAxis, 0f), Offset(rightAxis, h), strokeWidth = 1.5f)

        rightPaint.color = Palette.Tick.toArgb()

        // Graduations carry the unit's own numbers, and keep whatever precision it
        // takes to tell one from the next — a window a few millimetres wide would lose
        // all its labels to the read-out's rounding.
        val unit = format.unit
        for (t in decadeTicks(
            lo = unit.fromMm(window.lo),
            hi = unit.fromMm(window.hi),
            position = { v -> yOf(unit.toMm(v)) },
            minLabelGapPx = fontPx * 1.30f,
            minTickGapPx = fontPx * 0.32f,
        )) {
            val y = yOf(unit.toMm(t.value))
            if (y < 0f || y > h) continue
            drawLine(
                Palette.Tick,
                Offset(rightAxis, y),
                Offset(rightAxis + if (t.major) majorLen else tickLen, y),
                strokeWidth = if (t.major) 2f else 1f,
            )
            val label = t.label?.takeIf { y > labelCeiling } ?: continue
            drawContext.canvas.nativeCanvas.drawText(
                label,
                rightAxis + majorLen + fontPx * 0.25f,
                y + fontPx * 0.36f,
                rightPaint,
            )
        }

        // ---- blur amounts, down the left side ------------------------------------
        drawLine(Palette.Tick, Offset(leftAxis, 0f), Offset(leftAxis, h), strokeWidth = 1.5f)
        // The two graduations at blur 1 are the sharpness criterion, level with the red
        // lines, and they are what every other number on this axis is read against. So
        // they claim their labels first and the rest fit around them — laid out in scale
        // order they would lose to whichever neighbour happened to come first, and which
        // neighbour that was would flip either side of the subject.
        val placed = ArrayList<Float>()
        fun label(distance: Double, blur: Double, reserved: Boolean) {
            val y = yOf(distance)
            if (y < labelCeiling || y > h - fontPx * 0.2f) return
            val color = blurColor(blur / sharpBlur)
            drawLine(color, Offset(leftAxis - tickLen, y), Offset(leftAxis, y), strokeWidth = 2f)
            if (!reserved && placed.any { abs(y - it) < fontPx * 1.2f }) return
            placed += y
            leftPaint.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                formatSig(blur, 2),
                leftAxis - tickLen - fontPx * 0.2f,
                y + fontPx * 0.36f,
                leftPaint,
            )
        }

        val (sharp, rest) = (if (stackPoints.isEmpty()) blurTicks else emptyList())
            .partition { it.second == sharpBlur }
        for ((distance, blur) in sharp) label(distance, blur, reserved = true)
        // Sorted down the scale, so a crowded stretch keeps its topmost labels rather than
        // whichever ticks happened to come first.
        for ((distance, blur) in rest.sortedBy { yOf(it.first) }) {
            label(distance, blur, reserved = false)
        }

        // ---- markers -------------------------------------------------------------
        // Hyperfocal first, so the lines the user can grab draw over it.
        // Only when it is genuinely in view: positions are clamped to the window, so an
        // off-scale hyperfocal would otherwise pin itself to the top edge and read as
        // though it were at the far end of the visible range.
        val hyperfocalInView = hyperfocal >= window.lo && hyperfocal <= window.hi
        val hy = yOf(hyperfocal)
        if (hyperfocalInView && hy in 0f..h) {
            drawLine(
                Palette.HyperfocalLine,
                Offset(leftAxis, hy),
                Offset(rightAxis, hy),
                strokeWidth = 2f,
            )
        }

        // Beyond the hyperfocal distance the far limit is infinite and simply off the
        // top of the view. Pinning it to the top edge would draw a red line at a distance
        // it does not apply to; the header already reads it out as infinity.
        fun drawLimit(d: Double?) {
            if (stackPoints.isNotEmpty()) return
            if (d == null || !d.isFinite()) return
            if (d < window.lo || d > window.hi) return
            val y = yOf(d)
            if (y < -2f || y > h + 2f) return
            drawHandleLine(leftAxis, rightAxis, y, Palette.LimitLine, w * 0.05f)
        }
        drawLimit(near)
        drawLimit(far)

        // One line per frame, numbered from the near end while there is room to read it.
        leftPaint.color = Palette.StackLine.toArgb()
        stackPoints.forEachIndexed { i, d ->
            if (d < window.lo || d > window.hi) return@forEachIndexed
            val y = yOf(d)
            if (y < -2f || y > h + 2f) return@forEachIndexed
            drawLine(
                Palette.StackLine,
                Offset(leftAxis, y),
                Offset(rightAxis, y),
                strokeWidth = 2f,
            )
            if (stackPoints.size <= 12 && y > labelCeiling) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${i + 1}",
                    leftAxis - tickLen - fontPx * 0.2f,
                    y + fontPx * 0.36f,
                    leftPaint,
                )
            }
        }

        val sy = yOf(subject)
        if (sy > -2f && sy < h + 2f) {
            drawHandleLine(leftAxis, rightAxis, sy, Palette.SubjectLine, w * 0.05f)
            subjectBlurLabel?.takeIf { stackPoints.isEmpty() }?.let {
                leftPaint.color = Palette.Text.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    it,
                    leftAxis - tickLen - fontPx * 0.2f,
                    sy + fontPx * 0.36f,
                    leftPaint,
                )
            }
        }
    }
}

/** A draggable marker line: a bar across the cone with a grab triangle at its right end. */
private fun DrawScope.drawHandleLine(
    left: Float,
    right: Float,
    y: Float,
    color: Color,
    size: Float,
) {
    drawRect(color = color, topLeft = Offset(left, y - 1.25f), size = Size(right - left, 2.5f))
    val path = Path().apply {
        moveTo(right, y)
        lineTo(right - size, y - size * 0.75f)
        lineTo(right - size, y + size * 0.75f)
        close()
    }
    drawPath(path, color)
}
