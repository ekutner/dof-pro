package org.kutner.dofpro.calc

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Depth of field math.
 *
 * Every distance is in millimetres, every blur is a diameter in millimetres at the
 * sensor/film plane. Equations follow Appendix C of the DoF 4.0 reference manual:
 *
 *   L  = focal length            A  = focus distance
 *   f  = f stop                  D  = subject distance
 *   c  = circle of confusion     H  = hyperfocal distance
 *   Bd = diffraction blur        Bf = focus blur
 */
object Dof {

    /** f stop at which diffraction blur equals 1mm, for 550nm green light: Bd = f/750. */
    private const val GREEN_NM = 550.0
    private const val GREEN_DIVISOR = 750.0

    const val INF = Double.POSITIVE_INFINITY

    /**
     * Diffraction blur in mm. Bd = f/750 for green light; blur scales linearly with
     * wavelength, so red light diffracts ~20% more and blue ~20% less.
     */
    fun diffractionBlur(f: Double, wavelengthNm: Double = GREEN_NM): Double =
        f * (wavelengthNm / GREEN_NM) / GREEN_DIVISOR

    /** The f stop at which diffraction blur equals [blur]. Inverse of [diffractionBlur]. */
    fun fStopForDiffraction(blur: Double, wavelengthNm: Double = GREEN_NM): Double =
        blur * GREEN_DIVISOR * (GREEN_NM / wavelengthNm)

    /** Focus blur in mm for a subject at [d] when focused at [a]. */
    fun focusBlur(l: Double, f: Double, a: Double, d: Double): Double {
        if (a <= l) return INF
        if (d <= 0.0) return INF
        if (a == d) return 0.0
        if (a.isInfinite()) return l * l / (f * d)
        if (d.isInfinite()) return l * l / (f * (a - l))
        return abs(l * l * (a - d) / (f * d * (a - l)))
    }

    /**
     * Focus and diffraction blur combine in quadrature. This is the definition that makes
     * [bestFStop] below the true minimum of the combined curve, and it reproduces the
     * blur readouts in the manual exactly (at the plane of focus Bf = 0, so the readout
     * is pure diffraction blur).
     */
    fun combine(bd: Double, bf: Double): Double =
        if (bf.isInfinite()) INF else sqrt(bd * bd + bf * bf)

    /** Total blur at [d] expressed as a multiple of the circle of confusion. */
    fun blurValue(l: Double, f: Double, a: Double, d: Double, c: Double, wavelengthNm: Double): Double =
        combine(diffractionBlur(f, wavelengthNm), focusBlur(l, f, a, d)) / c

    /**
     * How much focus blur we can still spend once diffraction has taken its cut.
     * Returns null when diffraction alone already exceeds the allowed blur, in which
     * case no distance is acceptably sharp and the depth of field limits vanish.
     */
    fun focusBlurBudget(f: Double, c: Double, blurLevel: Double, wavelengthNm: Double): Double? {
        val allowed = blurLevel * c
        val bd = diffractionBlur(f, wavelengthNm)
        if (bd >= allowed) return null
        return sqrt(allowed * allowed - bd * bd)
    }

    /** Near focus limit for an allowed focus blur of [bf]. */
    fun nearLimit(l: Double, f: Double, a: Double, bf: Double): Double {
        if (a.isInfinite()) return l * l / (f * bf)
        return a * l * l / (l * l + f * bf * (a - l))
    }

    /** Far focus limit for an allowed focus blur of [bf]; infinite past the hyperfocal distance. */
    fun farLimit(l: Double, f: Double, a: Double, bf: Double): Double {
        if (a.isInfinite()) return INF
        val denom = l * l - f * bf * (a - l)
        if (denom <= 0.0) return INF
        return a * l * l / denom
    }

    /** H = L^2/(f*Bf) + L — the closest focus that still holds infinity acceptably sharp. */
    fun hyperfocal(l: Double, f: Double, bf: Double): Double = l * l / (f * bf) + l

    /** Focus distance that blurs [dn] and [df] equally: A = 2*Dn*Df/(Dn + Df). */
    fun bestFocus(dn: Double, df: Double): Double =
        if (df.isInfinite()) 2.0 * dn else 2.0 * dn * df / (dn + df)

    /** Near limit implied by a focus distance and far limit. */
    fun nearFromFocusAndFar(a: Double, df: Double): Double =
        if (df.isInfinite()) a / 2.0 else a * df / (2.0 * df - a)

    /** Far limit implied by a focus distance and near limit. */
    fun farFromFocusAndNear(a: Double, dn: Double): Double =
        if (a >= 2.0 * dn) INF else a * dn / (2.0 * dn - a)

    /**
     * The f stop where focus blur and diffraction blur are equal, which is where their
     * quadrature sum is smallest — the sharpest possible rendering of the range [dn]..[a].
     */
    fun bestFStop(l: Double, a: Double, dn: Double, wavelengthNm: Double = GREEN_NM): Double {
        if (a <= l || a <= dn) return Double.NaN
        val k = (wavelengthNm / GREEN_NM) / GREEN_DIVISOR
        return l * sqrt((a - dn) / (k * dn * (a - l)))
    }

    /**
     * Macro depth of field: DOF = 2*f*c*(m + 1)/m^2. Used instead of the standard
     * equations when the subject is close enough that they break down.
     */
    fun macroDof(f: Double, c: Double, m: Double): Double =
        if (m <= 0.0) INF else 2.0 * f * c * (m + 1.0) / (m * m)

