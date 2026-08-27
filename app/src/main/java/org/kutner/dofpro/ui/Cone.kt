package org.kutner.dofpro.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Blur at which the cone reaches the full width of the column. Measured off the desktop
 * app, whose line width works out at very close to blur/28 of the column width.
 */
private const val FULL_WIDTH_BLUR = 28.0

/**
 * Renders the double cone: the blurred image of a very narrow vertical line, drawn at
 * every distance in view. The width of the line is proportional to the amount of blur
 * and its colour follows the blur colour coding, so the picture pinches to a bright
 * waist at the plane of focus and flares out on either side.
 *
 * Drawn small and scaled up — the figure is soft by nature, and this keeps dragging fluid.
 */
fun buildCone(
    width: Int,
    height: Int,
    distanceAt: (Double) -> Double,
    blurAt: (Double) -> Double,
): ImageBitmap {
    val w = width.coerceIn(8, 256)
    val h = height.coerceIn(8, 1024)
    val pixels = IntArray(w * h)
    val half = w / 2.0

    for (j in 0 until h) {
        val blur = blurAt(distanceAt((j + 0.5) / h))
        val argb = blurColor(blur).toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        val spread = if (blur.isNaN()) 1.0 else (blur / FULL_WIDTH_BLUR).coerceIn(0.0, 1.0)
        val lineWidth = (spread * w).coerceAtLeast(1.6)
        val edge = lineWidth / 2.0
        val row = j * w

        val from = (half - edge).toInt().coerceAtLeast(0)
        val to = (half + edge).roundToInt().coerceAtMost(w - 1)
        for (i in from..to) {
            val t = (i + 0.5 - half) / edge
            if (t <= -1.0 || t >= 1.0) continue
            val a = ((1.0 - t * t).pow(0.8) * 255.0).roundToInt().coerceIn(0, 255)
            pixels[row + i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    val image = ImageBitmap(w, h)
    image.asAndroidBitmap().setPixels(pixels, 0, w, 0, 0, w, h)
    return image
}
