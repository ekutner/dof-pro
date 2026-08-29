package org.kutner.dofpro.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * How near the marker a finger has to land to take hold of it.
 *
 * Generous, because a marker is a hairline and a fingertip is not. On the distance scale
 * it is also what separates taking hold of a line from taking hold of the scale itself.
 */
val MARKER_GRAB_BAND = 24.dp

/**
 * Where a harmonic scale currently sits: the reciprocal value at the top of the column,
 * and how much of that reciprocal fits across its height.
 */
private class HarmonicGeometry(val uTop: Double, val span: Double) {
    fun yOf(value: Double, height: Float): Float =
        (height * (1.0 / value - uTop) / span).toFloat()

    val lo: Double get() = 1.0 / (uTop + span)
    val hi: Double get() = if (uTop <= 0.0) Double.POSITIVE_INFINITY else 1.0 / uTop
}

/**
 * A scale pinned to [lo]..[hi] with a little air at each end, so a graduation sitting
 * exactly on a limit is not half off the canvas. The marker moves within it and the
 * graduations never move — the way a zoom barrel is marked, and the way the aperture scale
 * already behaves.
 */
private fun boundedHarmonic(lo: Double, hi: Double): HarmonicGeometry {
    val loDraw = lo / END_MARGIN
    val hiDraw = hi * END_MARGIN
    val uTop = 1.0 / hiDraw
    return HarmonicGeometry(uTop, 1.0 / loDraw - uTop)
}

/** How far past each end of a bounded scale the graduations run. */
private const val END_MARGIN = 1.08

/**
 * Places the scale so [value] falls at [anchor] down the column, keeping the zoom — except
 * where that would run the top of the scale past [ceiling], in which case the top stops
 * there and the rest compresses to keep the marker where the finger left it.
 *
 * [ceiling] is how far the scale may *draw*, not how far the value may go — they are
 * different things. This is the layout for a scale with no natural ends: the blur scale,
 * and a focal length scale whose lens gives it nothing to bound (see [boundedHarmonic] and
 * `Lens.scaleRange`). It reaches much further above the marker than below, so a bounded
 * range is the better choice wherever there is one.
 */
private fun harmonicGeometry(
    value: Double,
    zoom: Double,
    ceiling: Double,
    anchor: Double,
): HarmonicGeometry {
    val uValue = 1.0 / value
    val a = anchor.coerceIn(0.05, 0.95)
    val uTop = maxOf(uValue - a * 2.0 * zoom * uValue, 1.0 / ceiling)
        .coerceAtMost(uValue * 0.999)
    return HarmonicGeometry(uTop, (uValue - uTop) / a)
}

/**
 * The focal length and blur scales are harmonic — laid out linearly in 1/value, the same
 * way the distance scale is. Equal steps along them are equal rotations of a focus ring,
 * which is what keeps the depth of field markers a fixed distance apart.
 *
 * Dragging carries the marker along with the finger, so the thing being dragged is the
 * thing that moves. The marker stops a fifth in from either end of the column, and from
 * there the same movement scrolls the scale past it instead.
 *
 * @param zoom how far the scale reaches either side of the marker, as a fraction of the
 *   marker value's reciprocal.
 * @param anchor where the marker sits, as a fraction down from the top.
 * @param ceiling largest value the scale will draw up to; defaults to [maxValue], but a
 *   scale whose value is confined by equipment wants the wider figure here.
 */
