package org.kutner.dofpro.model

/**
 * The f numbers actually engraved on lenses and reported in EXIF.
 *
 * A whole stop is a factor of √2, so the exact values run 1, 1.414, 2, 2.828, 4, 5.657…
 * but no lens is marked that way — they are printed rounded, and rounded by convention
 * rather than arithmetic: √2⁵ is 5.657 yet every lens says 5.6, and √2⁷ is 11.3 yet every
 * lens says 11. These are those conventional markings, so the aperture the app shows is
 * one you can dial in.
 *
 * Each series contains the one above it, so a whole stop stays a whole stop whichever
 * subdivision is selected.
 */
object FStops {

    /** Whole stops. */
    val FULL: List<Double> = listOf(
        0.5, 0.7, 1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0,
        32.0, 45.0, 64.0, 90.0, 128.0, 180.0, 256.0,
    )

    /** Half stops. */
    val HALF: List<Double> = listOf(
        0.5, 0.6, 0.7, 0.8, 1.0, 1.2, 1.4, 1.7, 2.0, 2.4, 2.8, 3.3, 4.0, 4.8,
        5.6, 6.7, 8.0, 9.5, 11.0, 13.0, 16.0, 19.0, 22.0, 27.0, 32.0, 38.0,
        45.0, 54.0, 64.0, 76.0, 90.0, 107.0, 128.0, 152.0, 180.0, 215.0, 256.0,
    )

    /** Third stops. */
    val THIRD: List<Double> = listOf(
        0.5, 0.56, 0.63, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5,
        2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0,
        16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0, 36.0, 40.0, 45.0, 51.0, 57.0, 64.0,
        72.0, 80.0, 90.0, 102.0, 114.0, 128.0, 145.0, 161.0, 180.0, 203.0, 228.0, 256.0,
    )

    fun series(step: ApertureStep): List<Double> =
        if (step == ApertureStep.HALF) HALF else THIRD

    /** Whole stops carry a label on the aperture scale; the subdivisions are bare ticks. */
    fun isWholeStop(f: Double): Boolean = FULL.any { it == f }
}
