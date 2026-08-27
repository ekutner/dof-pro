package org.kutner.dofpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kutner.dofpro.model.Camera
import org.kutner.dofpro.model.CameraType
import org.kutner.dofpro.model.TargetKind
import org.kutner.dofpro.model.UnitSystem
import org.kutner.dofpro.model.ViewingTarget
import org.kutner.dofpro.model.circleOfConfusion

/**
 * Where a picture is looked at, and what that does to the circle of confusion.
 *
 * The camera and the viewing target used to be one object, and the old "sharp image" and
 * "sharp print" methods were two rules. They are now one rule with different terms filled
 * in, so the first thing to check is that the rule still gives the old answers.
 */
class ViewingTargetTest {

    private val film35 = Camera(frameWidthMm = 36.0, filmResolution = 100.0)
    private val d810 = Camera(
        type = CameraType.DIGITAL,
        frameWidthMm = 36.0, frameHeightMm = 24.0,
        frameWidthPx = 7360, frameHeightPx = 4912,
    )
    private val print12at18 = ViewingTarget(
        kind = TargetKind.PRINT,
        widthMm = 12.0 * 25.4,
        viewingDistanceMm = 18.0 * 25.4,
    )
    private val pixelLevel = ViewingTarget(kind = TargetKind.PIXELS)

    // ---- The old methods still fall out of the one rule -------------------------------

    @Test
    fun `a print reproduces the conventional 35mm figure`() {
        assertEquals(0.0314, circleOfConfusion(film35, print12at18), 0.0005)
    }

    @Test
    fun `pixel level reproduces the sharp image figure`() {
        assertEquals(0.0098, circleOfConfusion(d810, pixelLevel), 0.0005)
    }

    @Test
    fun `a print asks less of a camera than its own pixels do`() {
        // Which is why the two methods were ever different, and why taking the coarser of
        // the two limits reproduces both without needing to know which is which.
        assertTrue(circleOfConfusion(d810, print12at18) > circleOfConfusion(d810, pixelLevel))
    }

    // ---- Screens ----------------------------------------------------------------------

    @Test
    fun `a screen is limited by the eye or by its own pixels, whichever is coarser`() {
        val camera = Camera(
            type = CameraType.DIGITAL,
            frameWidthMm = 36.0, frameWidthPx = 8256, frameHeightPx = 5504,
        )
        // A coarse screen close up: its pixels are the limit, not the eye.
        val coarse = ViewingTarget(
            kind = TargetKind.SCREEN,
            widthMm = 600.0, pixelsAcross = 800, viewingDistanceMm = 600.0,
        )
        assertEquals(600.0 / 800, coarse.detailShown, 1e-9)

        // The same screen at the same distance with far more pixels: now the eye is.
        val fine = coarse.copy(pixelsAcross = 8000)
        val eye = 600.0 * (Math.PI / 180.0 / 60.0)
        assertEquals(eye, fine.detailShown, 1e-9)
        assertTrue(fine.detailShown > fine.widthMm / fine.pixelsAcross)

        // And a finer screen can never ask more than the eye can see.
        assertTrue(circleOfConfusion(camera, fine) <= circleOfConfusion(camera, coarse))
    }

    @Test
    fun `a big screen far away and a small one close land in much the same place`() {
        // The reason sharpness is judged by angle: a television is enormous and across the
        // room, a phone is tiny and at arm's bend, and they demand comparable things.
        val camera = Camera(type = CameraType.DIGITAL, frameWidthMm = 36.0, frameWidthPx = 8256)
        val phone = ViewingTarget.defaults().first { it.name.startsWith("Phone") }
        val tv = ViewingTarget.defaults().first { it.name.startsWith("TV 77") }
        val ratio = circleOfConfusion(camera, tv) / circleOfConfusion(camera, phone)
        assertTrue("phone and television differ by $ratio times", ratio in 0.25..4.0)
    }

    @Test
    fun `a screen's width comes from its diagonal and its shape`() {
        // Screens are sold by the diagonal; the optics want the width.
        assertEquals(1217.5, ViewingTarget.widthOf(55.0, 16, 9), 0.5)
        assertEquals(301.5, ViewingTarget.widthOf(14.0, 16, 10), 0.5)
        // A 4:3 tablet is wider than a 16:9 screen of the same diagonal is tall, and
        // narrower than one of the same diagonal is wide.
        assertTrue(ViewingTarget.widthOf(11.0, 4, 3) < ViewingTarget.widthOf(11.0, 16, 9))
    }

    // ---- The two halves are genuinely independent -------------------------------------

    @Test
    fun `the same camera gives different answers on different targets`() {
        // The whole point of the separation: one body, several ways of looking at it, and
        // no need for a second copy of the camera to say so.
        val camera = Camera(type = CameraType.DIGITAL, frameWidthMm = 36.0, frameWidthPx = 8256)
        val cocs = ViewingTarget.defaults().map { circleOfConfusion(camera, it) }
        assertTrue("every target gave the same answer", cocs.distinct().size > 4)
        assertTrue("nothing should be zero or negative", cocs.all { it > 0.0 })
    }

