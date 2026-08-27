package org.kutner.dofpro.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The camera-like palette the scales are drawn from, in both themes.
 *
 * Held as one flag rather than passed down, because these colours are read from inside
 * `Canvas` draw lambdas all over the instrument and threading a palette through every one
 * of them would be noise. It is snapshot state, so everything drawn from it redraws when
 * the theme changes — the flag is not a plain field for exactly that reason.
 *
 * Set by [DofTheme] and nothing else.
 */
object Palette {

    internal var dark by mutableStateOf(true)

    private fun tone(onDark: Long, onLight: Long): Color =
        Color(if (dark) onDark else onLight)

    val Window: Color get() = tone(0xFF2B2B2B, 0xFFE3E5E8)
    val Background: Color get() = tone(0xFF000000, 0xFFFFFFFF)
    val Panel: Color get() = tone(0xFF0A0A0A, 0xFFFCFCFC)
    val PanelBorder: Color get() = tone(0xFF3C3C3C, 0xFFC4C7CC)
    val ToolBar: Color get() = tone(0xFF3C3C3C, 0xFFD5D8DD)
    val ToolBarButton: Color get() = tone(0xFF505050, 0xFFBFC3C9)
    val ToolBarButtonActive: Color get() = tone(0xFFF0F0F0, 0xFF2A2D31)
    val Text: Color get() = tone(0xFFFFFFFF, 0xFF15181B)
    val DimText: Color get() = tone(0xFFBBBBBB, 0xFF5A5E63)
    val Tick: Color get() = tone(0xFFFFFFFF, 0xFF15181B)

    /** Headings naming what a scale's two sides measure; quieter than the graduations. */
    val AxisName: Color get() = tone(0xFF9AA0A6, 0xFF6B7075)
    val Marker: Color get() = tone(0xFFFF0000, 0xFFC70000)
    val MarkerLocked: Color get() = tone(0xFFFFFFFF, 0xFF2A2D31)

    /** The subject distance — the one line only the user moves. */
    val SubjectLine: Color get() = tone(0xFF2E9BFF, 0xFF0B63C4)

    /** The near and far edges of the acceptable depth of field. */
    val LimitLine: Color get() = tone(0xFFFF0000, 0xFFC70000)

    /** The hyperfocal distance, which is only ever calculated. */
    val HyperfocalLine: Color get() = tone(0xFF00E000, 0xFF0A8A0A)

    /** Where to focus each frame of a focus stack. */
    val StackLine: Color get() = tone(0xFFFFD000, 0xFFA8730A)
}

/**
 * Blur is colour coded as the reference manual specifies, running from "sharp" through
 * yellow and red to blue:
 *
 *   < 1      sharp
 *   1 - 2    sharp to yellow
 *   2 - 10   yellow to red
 *   10 - 20  red to magenta
 *   20 - 40  magenta to blue
 *   > 40     blue
 *
 * The manual's "sharp" is white, which is only meaningful against a dark ground. On a
 * light one white says nothing at all, so the light theme reads the scale as ink rather
 * than as light: sharp is near-black and every stop after it is darkened enough to hold
 * its contrast. The order and the meaning are unchanged — only what "nothing" looks like.
 */
fun blurColor(blur: Double): Color {
    val sharp = if (Palette.dark) Color.White else Color(0xFF1A1C1E)
    if (blur.isNaN()) return sharp
    val yellow = if (Palette.dark) Color(0xFFFFFF00) else Color(0xFFA8730A)
    val red = if (Palette.dark) Color(0xFFFF0000) else Color(0xFFC00000)
    val magenta = if (Palette.dark) Color(0xFFFF00FF) else Color(0xFF9C009C)
    val blue = if (Palette.dark) Color(0xFF4040FF) else Color(0xFF2626B8)
    return when {
        blur <= 1.0 -> sharp
        blur <= 2.0 -> lerpColor(sharp, yellow, (blur - 1.0) / 1.0)
        blur <= 10.0 -> lerpColor(yellow, red, (blur - 2.0) / 8.0)
        blur <= 20.0 -> lerpColor(red, magenta, (blur - 10.0) / 10.0)
        blur <= 40.0 -> lerpColor(magenta, blue, (blur - 20.0) / 20.0)
        else -> blue
    }
}

private fun lerpColor(a: Color, b: Color, t: Double): Color {
    val f = t.coerceIn(0.0, 1.0).toFloat()
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = 1f,
    )
}