    /** Focus blur at [d] for a lens focused at [a] at magnification [m]. Inverse of [macroDof]. */
    fun macroFocusBlur(f: Double, m: Double, a: Double, d: Double): Double {
        if (m <= 0.0 || d <= 0.0) return INF
        if (d.isInfinite()) return INF
        return m * m * abs(a - d) / (f * (m + 1.0))
    }

    // ---- Harmonic distance scale -------------------------------------------------
    // Equal steps along this scale are equal rotations of a lens focus ring, which is
    // why the depth of field markers keep their spacing as the focus distance changes.

    /** Harmonic position [0..1] -> distance, over the range [lo]..[hi]. */
    fun harmonicToDistance(x: Double, lo: Double, hi: Double): Double =
        if (hi.isInfinite()) lo / (1.0 - x) else lo / (1.0 - (1.0 - lo / hi) * x)

    /** Distance -> harmonic position [0..1] over the range [lo]..[hi]. */
    fun distanceToHarmonic(y: Double, lo: Double, hi: Double): Double =
        if (hi.isInfinite()) 1.0 - lo / y else (1.0 - lo / y) / (1.0 - lo / hi)

    // ---- Focus stacking ----------------------------------------------------------

    data class Stack(
        /** Best single f stop for the whole stack. */
        val fStop: Double,
        /** Distance to focus each frame at, near to far. */
        val focusPoints: List<Double>,
        /** Worst blur anywhere in the range, as a multiple of the circle of confusion. */
        val worstBlur: Double,
    )

    /** A stack: where to focus each frame, and whether the range was covered. */
    data class Frames(val focusPoints: List<Double>, val complete: Boolean) {
        val count: Int get() = focusPoints.size
    }

    /**
     * The fewest frames that hold everything from [near] out to infinity sharp, and where
     * to focus each of them, given a lens already set to a focal length and an aperture.
     *
     * The inverse of [focusStack], which is handed a frame count and finds the aperture.
     * Here the aperture is the photographer's and the count falls out of it.
     *
     * Each frame is placed so its *near* limit lands where the previous frame's coverage
     * ran out, and the last is focused at the hyperfocal distance [h] — the closest focus
     * that still holds infinity, and therefore the one that reaches back furthest while
     * doing so. Greedy placement like this is minimal by construction: no frame could
     * start further out without leaving a gap.
     *
     * [overlap] is the fraction of a frame's depth of field that the next frame doubles
     * back over, so a stack has no hairline seams to go wrong in the blend. It is measured
     * in reciprocal distance because that is the space a stack is uniform in — equal steps
     * there are equal rotations of the focus ring — so the overlap means the same thing at
     * the near end as at the far one.
     */
    fun stackToInfinity(
        l: Double,
        h: Double,
        near: Double,
        overlap: Double,
        maxFrames: Int = 999,
    ): Frames {
        if (!h.isFinite() || !near.isFinite() || near <= l || l <= 0.0) {
            return Frames(emptyList(), false)
        }
        val p = overlap.coerceIn(0.0, 0.9)
        val points = ArrayList<Double>()
        var n = near
        while (points.size < maxFrames) {
            // Focusing at h reaches back to h/2, so once the remaining range starts there
            // or beyond, one more frame finishes the stack.
            if (n >= h / 2.0) {
                points += h
                return Frames(points, true)
            }
            val denom = h - l - n
            if (denom <= 0.0) {
                points += h
                return Frames(points, true)
            }
            // The focus distance whose near limit is exactly n.
            val a = n * (h - 2.0 * l) / denom
            if (!a.isFinite() || a >= h) {
                points += h
                return Frames(points, true)
            }
            points += a
            val far = a * (h - l) / (h - a)
            if (!far.isFinite() || far <= n) return Frames(points, false)
            val xNear = 1.0 / n
            val xFar = 1.0 / far
            val next = 1.0 / (xFar + p * (xNear - xFar))
            if (next <= n) return Frames(points, false)
            n = next
        }
        return Frames(points, false)
    }

    /**
     * Splits [dn]..[df] into [images] sub-ranges of equal harmonic width — equal focus ring
     * rotations — then picks the f stop that the hardest sub-range needs. The near sub-range
     * is always the hardest, so that f stop covers the rest with room to spare.
     */
    fun focusStack(
        l: Double,
        dn: Double,
        df: Double,
        images: Int,
        c: Double,
        wavelengthNm: Double,
    ): Stack {
        val n = images.coerceAtLeast(1)
        val uNear = 1.0 / dn
        val uFar = if (df.isInfinite()) 0.0 else 1.0 / df
        val edges = DoubleArray(n + 1) { uNear + (uFar - uNear) * it / n }

        val focusPoints = ArrayList<Double>(n)
        var fStop = 0.0
        for (i in 1..n) {
            val subNear = 1.0 / edges[i - 1]
            val a = 2.0 / (edges[i - 1] + edges[i])
            focusPoints += a
            val fi = bestFStop(l, a, subNear, wavelengthNm)
            if (!fi.isNaN() && fi > fStop) fStop = fi
        }

        var worst = 0.0
        for (i in 1..n) {
            val a = focusPoints[i - 1]
            val subNear = 1.0 / edges[i - 1]
            val subFar = if (edges[i] <= 0.0) INF else 1.0 / edges[i]
            worst = maxOf(
                worst,
                blurValue(l, fStop, a, subNear, c, wavelengthNm),
                blurValue(l, fStop, a, subFar, c, wavelengthNm),
            )
        }
        return Stack(fStop, focusPoints, worst)
    }
}
