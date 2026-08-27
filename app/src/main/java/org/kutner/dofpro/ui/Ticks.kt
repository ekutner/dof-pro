package org.kutner.dofpro.ui

import androidx.compose.ui.graphics.Color
import org.kutner.dofpro.model.FStops
import org.kutner.dofpro.model.formatSig
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One graduation on a scale. Major ticks are longer and carry a label. */
data class ScaleTick(
    val value: Double,
    val label: String? = null,
    val color: Color = Palette.Tick,
    val major: Boolean = label != null,
)

/**
 * Log-scale graduations over [lo]..[hi], laid out like a lens barrel: fine unlabelled
 * ticks throughout, and labels on the roundest numbers that still have room.
 */
fun decadeTicks(
    lo: Double,
    hi: Double,
    position: (Double) -> Float,
    minLabelGapPx: Float,
    minTickGapPx: Float,
): List<ScaleTick> {
    if (lo <= 0 || hi <= lo) return emptyList()
    // Zoomed inside a single decade there are no round mantissas left to label, so fall
    // back to evenly spaced steps.
    if (hi / lo < 4.0) return linearTicks(lo, hi, position, minLabelGapPx, minTickGapPx)

    val kLo = floor(log10(lo)).toInt()
    val kHi = floor(log10(hi)).toInt()

    // Round numbers first, so the labels that survive a crowded scale are the useful ones.
    val priority = listOf(1.0, 2.0, 5.0, 3.0, 1.5, 4.0, 6.0, 8.0, 2.5, 7.0, 9.0, 1.2)
    val labelled = ArrayList<Pair<Double, Float>>()
    val out = ArrayList<ScaleTick>()

    for (m in priority) {
        for (k in kLo..kHi) {
            val v = m * 10.0.pow(k)
            if (v < lo || v > hi) continue
            val y = position(v)
            if (labelled.any { kotlin.math.abs(it.second - y) < minLabelGapPx }) continue
            labelled += v to y
            out += ScaleTick(v, formatTick(v))
        }
    }

    // Minor graduations: tenths of a decade, thinned out to whatever the zoom allows.
    val minor = ArrayList<Float>()
    for (k in kLo..kHi) {
        var m = 1.0
        while (m < 10.0) {
            val v = m * 10.0.pow(k)
            m += 0.1
            if (v < lo || v > hi) continue
            val y = position(v)
            if (labelled.any { kotlin.math.abs(it.second - y) < minTickGapPx }) continue
            if (minor.any { kotlin.math.abs(it - y) < minTickGapPx }) continue
            minor += y
            out += ScaleTick(v, null)
        }
    }
    return out
}

/**
 * Graduations for a window narrower than a decade — a 1-2-5 step chosen to give a handful
 * of labels, with finer ticks in between.
 */
private fun linearTicks(
    lo: Double,
    hi: Double,
    position: (Double) -> Float,
    minLabelGapPx: Float,
    minTickGapPx: Float,
): List<ScaleTick> {
    val span = hi - lo
    if (span <= 0.0) return emptyList()

    // Smallest 1-2-5 step that still keeps the labels down to a readable handful.
    val base = 10.0.pow(floor(log10(span)))
    val steps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0).map { it * base }
    val step = steps.firstOrNull { span / it <= 8.0 } ?: steps.last()
    val minorStep = step / 5.0

    // Enough decimals to tell one graduation from the next. Significant figures alone
    // will not do it once the view is a few millimetres wide: 1.9985 and 1.9990 both
    // round to 1.999 and the scale reads as though it had stopped.
    val decimals = kotlin.math.max(0.0, -floor(log10(step))).toInt().coerceAtMost(6)

    val out = ArrayList<ScaleTick>()
    var lastLabelY = -1e9f
    var lastTickY = -1e9f
    var i = kotlin.math.ceil(lo / minorStep).toLong()
    while (i * minorStep <= hi) {
        val v = i * minorStep
        i++
        val y = position(v)
        // Rounding keeps 47.900000000000006 from reaching the label formatter.
        val isMajor = kotlin.math.abs(v / step - Math.round(v / step)) < 1e-6
        if (isMajor && kotlin.math.abs(y - lastLabelY) >= minLabelGapPx) {
            lastLabelY = y
            lastTickY = y
            out += ScaleTick(v, String.format("%.${decimals}f", v))
        } else if (kotlin.math.abs(y - lastTickY) >= minTickGapPx) {
            lastTickY = y
            out += ScaleTick(v, null)
        }
    }
    return out
}

/** Tick labels are round numbers; float noise like 0.7000000000000001 must not show. */
private fun formatTick(v: Double): String = formatSig(v, 4)

/**
 * Aperture graduations, taken from the f numbers real lenses are marked with. Whole stops
 * are labelled and coloured by how much diffraction blur they cause; the subdivisions in
 * between follow the half/third stop setting.
 */
fun apertureTicks(
    stops: List<Double>,
    lo: Double,
    hi: Double,
    diffractionBlurAt: (Double) -> Double,
): List<ScaleTick> = stops
    .filter { it in lo..hi }
    .map { f ->
        if (FStops.isWholeStop(f)) {
            ScaleTick(f, formatSig(f, 3), blurColor(diffractionBlurAt(f)))
        } else {
            ScaleTick(f, null)
        }
    }
