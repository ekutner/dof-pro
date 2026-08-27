package org.kutner.dofpro.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Distance units. Everything is computed in mm and converted only for display. */
enum class DistanceUnit(val label: String, val mm: Double) {
    MM("mm", 1.0),
    CM("cm", 10.0),
    IN("in", 25.4),
    FT("ft", 304.8),
    YD("yd", 914.4),
    M("m", 1000.0);

    fun fromMm(v: Double): Double = v / mm
    fun toMm(v: Double): Double = v * mm
}

/**
 * Which family of units to write distances in. The particular unit is not a setting: a
 * calculator that can be looking at 40 mm of depth or forty metres of it has no one unit
 * that suits both, and asking the user to switch by hand as they pan is asking them to do
 * the app's job. So the choice is only metric or imperial, and [formatFor] picks the unit
 * and the precision from the distance itself.
 */
enum class UnitSystem(val label: String) {
    METRIC("Metric"),
    IMPERIAL("Imperial");

    /**
     * How to write a distance of [mm] millimetres.
     *
     * Metric reads in metres from half a metre up, dropping the decimals past ten metres
     * where they are noise — nobody stands "13.06 m" from anything. Below half a metre it
     * turns to centimetres, whole ones down to 20 cm and tenths below that, which is where
     * close-up work needs the resolution.
     *
     * Imperial is not a translation of that. It is built out of the numbers an imperial
     * reader would use, and it needs fewer bands, because the foot already subdivides
     * twelve ways where the metre subdivides a hundred. Feet take over at two feet — a
     * foot and a half is spoken as eighteen inches, and only past a couple of feet does
     * anyone reach for feet at all — and the decimals go at thirty feet. Inches carry
     * whatever precision they need, as feet do below thirty, so the near limit and the far
     * limit stay distinguishable in close-up work.
     */
    fun formatFor(mm: Double): DistanceFormat = when (this) {
        METRIC -> when {
            mm >= 500.0 -> DistanceFormat(DistanceUnit.M, if (mm > 10_000.0) 0 else null)
            mm >= 200.0 -> DistanceFormat(DistanceUnit.CM, 0)
            else -> DistanceFormat(DistanceUnit.CM, 1)
        }
        IMPERIAL -> when {
            mm >= 2.0 * DistanceUnit.FT.mm ->
                DistanceFormat(DistanceUnit.FT, if (mm > 30.0 * DistanceUnit.FT.mm) 0 else null)
            else -> DistanceFormat(DistanceUnit.IN, null)
        }
    }

    companion object {
        /**
         * Reads the stored units setting, accepting what earlier versions wrote there.
         *
         * It used to name one fixed unit — MM, CM, IN, FT, YD, M — and now names a family,
         * so a stored setting is carried over to the family it belonged to rather than
         * quietly switching an imperial user to metric on upgrade.
         */
        fun parse(name: String?, fallback: UnitSystem = METRIC): UnitSystem = when (name) {
            "MM", "CM", "M", METRIC.name -> METRIC
            "IN", "FT", "YD", IMPERIAL.name -> IMPERIAL
            else -> fallback
        }
    }
}

/**
 * The unit a distance is written in, and how many decimal places it gets — or null to let
 * [distinguishingSig] choose, which is what keeps a shallow depth of field from printing
 * the same number for its near limit, its subject and its far limit.
 */
data class DistanceFormat(val unit: DistanceUnit, val decimals: Int?) {

    /** The distance in mm as a bare number, with no unit — what an editor is filled with. */
    fun number(mm: Double, sig: Int = 3): String {
        if (mm.isNaN()) return "—"
        if (mm.isInfinite()) return "∞"
        val v = unit.fromMm(mm)
        return if (decimals != null) String.format(Locale.US, "%.${decimals}f", v)
        else formatSig(v, sig)
    }

    /** The distance in mm, written out with its unit. */
    fun text(mm: Double, sig: Int = 3): String {
        if (!mm.isFinite()) return number(mm, sig)
        return "${number(mm, sig)} ${unit.label}"
    }

    /**
     * Reads a distance the user typed, in this format's unit, as millimetres.
     *
     * Returns null for anything that is not a positive number. A comma is taken as a
     * decimal point: the numbers are written with a point whatever the phone's locale, so
     * a keyboard that offers a comma would otherwise produce something unreadable back.
     */
    fun parse(typed: String): Double? {
        val v = typed.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!v.isFinite() || v <= 0.0) return null
        return unit.toMm(v)
    }
}

/**
 * Formats a number to [sig] significant digits without trailing zero noise, the way the
 * readouts at the top of each scale are written: 9.23 m, 66.4 ft, 4 ft.
 */
fun formatSig(value: Double, sig: Int = 3): String {
    if (value.isNaN()) return "—"
    if (value.isInfinite()) return "∞"
    if (value == 0.0) return "0"
    val a = abs(value)
    val decimals = (sig - 1 - floor(log10(a)).toInt()).coerceIn(0, 6)
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    // Locale-fixed: a decimal comma would read as a thousands separator to half the world.
    var s = String.format(Locale.US, "%.${decimals}f", rounded)
    if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
    return s
}

/** A distance in mm rendered in [unit], with the unit appended. */
fun formatDistance(mm: Double, unit: DistanceUnit, sig: Int = 3): String =
    if (mm.isInfinite()) "∞" else "${formatSig(unit.fromMm(mm), sig)} ${unit.label}"

/** A distance in mm written the way [system] writes a distance that size. */
fun formatDistance(mm: Double, system: UnitSystem): String =
    system.formatFor(mm).text(mm)

/**
 * The fewest significant digits that still tell these distances apart. Three is plenty at
 * ordinary distances, but a macro depth of field can be a few hundredths of an inch wide,
 * and printing the near limit, the subject and the far limit as the same number three
 * times hides exactly what the reader is looking for.
 */
fun distinguishingSig(values: List<Double>, unit: DistanceUnit, max: Int = 6): Int {
    val finite = values.filter { it.isFinite() }
    for (sig in 3..max) {
        val shown = finite.map { formatSig(unit.fromMm(it), sig) }
        if (shown.size == shown.toSet().size) return sig
    }
    return max
}

/** Light or dark, or whichever the phone is set to. */
enum class ThemeChoice(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}
