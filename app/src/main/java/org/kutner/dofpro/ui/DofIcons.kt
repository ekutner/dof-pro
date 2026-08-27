package org.kutner.dofpro.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of Material icons this app needs that the core icon set does not carry.
 *
 * Drawn from their published path data rather than pulled from a dependency, because the
 * three of them live in material-icons-extended — some thousands of glyphs and several
 * megabytes to obtain three. These are Google's own outlines, stated as paths, and they
 * tint and scale like any icon from the set.
 */

/** Material Symbols `visibility`. Shows the figures behind a reading. */
val EyeIcon: ImageVector by lazy {
    icon24(
        "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5" +
            "c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5" +
            "-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
        name = "Visibility",
    )
}

/** Material Symbols `settings_photo_camera`. A camera with a gear: what is set up to shoot. */
val CameraSettingsIcon: ImageVector by lazy {
    symbol960(
        "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27" +
            "q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128" +
            "q13 5 24.5 12t22.5 15l119-50 110 190-85 65H616q-14-43-50-71.5T482-620" +
            "q-58 0-99 41t-41 99q0 48 27 84t71 50v266h-70Zm210 0q-25 0-42.5-17.5T520-140" +
            "v-200q0-25 17.5-42.5T580-400h60l40-40h80l40 40h60q25 0 42.5 17.5T920-340v200" +
            "q0 25-17.5 42.5T860-80H580Zm140-80q33 0 56.5-23.5T800-240q0-33-23.5-56.5" +
            "T720-320q-33 0-56.5 23.5T640-240q0 33 23.5 56.5T720-160Z",
        name = "SettingsPhotoCamera",
    )
}

/** Material Symbols `stacks`. Frames piled one on the next — a focus stack, drawn. */
val StacksIcon: ImageVector by lazy {
    symbol960(
        "M480-400 40-640l440-240 440 240-440 240Zm0 160L63-467l84-46 333 182 333-182 84 46" +
            "-417 227Zm0 160L63-307l84-46 333 182 333-182 84 46L480-80Z",
        name = "Stacks",
    )
}

/** A classic Material icon: a 24 unit viewport with the origin at the top left. */
private fun icon24(path: String, name: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
    }.build()

/**
 * A Material Symbol: a 960 unit viewport, but drawn against `0 -960 960 960` — the origin
 * sits on the *bottom* edge and the artwork runs upwards into negative y. [ImageVector] has
 * no viewBox origin to set, so the whole thing is shifted down a viewport instead, which
 * puts it back where a top-left origin expects it.
 */
private fun symbol960(path: String, name: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addGroup(translationY = 960f)
        addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        clearGroup()
    }.build()