@Composable
fun HarmonicScale(
    value: Double,
    zoom: Double,
    minValue: Double,
    maxValue: Double,
    editable: Boolean,
    ticks: (lo: Double, hi: Double, position: (Double) -> Float) -> List<ScaleTick>,
    fontPx: Float,
    modifier: Modifier = Modifier,
    anchor: Double = 0.5,
    ceiling: Double = maxValue,
    /**
     * Fixes the scale to this stretch instead of anchoring it on the value. The marker
     * then travels the whole column and the graduations hold still, and neither the anchor
     * nor the zoom has anything left to do.
     */
    bounds: ClosedFloatingPointRange<Double>? = null,
    onValueChange: (Double) -> Unit = {},
    onZoomChange: (Double) -> Unit = {},
    onAnchorChange: (Double) -> Unit = {},
    onSettle: () -> Unit = {},
) {
    val labelPaint = remember(fontPx) { textPaint(fontPx, Paint.Align.LEFT) }
    val latest by rememberUpdatedState(
        HarmonicInteraction(
            value, zoom, minValue, maxValue, ceiling, editable, anchor, bounds,
            onValueChange, onZoomChange, onAnchorChange, onSettle,
        )
    )

    val grab = remember { Grab() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val grabBandPx = MARKER_GRAB_BAND.toPx()

            scaleGestures(
                onDown = { pos ->
                    val s = latest
                    val geometry = s.bounds?.let { boundedHarmonic(it.start, it.endInclusive) }
                        ?: harmonicGeometry(s.value, s.zoom, s.ceiling, s.anchor)
                    val markerY = geometry.yOf(s.value, size.height.toFloat())
                    grab.start(s.value, 0f, abs(pos.y - markerY) <= grabBandPx)
                },
                onDrag = { _, delta, _ ->
                    val s = latest
                    if (s.editable && grab.holding && size.height > 0 && grab.value > 0.0) {
                        val geometry = s.bounds?.let { boundedHarmonic(it.start, it.endInclusive) }
                            ?: harmonicGeometry(grab.value, s.zoom, s.ceiling, s.anchor)
                        // Dragging down walks the marker onto the smaller values printed
                        // below it: 1/value grows downward, so the step is positive.
                        val step = (delta.y / size.height) * geometry.span
                        val u = 1.0 / grab.value + step
                        if (u > 0.0) {
                            val before = grab.value
                            grab.moveTo((1.0 / u).coerceIn(s.minValue, s.maxValue))
                            s.onValue(grab.value)
                            // Once the value is against its limit the marker stops too,
                            // or it would slide on and stop pointing at its own reading.
                            // A bounded scale has no anchor to move: the marker travels the
                            // column and there is nothing beyond the ends to scroll to.
                            if (grab.value != before && s.bounds == null) {
                                s.onAnchor((delta.y / size.height).toDouble())
                            }
                        }
                    }
                },
                onTransform = { _, _, z ->
                    val s = latest
                    if (z > 0f && s.bounds == null) s.onZoom((s.zoom / z).coerceIn(0.05, 0.999))
                },
                onUp = { latest.onSettle() },
            )
        }
    ) {
        val h = size.height
        val w = size.width
        if (h <= 0f || w <= 0f) return@Canvas

        val geometry = bounds?.let { boundedHarmonic(it.start, it.endInclusive) }
            ?: harmonicGeometry(value, zoom, ceiling, anchor)
        fun yOf(v: Double): Float = geometry.yOf(v, h)

        drawScaleBody(
            w, h,
            ticks(geometry.lo, geometry.hi, ::yOf),
            ::yOf, fontPx, labelPaint, editable,
            yOf(value),
        )
    }
}

/**
 * The aperture scale is logarithmic — whole stops are evenly spaced — and unlike the other
 * scales it holds still while the marker moves, matching the way a lens aperture ring is
 * marked. A white marker means the mode is computing the f stop.
 */
@Composable
fun ApertureScale(
    value: Double,
    center: Double,
    span: Double,
    minValue: Double,
    maxValue: Double,
    editable: Boolean,
    ticks: (lo: Double, hi: Double) -> List<ScaleTick>,
    fontPx: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit = {},
    onCenterChange: (Double) -> Unit = {},
    onSpanChange: (Double) -> Unit = {},
) {
    val labelPaint = remember(fontPx) { textPaint(fontPx, Paint.Align.LEFT) }

    // Keep the marker on screen no matter where the scale has been panned or zoomed.
    val shownCenter = center.coerceIn(value * exp(-span * 0.45), value * exp(span * 0.45))

    val latest by rememberUpdatedState(
        ApertureInteraction(
            value, shownCenter, span, minValue, maxValue, editable,
            onValueChange, onCenterChange, onSpanChange,
        )
    )

    val grab = remember { Grab() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val grabBandPx = MARKER_GRAB_BAND.toPx()

            scaleGestures(
                onDown = { pos ->
                    val s = latest
                    val markerY = size.height / 2f -
                        (ln(s.value / s.center) * (size.height / s.span)).toFloat()
                    grab.start(s.value, 0f, abs(pos.y - markerY) <= grabBandPx)
                },
                onDrag = { _, delta, _ ->
                    val s = latest
                    if (s.editable && grab.holding && size.height > 0 && grab.value > 0.0) {
                        val pxPerLn = size.height / s.span
                        // Dragging up on the scale walks the marker toward smaller
                        // apertures. Measured as movement rather than as absolute
                        // position: anything that reflows the column mid-drag — a warning
                        // appearing, say — moves the canvas under a still finger, and an
                        // absolute reading takes that for a drag of the same size.
                        val v = grab.value * exp(-delta.y / pxPerLn)
                        grab.moveTo(v.coerceIn(s.minValue, s.maxValue))
                        s.onValue(grab.value)
                    }
                },
                onTransform = { _, panY, zoom ->
                    val s = latest
                    if (zoom > 0f) s.onSpan((s.span / zoom).coerceIn(0.8, 6.5))
                    if (panY != 0f && size.height > 0) {
                        val pxPerLn = size.height / s.span
                        s.onCenter(s.center * exp(panY / pxPerLn))
                    }
                },
                onTap = { pos ->
                    val s = latest
                    if (s.editable && size.height > 0) {
                        val pxPerLn = size.height / s.span
                        val v = s.center * exp((size.height / 2f - pos.y) / pxPerLn)
                        s.onValue(v.coerceIn(s.minValue, s.maxValue))
                    }
                },
            )
        }
    ) {
        val h = size.height
        val w = size.width
        if (h <= 0f || w <= 0f) return@Canvas

        val pxPerLn = (h / span).toFloat()
        fun yOf(v: Double): Float = h / 2f - (ln(v / shownCenter) * pxPerLn).toFloat()

        val lo = shownCenter * exp(-span / 2.0)
        val hi = shownCenter * exp(span / 2.0)

        drawScaleBody(w, h, ticks(lo, hi), ::yOf, fontPx, labelPaint, editable, yOf(value))
    }
}

