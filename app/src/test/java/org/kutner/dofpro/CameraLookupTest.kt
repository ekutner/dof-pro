package org.kutner.dofpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kutner.dofpro.model.CameraLookup

/**
 * Reading a camera's sensor out of a Wikipedia infobox.
 *
 * The fixtures are the real thing, copied from the articles as they stand, because the
 * whole difficulty here is how many shapes the same fact is written in.
 */
class CameraLookupTest {

    private fun parse(wiki: String) = CameraLookup.parse("test", wiki)

    // ---- The shapes the millimetres come in ------------------------------------------

    @Test
    fun `reads the frame size however the article writes it`() {
        val cases = mapOf(
            "|sensor_size = 35.7&nbsp;× 23.8&nbsp;mm (full frame type)" to (35.7 to 23.8),
            "|sensor_size = 23.8 mm × 15.6 mm" to (23.8 to 15.6),
            "|sensor_size = 17.3×13mm (Four Thirds type)" to (17.3 to 13.0),
            "|sensor_size = Full-frame (36 x 24 mm)" to (36.0 to 24.0),
            "|sensor_size = Full frame (35.9 x 23.9 mm)<br />Nikon FX format" to (35.9 to 23.9),
            "|sensor_size = [[APS-C]] 22.3 x 14.9 mm" to (22.3 to 14.9),
        )
        for ((wiki, want) in cases) {
            val got = parse(wiki)
            assertEquals(wiki, want.first, got.widthMm!!, 1e-9)
            assertEquals(wiki, want.second, got.heightMm!!, 1e-9)
        }
    }

    @Test
    fun `a sensor format on its own is not a measurement`() {
        // The point of the whole exercise. Canon's APS-C is 22.3 x 14.9 mm, Sony's
        // 23.3 x 15.5, Nikon's 23.5 x 15.7, Fujifilm's 23.8 x 15.6 — so "APS-C" names a
        // family and measures nothing. Filling in a nominal figure would be wrong for most
        // cameras that say it, and wrong quietly, which is worse.
        for (wiki in listOf(
            "|sensor_size = APS-C",
            "|sensor = [[APS-C]] CMOS",
            "|sensor_size = Full-frame",
            "|sensor_size = Four Thirds",
            "|sensor_size = 1-inch type",
        )) {
            val got = parse(wiki)
            assertNull(wiki, got.widthMm)
            assertNull(wiki, got.heightMm)
            assertFalse(got.hasFrame)
        }
    }

    @Test
    fun `falls back to the sensor field when there is no sensor size field`() {
        val got = parse("|sensor = CMOS 23.5 mm × 15.6 mm APS-C")
        assertEquals(23.5, got.widthMm!!, 1e-9)
        assertEquals(15.6, got.heightMm!!, 1e-9)
    }

    // ---- Resolution -------------------------------------------------------------------

    @Test
    fun `reads the resolution however the article writes it`() {
        val cases = mapOf(
            "|sensor_size = 35.7 x 23.8 mm\n|res = 9504&nbsp;× 6336 (61 megapixels)" to (9504 to 6336),
            "|sensor_size = 23.8 mm × 15.6 mm\n|res = 40.20 megapixels <br/>7728 x 5152 (3:2)" to (7728 to 5152),
            "|sensor_size = 35.9 x 23.9 mm\n|res = 8,256 x 5,504 (45.4 megapixels)" to (8256 to 5504),
        )
        for ((wiki, want) in cases) {
            val got = parse(wiki)
            assertEquals(wiki, want.first, got.widthPx)
            assertEquals(wiki, want.second, got.heightPx)
        }
    }

    @Test
    fun `a resolution that is not the sensor's shape is thrown away`() {
        // The E-M1 Mark III case: scanning turned up 4096 x 2160, which is a video mode.
        // Pixels are square, so the frame's shape and the image's shape are one shape —
        // when they disagree the resolution is not the sensor's, whatever it is.
        val got = parse("|sensor_size = 17.3 x 13 mm\n|res = 4096 x 2160")
        assertEquals(17.3, got.widthMm!!, 1e-9)
        assertNull("a 16:9 mode on a 4:3 sensor", got.widthPx)
        assertNull(got.heightPx)
        // The frame size survives, because it is the harder number to look up by hand.
        assertTrue(got.hasFrame)
        assertFalse(got.hasPixels)
    }

    @Test
    fun `rounding in a published figure is not treated as a mismatch`() {
        // Fujifilm quote 23.8 x 15.6 mm, which is 1.526 rather than a clean 1.5, and the
        // sensor really is 3:2. A tolerance too tight here would reject good cameras.
        val got = parse("|sensor_size = 23.8 mm × 15.6 mm\n|res = 7728 x 5152")
        assertEquals(7728, got.widthPx)
        assertEquals(5152, got.heightPx)
    }

    @Test
    fun `a screen or thumbnail size is not a resolution`() {
        val got = parse("|sensor_size = 36 x 24 mm\n|res = 640 x 480")
        assertNull(got.widthPx)
    }

    // ---- Nothing is invented ----------------------------------------------------------

    @Test
    fun `an article with no numbers yields none`() {
        val got = parse("|name = Some Camera\n|type = Digital SLR")
        assertFalse(got.hasFrame)
        assertFalse(got.hasPixels)
    }

    @Test
    fun `markup never reaches the numbers`() {
        val got = parse("|sensor_size = {{convert|36|x|24|mm}} [[full-frame]] <ref name=x/>")
        assertEquals(36.0, got.widthMm!!, 1e-9)
        assertEquals(24.0, got.heightMm!!, 1e-9)
    }
}
