package org.kutner.dofpro.model

/**
 * A lens in the user's bag: what focal lengths it covers and how far it opens and stops
 * down. Selecting one confines the focal length and aperture scales to settings that lens
 * can actually be set to, so the app only ever suggests a shot you could take.
 */
data class Lens(
    val name: String = "Any lens",
    /** Shortest focal length in mm; equal to [maxFocal] on a prime. */
    val minFocal: Double = DofState.MIN_FOCAL,
    val maxFocal: Double = DofState.MAX_FOCAL,
    /** Widest aperture — the smallest f number the lens opens to. */
    val minFStop: Double = DofState.MIN_F,
    /** Narrowest aperture — the largest f number it stops down to. */
    val maxFStop: Double = DofState.MAX_F,
) {
    /** A prime has one focal length, so its scale is a read-out rather than a control. */
    val isZoom: Boolean get() = maxFocal > minFocal * 1.001

    fun clampFocal(mm: Double): Double = mm.coerceIn(minFocal, maxFocal)

    /**
     * The stretch the focal length scale should be drawn over, or null to let it place
     * itself around whatever focal length is set.
     *
     * A zoom gets its own range and nothing else, the way its barrel is engraved: the
     * focal length cannot leave it, so there is nothing beyond it worth scrolling to, and
     * both ends stay on screen where a scale that follows the marker would lose the short
     * end whenever the marker went near the long one.
     *
     * Two lenses get no range. A prime has a single focal length, so there is no stretch
     * to draw — its scale stays a read-out placed around that value. And a nominal lens
     * covering everything from 1 mm to 3 metres is not a barrel to engrave: drawn end to
     * end it would crush every real focal length into the last few pixels, so it too keeps
     * the moving view. [WIDEST_DRAWN] is set well past any real lens — a 22:1 superzoom is
     * about as wide as they come — so only a placeholder like that falls through.
     */
    fun scaleRange(): ClosedFloatingPointRange<Double>? {
        if (!isZoom || minFocal <= 0.0) return null
        if (maxFocal / minFocal > WIDEST_DRAWN) return null
        return minFocal..maxFocal
    }

    fun clampFStop(f: Double): Double = f.coerceIn(minFStop, maxFStop)

    /** "24-70mm f/2.8-22", or "50mm f/1.8-16" for a prime. */
    val specification: String
        get() {
            val focal = if (isZoom) {
                "${formatSig(minFocal)}-${formatSig(maxFocal)}mm"
            } else {
                "${formatSig(minFocal)}mm"
            }
            return "$focal  f/${formatSig(minFStop)}-${formatSig(maxFStop)}"
        }

    companion object {
        /** Widest zoom ratio still drawn end to end; past this the scale follows the marker. */
        const val WIDEST_DRAWN = 40.0

        /** The unconstrained default, for when no particular lens is in mind. */
        val ANY = Lens()

        /**
         * What a newly added lens starts as. The unconstrained range is right for "Any
         * lens" but a poor blank form — somebody adding a lens is describing one they
         * own, so it opens as an ordinary prime for them to correct.
         */
        fun blank(): Lens = Lens(
            name = "New lens",
            minFocal = 50.0,
            maxFocal = 50.0,
            minFStop = 2.0,
            maxFStop = 22.0,
        )

        fun defaults(): List<Lens> = listOf(
            ANY,
            Lens("24mm f/1.4", 24.0, 24.0, 1.4, 16.0),
            Lens("35mm f/2", 35.0, 35.0, 2.0, 22.0),
            Lens("50mm f/1.8", 50.0, 50.0, 1.8, 22.0),
            Lens("85mm f/1.8", 85.0, 85.0, 1.8, 22.0),
            Lens("100mm f/2.8 macro", 100.0, 100.0, 2.8, 32.0),
            Lens("16-35mm f/4", 16.0, 35.0, 4.0, 22.0),
            Lens("24-70mm f/2.8", 24.0, 70.0, 2.8, 22.0),
            Lens("70-200mm f/2.8", 70.0, 200.0, 2.8, 32.0),
            Lens("100-400mm f/5.6", 100.0, 400.0, 5.6, 32.0),
        )
    }
}
