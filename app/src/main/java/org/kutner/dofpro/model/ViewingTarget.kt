package org.kutner.dofpro.model

import kotlin.math.PI
import kotlin.math.sqrt

/** How a picture is going to be looked at, which is what decides how sharp is sharp. */
enum class TargetKind(val label: String) {
    PRINT("Print"),
    SCREEN("Screen"),
    PIXELS("Pixel level"),
    CUSTOM("Custom CoC"),
}

/**
 * Where a picture is going to be seen, and from how far away.
 *
 * Separate from the camera because the two are separate things. A camera records a certain
 * amount of detail; whether any of it is *visible* depends entirely on how big the picture
 * is shown and how close the viewer stands. The same file is critically sharp on a phone
 * and obviously soft as a metre-wide print, and one camera can be used for both, so the
 * viewing conditions cannot sensibly live on the camera.
 *
 * The four kinds are one model with different terms filled in, not four rules:
 *
 *  - **Print** — the eye's own limit at the viewing distance, and nothing else. Ink has no
 *    pixel size worth speaking of.
 *  - **Screen** — the eye's limit *or* one display pixel, whichever is coarser. A retina
 *    display viewed at arm's length is limited by the eye; the same picture across a
 *    television seen from the sofa is limited by the eye again; a low resolution projector
 *    up close is limited by its pixels. Taking the larger of the two covers all three.
 *  - **Pixel level** — no viewing geometry at all: judged at 100%, one sensor pixel at a
 *    time, which is what the reference app calls a Sharp Image.
 *  - **Custom** — a circle of confusion stated outright, for when a figure is simply known.
 */