    @Test
    fun `the same target gives different answers for different cameras`() {
        val cocs = Camera.defaults().map { circleOfConfusion(it, print12at18) }
        assertTrue(cocs.distinct().size > 1)
        assertTrue(cocs.all { it > 0.0 })
    }

    @Test
    fun `a bigger sensor needs less enlargement, so it may blur more and still pass`() {
        val fullFrame = Camera(type = CameraType.DIGITAL, frameWidthMm = 36.0, frameWidthPx = 6000)
        val cropped = Camera(type = CameraType.DIGITAL, frameWidthMm = 17.3, frameWidthPx = 6000)
        assertTrue(circleOfConfusion(fullFrame, print12at18) > circleOfConfusion(cropped, print12at18))
    }

    @Test
    fun `a custom target states the figure outright`() {
        val custom = ViewingTarget(kind = TargetKind.CUSTOM, customCoc = 0.02)
        assertEquals(0.02, circleOfConfusion(film35, custom), 1e-12)
        // And nothing about the camera changes it.
        assertEquals(0.02, circleOfConfusion(d810, custom), 1e-12)
    }

    @Test
    fun `allowable blur scales the answer and nothing else does`() {
        val one = print12at18.copy(allowableBlur = 1.0)
        val two = print12at18.copy(allowableBlur = 2.0)
        assertEquals(
            2.0 * circleOfConfusion(film35, one),
            circleOfConfusion(film35, two),
            1e-12,
        )
    }

    @Test
    fun `a list entry carries its viewing distance on the same line`() {
        val phone = ViewingTarget.defaults().first { it.name.startsWith("Phone") }
        val metric = phone.listLabel(UnitSystem.METRIC)
        assertTrue("no distance in \"$metric\"", metric.startsWith(phone.name + " @ "))
        assertTrue(metric.contains("30 cm"))
        assertTrue(phone.listLabel(UnitSystem.IMPERIAL).contains("in"))

        // Pixel level has no viewing geometry, so there is no distance to append.
        val pixels = ViewingTarget.defaults().first { it.kind == TargetKind.PIXELS }
        assertEquals(pixels.name, pixels.listLabel(UnitSystem.METRIC))
    }

    @Test
    fun `no print name repeats the distance the label already gives`() {
        // The names used to read "Print A4 at 16in" and the label would then have said the
        // distance twice over.
        for (t in ViewingTarget.defaults().filter { it.kind == TargetKind.PRINT }) {
            assertTrue("\"${t.name}\" still carries its distance", !t.name.contains(" at "))
        }
    }

    @Test
    fun `a target describes itself in whichever units are in use`() {
        val print = ViewingTarget.defaults().first { it.kind == TargetKind.PRINT }
        assertTrue(print.describe(UnitSystem.METRIC).contains("cm") ||
            print.describe(UnitSystem.METRIC).contains(" m"))
        assertTrue(print.describe(UnitSystem.IMPERIAL).contains("in") ||
            print.describe(UnitSystem.IMPERIAL).contains("ft"))
    }

    @Test
    fun `the shipped list covers both prints and screens`() {
        // They ship as ordinary entries in the list, editable and deletable like any
        // other, rather than hiding behind a preset picker inside the editor.
        val kinds = ViewingTarget.defaults().groupBy { it.kind }
        assertTrue("no prints", (kinds[TargetKind.PRINT]?.size ?: 0) >= 4)
        assertTrue("no screens", (kinds[TargetKind.SCREEN]?.size ?: 0) >= 5)
        // And short enough to pick from without scrolling forever — it is a dropdown on
        // the main screen, not a catalogue.
        assertTrue("the list has grown unwieldy", ViewingTarget.defaults().size <= 12)
        assertTrue("no pixel-level entry", kinds.containsKey(TargetKind.PIXELS))
        // Phone through to television, so there is something near whatever anyone uses.
        val widths = ViewingTarget.defaults()
            .filter { it.kind == TargetKind.SCREEN }
            .map { it.widthMm }
        assertTrue("screens should span from a phone to a TV", widths.max() / widths.min() > 15.0)
    }

    @Test
    fun `every stock target is usable with every stock camera`() {
        for (camera in Camera.defaults()) {
            for (target in ViewingTarget.defaults()) {
                val coc = circleOfConfusion(camera, target)
                assertTrue("${camera.name} on ${target.name} gave $coc", coc > 0.0 && coc.isFinite())
                for (units in UnitSystem.entries) {
                    assertTrue(
                        "${target.name} has no description in $units",
                        target.describe(units).isNotBlank(),
                    )
                }
            }
        }
    }
}
