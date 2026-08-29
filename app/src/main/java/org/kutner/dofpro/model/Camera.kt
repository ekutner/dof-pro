package org.kutner.dofpro.model

import org.kutner.dofpro.calc.Dof
import kotlin.math.PI

enum class CameraType(val label: String) { DIGITAL("Digital"), FILM("Film") }

enum class FocalMode(val label: String) { ACTUAL("Enter actual"), EQUIV_35("Enter 35mm equivalent") }

/**
 * What the camera can record: how big the frame is, and how finely it is sampled.
 *
 * Nothing here says what counts as sharp. That depends on how large the picture is shown
 * and from how far away, which is a property of the viewing rather than of the camera —
 * see [ViewingTarget], and [circleOfConfusion], which needs both.
 */
data class Camera(
    val name: String = "35mm film",
    val type: CameraType = CameraType.FILM,
    val focalMode: FocalMode = FocalMode.ACTUAL,
    val frameWidthMm: Double = 36.0,
    val frameHeightMm: Double = 24.0,
    val frameWidthPx: Int = 6000,
    val frameHeightPx: Int = 4000,
    /** Film resolution in line pairs/mm; film cameras only. */
    val filmResolution: Double = 100.0,
    val wavelengthNm: Double = 550.0,
) {
    /** Smallest detail the sensor or film can record, in mm. */
    val pixelPitch: Double
        get() = when (type) {
            CameraType.DIGITAL -> frameWidthMm / frameWidthPx.coerceAtLeast(1)
            // One line pair spans two pixels, so a single resolvable detail is half a pair.
            CameraType.FILM -> 1.0 / (2.0 * filmResolution.coerceAtLeast(1.0))
        }

    /** How much the frame must be enlarged relative to full frame 35mm. */
    val cropFactor: Double get() = 36.0 / frameWidthMm.coerceAtLeast(0.1)

    val megapixels: Double
        get() = when (type) {
            CameraType.DIGITAL -> frameWidthPx.toDouble() * frameHeightPx / 1e6
            CameraType.FILM -> {
                val lines = 2.0 * filmResolution
                (frameWidthMm * lines) * (frameHeightMm * lines) / 1e6
            }
        }

    /** Actual focal length to compute with, given what the user typed on the lens scale. */
    fun actualFocalLength(entered: Double): Double =
        if (focalMode == FocalMode.EQUIV_35) entered / cropFactor else entered

    /** Inverse of [actualFocalLength], for showing a value back on the scale. */
    fun enteredFocalLength(actual: Double): Double =
        if (focalMode == FocalMode.EQUIV_35) actual * cropFactor else actual

    companion object {
        /**
         * The bodies the app ships with: a starting point, not a catalogue.
         *
         * Every one of them can be renamed, retuned or deleted, and a camera not on the
         * list can be looked up by name, so this only has to cover the formats someone is
         * likely to be holding: two crops, and full frame across the pixel counts it is
         * sold at. A phone body is deliberately absent — its focal lengths are quoted as
         * 35mm equivalents and its lens is fixed, neither of which the lens list models,
         * so it could only ever be paired with lenses it does not have.
         *
         * The two APS-C entries differ because APS-C is not one size: Canon's is a little
         * smaller than everyone else's, and at the same pixel count that is a different
         * pixel pitch and a different circle of confusion.
         */
        fun defaults(): List<Camera> = listOf(
            Camera(
                name = "Canon APS-C 24MP",
                type = CameraType.DIGITAL,
                frameWidthMm = 22.5, frameHeightMm = 14.8,
                frameWidthPx = 6000, frameHeightPx = 4000,
            ),
            Camera(
                name = "Sony APS-C 24MP",
                type = CameraType.DIGITAL,
                frameWidthMm = 23.6, frameHeightMm = 15.7,
                frameWidthPx = 6000, frameHeightPx = 4000,
            ),
            Camera(
                name = "Full frame 33MP",
                type = CameraType.DIGITAL,
                frameWidthMm = 36.0, frameHeightMm = 24.0,
                frameWidthPx = 7008, frameHeightPx = 4672,
            ),
            Camera(
                name = "Full frame 45MP",
                type = CameraType.DIGITAL,
                frameWidthMm = 36.0, frameHeightMm = 24.0,
                frameWidthPx = 8256, frameHeightPx = 5504,
            ),
            Camera(
                name = "Full frame 61MP",
                type = CameraType.DIGITAL,
                frameWidthMm = 36.0, frameHeightMm = 24.0,
                frameWidthPx = 9504, frameHeightPx = 6336,
            ),
        )
    }
}