data class ViewingTarget(
    val name: String = "Print 12in at 18in",
    val kind: TargetKind = TargetKind.PRINT,
    /** How wide the picture is shown, in mm: the print's width, or the screen's. */
    val widthMm: Double = 304.8,
    /** How far away it is looked at from, in mm. */
    val viewingDistanceMm: Double = 457.2,
    /** Pixels across the display. Screens only. */
    val pixelsAcross: Int = 1920,
    /** The smallest angle the viewer can resolve, in arc minutes. 1.0 is normal eyesight. */
    val visualResolution: Double = 1.0,
    /** How many just-resolvable details of blur still count as sharp. */
    val allowableBlur: Double = 2.0,
    /** A circle of confusion stated outright, for [TargetKind.CUSTOM]. */
    val customCoc: Double = 0.03,
) {
    /** The smallest angle the viewer resolves, in radians. */
    private val eyeAngle: Double get() = visualResolution * (PI / 180.0) / 60.0

    /**
     * The smallest detail visible on the print or screen itself, in mm.
     *
     * Zero for the pixel-level target, which has no display to speak of — there the camera
     * alone decides, and [circleOfConfusion] takes care of it.
     */
    val detailShown: Double
        get() = when (kind) {
            TargetKind.PRINT -> viewingDistanceMm * eyeAngle
            TargetKind.SCREEN -> maxOf(
                viewingDistanceMm * eyeAngle,
                widthMm / pixelsAcross.coerceAtLeast(1),
            )
            TargetKind.PIXELS, TargetKind.CUSTOM -> 0.0
        }

    /** How many times the frame is enlarged to fill this target. */
    fun magnification(frameWidthMm: Double): Double =
        if (frameWidthMm > 0.0) widthMm / frameWidthMm else 1.0

    /** What the smallest visible detail measures back at the sensor, in mm. */
    fun detailAtSensor(frameWidthMm: Double): Double =
        if (kind == TargetKind.PIXELS || kind == TargetKind.CUSTOM) 0.0
        else detailShown / magnification(frameWidthMm)

    /**
     * How it reads in a list: the name, and the distance it is seen from.
     *
     * The distance rides on the name rather than sitting on a line of its own, because it
     * is the half of a viewing target that is not in its name and the half that changes
     * the answer most — the same screen at arm's length and across a room are different
     * targets. Everything else about it is in the editor, where it can be changed.
     *
     * In whatever units the user reads distances in. The names carry inches because that
     * is how screens and most print sizes are sold the world over, but a distance is a
     * measurement like any other and should read the way the rest of the app does.
     */
    fun listLabel(units: UnitSystem): String = when (kind) {
        TargetKind.PRINT, TargetKind.SCREEN ->
            "$name @ ${formatDistance(viewingDistanceMm, units)}"
        TargetKind.PIXELS, TargetKind.CUSTOM -> name
    }

    /** The fuller description, for the editor's own read-out. */
    fun describe(units: UnitSystem): String = when (kind) {
        TargetKind.PRINT ->
            "${formatDistance(widthMm, units)} wide, seen from " +
                formatDistance(viewingDistanceMm, units)
        TargetKind.SCREEN ->
            "${formatDistance(widthMm, units)} wide · $pixelsAcross px · seen from " +
                formatDistance(viewingDistanceMm, units)
        TargetKind.PIXELS -> "Judged at 100%, one sensor pixel at a time"
        TargetKind.CUSTOM -> "CoC ${formatSig(customCoc, 3)} mm"
    }

    companion object {
        /**
         * The width of a display of [diagonalInches] with an aspect of [w] by [h], in mm.
         * Screens are sold by their diagonal, and the optics want the width.
         */
        fun widthOf(diagonalInches: Double, w: Int, h: Int): Double =
            diagonalInches * 25.4 * w / sqrt((w * w + h * h).toDouble())

        private fun inches(v: Double) = v * 25.4

        /**
         * Somewhere to start: a short list of the sizes people actually look at pictures
         * on, kept short on purpose. It is a picker on the main screen, so every entry
         * costs a line of scrolling — and each of these is an ordinary entry the user can
         * rename, retune or delete, so the list is a starting point rather than a menu of
         * everything.
         *
         * The viewing distances are the ordinary ones for each — a phone at arm's bend, a
         * laptop at desk distance, a television across a room — because that is what makes
         * a 65 inch screen and a 6 inch one comparable at all. A television is enormous and
         * far away; a phone is tiny and close; they land in much the same place, which is
         * the whole point of judging sharpness by angle rather than by size.
         */
        fun defaults(): List<ViewingTarget> = listOf(
            print("Print 8x10in", 8.0, 14.0),
            print("Print 12x18in", 12.0, 18.0),
            print("Print A4", 297.0 / 25.4, 16.0),
            print("Print A3", 420.0 / 25.4, 20.0),
            screen("Phone 6.1in", 6.1, 9, 19, 1179, 300.0),
            screen("Laptop 14in", 14.0, 16, 10, 1920, 500.0),
            screen("Desktop 21in 1080p", 21.0, 16, 9, 1920, 600.0),
            screen("Desktop 27in 4K", 27.0, 16, 9, 3840, 600.0),
            screen("TV 43in 4K", 43.0, 16, 9, 3840, 2000.0),
            screen("TV 77in 4K", 77.0, 16, 9, 3840, 3400.0),
            ViewingTarget(name = "Pixel level", kind = TargetKind.PIXELS),
        )

        private fun print(name: String, widthInches: Double, distanceInches: Double) =
            ViewingTarget(
                name = name,
                kind = TargetKind.PRINT,
                widthMm = inches(widthInches),
                viewingDistanceMm = inches(distanceInches),
            )

        private fun screen(
            name: String,
            diagonalInches: Double,
            aspectW: Int,
            aspectH: Int,
            pixels: Int,
            distanceMm: Double,
        ) = ViewingTarget(
            name = name,
            kind = TargetKind.SCREEN,
            widthMm = widthOf(diagonalInches, aspectW, aspectH),
            viewingDistanceMm = distanceMm,
            pixelsAcross = pixels,
        )
    }
}

/**
 * The blur that still counts as sharp, in mm at the sensor — the circle of confusion, and
 * the one number the whole calculator turns on.
 *
 * It takes both halves of the problem, which is why it lives here rather than on either.
 * The detail that matters is the coarsest of the two limits: the camera cannot record
 * anything finer than one of its own pixels, and the viewer cannot see anything finer than
 * the print or screen shows at that distance. Blur below whichever is coarser is invisible,
 * so that is the one that sets the criterion, and [ViewingTarget.allowableBlur] says how
 * many of them are still acceptable.
 *
 * Taking the coarser of the two is what makes the old Sharp Print and Sharp Image methods
 * fall out of a single rule rather than needing to be cases: a print viewed at arm's length
 * asks so much less than the sensor can give that the print wins, while a pixel-level
 * target shows nothing at all and leaves the sensor to decide.
 */
fun circleOfConfusion(camera: Camera, target: ViewingTarget): Double {
    if (target.kind == TargetKind.CUSTOM) return target.customCoc.coerceAtLeast(1e-6)
    val limit = maxOf(target.detailAtSensor(camera.frameWidthMm), camera.pixelPitch)
    return (target.allowableBlur * limit).coerceAtLeast(1e-6)
}