/** Axis, graduations, labels and marker — shared by every scale column. */
private fun DrawScope.drawScaleBody(
    w: Float,
    h: Float,
    ticks: List<ScaleTick>,
    yOf: (Double) -> Float,
    fontPx: Float,
    labelPaint: Paint,
    editable: Boolean,
    markerY: Float,
) {
    val axisX = w * 0.28f
    val majorLen = w * 0.15f
    val minorLen = w * 0.085f

    drawLine(Palette.Tick, Offset(axisX, 0f), Offset(axisX, h), strokeWidth = 1.5f)

    for (t in ticks) {
        val y = yOf(t.value)
        if (y < -fontPx || y > h + fontPx) continue
        drawLine(
            color = if (t.major) t.color else Palette.Tick.copy(alpha = 0.8f),
            start = Offset(axisX, y),
            end = Offset(axisX + if (t.major) majorLen else minorLen, y),
            strokeWidth = if (t.major) 2f else 1f,
        )
        val label = t.label ?: continue
        labelPaint.color = t.color.toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            label,
            axisX + majorLen + fontPx * 0.28f,
            y + fontPx * 0.36f,
            labelPaint,
        )
    }

    if (markerY >= -w && markerY <= h + w) {
        drawMarker(
            x = axisX,
            y = markerY,
            size = w * 0.10f,
            color = if (editable) Palette.Marker else Palette.MarkerLocked,
        )
    }
}

/**
 * Where a drag began: the value under the finger and the pixel it was grabbed at.
 *
 * Every position during the drag is derived from this rather than accumulated event by
 * event. Accumulating breaks twice over — a slow drag whose per-event step is smaller
 * than one snap increment rounds back to where it started and the scale sticks, and any
 * event that arrives outside the intended gesture creeps the value along for good.
 * Deriving from the gesture's origin makes the same finger position always mean the same
 * value, however the events happen to arrive.
 */
private class Grab {
    var value: Double = 0.0
        private set
    var y: Float = 0f
        private set

    /**
     * Whether the finger went down on the marker.
     *
     * These two scales have one thing on them that can be moved, so a drag that did not
     * start on it has nothing to mean. Taking any drag as "move the value" made the
     * lightest brush across a column change the focal length or the aperture, usually
     * while the finger was on its way somewhere else.
     */
    var holding = false
        private set

    fun start(value: Double, y: Float, holding: Boolean = true) {
        this.value = value
        this.y = y
        this.holding = holding
    }

    /** Steps the carried value along without disturbing the grab point. */
    fun moveTo(value: Double) {
        this.value = value
    }
}

private class HarmonicInteraction(
    val value: Double,
    val zoom: Double,
    val minValue: Double,
    val maxValue: Double,
    val ceiling: Double,
    val editable: Boolean,
    val anchor: Double,
    val bounds: ClosedFloatingPointRange<Double>?,
    val onValue: (Double) -> Unit,
    val onZoom: (Double) -> Unit,
    val onAnchor: (Double) -> Unit,
    val onSettle: () -> Unit,
)

private class ApertureInteraction(
    val value: Double,
    val center: Double,
    val span: Double,
    val minValue: Double,
    val maxValue: Double,
    val editable: Boolean,
    val onValue: (Double) -> Unit,
    val onCenter: (Double) -> Unit,
    val onSpan: (Double) -> Unit,
)

/** The little triangle that points at the current value. */
fun DrawScope.drawMarker(x: Float, y: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x - size, y - size * 0.85f)
        lineTo(x - size, y + size * 0.85f)
        close()
    }
    drawPath(path, color)
}

fun textPaint(sizePx: Float, align: Paint.Align): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = sizePx
    textAlign = align
    isSubpixelText = true
}
