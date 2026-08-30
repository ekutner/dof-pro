package org.kutner.dofpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.kutner.dofpro.calc.Dof
import org.kutner.dofpro.model.ApertureStep
import org.kutner.dofpro.model.Camera
import org.kutner.dofpro.model.CameraType
import org.kutner.dofpro.model.TargetKind
import org.kutner.dofpro.model.ViewingTarget
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.Lens
import org.kutner.dofpro.model.Notice
import org.kutner.dofpro.model.Settings
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * The interaction contract between the four scales:
 *
 *  - focal length and the subject distance are the user's alone
 *  - the aperture and the depth of field limits each determine the other
 *  - the blur read-out and the hyperfocal distance are always calculated
 */
class StateTest {

    private val ft = 304.8

    /**
     * How these tests judge sharpness, now that viewing is separate from the camera. The
     * 12 inch print at 18 inches is the condition the reference figures were taken under,
     * so keeping it here keeps every number in this file comparable with them.
     */
    private val PRINT_12_AT_18 = ViewingTarget(
        kind = TargetKind.PRINT,
        widthMm = 12.0 * 25.4,
        viewingDistanceMm = 18.0 * 25.4,
    )

    private fun state(): DofState = DofState(
        Settings(
            cameras = listOf(
                Camera(
                    name = "test",
                    type = CameraType.FILM,
                    frameWidthMm = 36.0,
                    frameHeightMm = 24.0,
                ),
            ),
            targets = listOf(PRINT_12_AT_18),
            focalLength = 50.0,
            fStop = 4.0,
            subject = 10.0 * ft,
        )
    )

    // ---- Aperture drives the limits ----------------------------------------------

    @Test
    fun `stopping down widens the depth of field and leaves the subject alone`() {
        val s = state()
        val wide = s.compute()
        s.fStop = 16.0
        val narrow = s.compute()

        assertEquals(10.0 * ft, narrow.subject, 1e-9)
        assertTrue(narrow.near!! < wide.near!!)
        assertTrue(narrow.far!! > wide.far!!)
        // The hyperfocal distance is recalculated too, and comes closer.
        assertTrue(narrow.hyperfocal < wide.hyperfocal)
    }

    @Test
    fun `changing focal length moves the limits but never the aperture or subject`() {
        val s = state()
        val before = s.compute()
        s.changeFocalLength(100.0)

        assertEquals(4.0, s.fStop, 1e-9)
        assertEquals(10.0 * ft, s.subject, 1e-9)
        // A longer lens at the same aperture and distance has less depth of field.
        val after = s.compute()
        assertTrue(after.far!! - after.near!! < before.far!! - before.near!!)
    }

    @Test
    fun `moving the subject leaves the aperture alone`() {
        val s = state()
        s.moveSubject(20.0 * ft)
        assertEquals(4.0, s.fStop, 1e-9)
        assertEquals(20.0 * ft, s.compute().subject, 1e-9)
    }

    // ---- The limits drive the aperture -------------------------------------------

    @Test
    fun `dragging the near limit closer stops the lens down`() {
        val s = state()
        val start = s.compute()
        s.dragNearLimit(start.near!! * 0.5)
        assertTrue("expected a smaller aperture, got f/${s.fStop}", s.fStop > 4.0)
        // The subject and focal length are untouched — only the aperture moved.
        assertEquals(10.0 * ft, s.subject, 1e-9)
        assertEquals(50.0, s.focalLength, 1e-9)
    }

    @Test
    fun `dragging the near limit away from the camera opens the lens up`() {
        val s = state()
        s.fStop = 16.0
        val start = s.compute()
        s.dragNearLimit(start.near!! * 1.5)
        assertTrue("expected a wider aperture, got f/${s.fStop}", s.fStop < 16.0)
    }

    @Test
    fun `dragging the far limit outwards stops the lens down`() {
        val s = state()
        val start = s.compute()
        s.dragFarLimit(start.far!! * 1.5)
        assertTrue(s.fStop > 4.0)
    }

    @Test
    fun `a dragged limit always lands on a real f stop`() {
        val s = state()
        val stops = s.apertureStops()
        for (factor in listOf(0.2, 0.5, 0.8, 0.95, 1.2, 2.0, 5.0)) {
            val start = s.compute()
            s.dragNearLimit(start.near!! * factor)
            assertTrue(
                "f/${s.fStop} is not a selectable stop",
                stops.any { abs(it - s.fStop) < 1e-9 },
            )
        }
    }

    @Test
    fun `the limits move in jumps, so a small drag either holds or steps a whole stop`() {
        val s = state()
        s.apertureStep = ApertureStep.HALF
        val start = s.compute()

        // Nudging the near limit by a hair should not move the aperture at all.
        s.dragNearLimit(start.near!! * 1.001)
        assertEquals(4.0, s.fStop, 1e-9)

        // Pulling it far enough lands on the next half stop, not somewhere between.
        s.dragNearLimit(start.near * 0.7)
        assertNotEquals(4.0, s.fStop, 1e-9)
        val stops = s.apertureStops()
        assertTrue(stops.any { abs(it - s.fStop) < 1e-9 })
    }

    @Test
    fun `a fresh install works in third stops`() {
        // The finer of the two, and what a modern body's ring actually clicks in — so it
        // is what someone who never opens Settings gets.
        assertEquals(ApertureStep.THIRD, Settings().apertureStep)
        assertEquals(ApertureStep.THIRD, DofState().apertureStep)
        // And it survives a round trip rather than being re-defaulted on the way back.
        val s = state()
        s.apertureStep = ApertureStep.HALF
        assertEquals(ApertureStep.HALF, DofState(s.toSettings()).apertureStep)
    }

    @Test
    fun `third stops offer finer control of the limits than half stops`() {
        val half = state().apply { apertureStep = ApertureStep.HALF }
        val third = state().apply { apertureStep = ApertureStep.THIRD }
        assertTrue(third.apertureStops().size > half.apertureStops().size)
    }

    // ---- Read-outs ----------------------------------------------------------------

    @Test
    fun `blur at the subject is pure diffraction and climbs with the f number`() {
        val s = state()
        val wide = s.compute().blurAtSubject
        s.fStop = 22.0
        val stopped = s.compute().blurAtSubject
        assertTrue(stopped > wide)
        // Doubling the f number doubles diffraction blur.
        s.fStop = 8.0
        val at8 = s.compute().blurAtSubject
        s.fStop = 16.0
        assertEquals(2.0 * at8, s.compute().blurAtSubject, 1e-9)
    }

    @Test
    fun `past the diffraction limit there are no limits left to draw`() {
        val s = state()
        s.fStop = 32.0
        val r = s.compute()
        assertTrue(r.blurAtSubject > r.sharpBlur)
        assertNull(r.near)
        assertNull(r.far)
        assertTrue(r.hyperfocal.isInfinite())
        // And dragging a limit cannot revive them, because no stop can deliver one.
        val before = s.fStop
        s.dragNearLimit(5.0 * ft)
        assertTrue(s.fStop <= before)
    }

    // ---- The limits always straddle the subject -----------------------------------

    @Test
    fun `the limits bracket the subject at every distance the scale can reach`() {
        val s = state()
        var d = DofState.MIN_DISTANCE
        while (d <= DofState.MAX_DISTANCE) {
            for (stop in listOf(1.4, 4.0, 11.0, 22.0)) {
                s.fStop = stop
                s.moveSubject(d)
                val r = s.compute()
                val near = r.near
                val far = r.far
                if (near != null) {
                    assertTrue(
                        "near ${near} is not in front of subject ${r.subject} " +
                            "at f/$stop, requested $d mm",
                        near < r.subject,
                    )
                }
                if (far != null) {
                    assertTrue(
                        "far ${far} is not behind subject ${r.subject} " +
                            "at f/$stop, requested $d mm",
                        far > r.subject,
                    )
                }
            }
            d *= 1.3
        }
    }

    @Test
    fun `the subject cannot be placed inside the focal length`() {
        val s = state()
        // A 50mm lens forms no image of anything at 20mm; asking for it must not produce
        // a near limit sitting behind the subject.
        s.moveSubject(20.0)
        assertTrue(s.subject > s.effectiveFocal)
        val r = s.compute()
        assertTrue(r.near!! < r.subject)
        assertTrue(r.far!! > r.subject)
    }

    @Test
    fun `a longer lens pushes a too-close subject out to its own minimum`() {
        val s = state()
        // Snapping rounds in the display unit, so this lands near 200mm rather than on it.
        s.moveSubject(200.0)
        assertEquals(200.0, s.subject, 5.0)

        s.changeFocalLength(400.0)
        assertTrue(
            "subject ${s.subject} should have been pushed past ${s.minSubject}",
            s.subject >= s.minSubject,
        )
        val r = s.compute()
        assertTrue(r.near!! < r.subject)
        assertTrue(r.far!! > r.subject)
    }

    @Test
    fun `a teleconverter also raises the closest the subject may stand`() {
        val s = state()
        val bare = s.minSubject
        s.teleconverter = org.kutner.dofpro.model.Teleconverter.X2
        assertEquals(2.0 * bare, s.minSubject, 1e-9)
    }

    // ---- Real f stops --------------------------------------------------------------

    @Test
    fun `the selectable apertures are the ones engraved on lenses`() {
        val s = state()
        s.apertureStep = ApertureStep.THIRD
        val stops = s.apertureStops()
        listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0).forEach {
            assertTrue("f/$it should be selectable", stops.contains(it))
        }
        // Nothing like 2.8284 or 5.657 ever reaches the display.
        stops.forEach {
            val decimals = if (it < 10.0) 2 else 0
            val rounded = Math.round(it * Math.pow(10.0, decimals.toDouble())) /
                Math.pow(10.0, decimals.toDouble())
            assertEquals(rounded, it, 1e-9)
        }
    }

    @Test
    fun `half stops are a subset of third stops only where they should be`() {
        val s = state()
        s.apertureStep = ApertureStep.HALF
        val half = s.apertureStops()
        s.apertureStep = ApertureStep.THIRD
        val third = s.apertureStops()
        // Every whole stop appears in both, so a whole stop stays whole either way.
        listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0).forEach {
            assertTrue(half.contains(it))
            assertTrue(third.contains(it))
        }
    }

    @Test
    fun `a calculated aperture is also a real engraved stop`() {
        val s = state()
        val start = s.compute()
        s.dragNearLimit(start.near!! * 0.6)
        assertTrue(
            "f/${s.fStop} is not an engraved marking",
            s.apertureStops().contains(s.fStop),
        )
    }

    // ---- The distance window --------------------------------------------------------

    /** What fraction of the scale's height the two red lines are apart. */
    private fun dofShare(s: DofState): Double {
        val r = s.compute()
        val w = s.distanceWindow(r)
        return ln(r.far!! / r.near!!) / ln(w.hi / w.lo)
    }

    @Test
    fun `the depth of field takes a quarter of the scale, whatever the settings`() {
        val s = state()
        for (distance in listOf(0.5, 1.0, 4.0, 10.0, 50.0, 200.0)) {
            for (stop in listOf(1.4, 2.8, 5.6, 11.0)) {
                for (focal in listOf(24.0, 50.0, 200.0)) {
                    s.changeFocalLength(focal)
                    s.fStop = stop
                    s.moveSubject(distance * ft)
                    val r = s.compute()
                    // Only meaningful while the far limit is still finite.
                    if (r.near == null || r.far?.isFinite() != true) continue
                    val w = s.distanceWindow(r)
                    assertTrue("subject outside window", r.subject in w.lo..w.hi)
                    val where = "at ${focal}mm f/$stop on a ${distance}ft subject"
                    val span = ln(w.hi / w.lo)
                    when {
                        // Opened as wide as it goes: a depth of field this deep gets more
                        // than its quarter rather than less.
                        span >= DofState.MAX_WINDOW_SPAN - 1e-9 ->
                            assertTrue("$where: share shrank", dofShare(s) >= 0.25)
                        else ->
                            assertEquals(where, DofState.DOF_SHARE_OF_SCALE, dofShare(s), 0.001)
                    }
                }
            }
        }
    }

    @Test
    fun `the subject sits at the centre of the scale`() {
        val s = state()
        s.moveSubject(4.0 * ft)
        val r = s.compute()
        val w = s.distanceWindow(r)
        // Equal amounts of out-of-focus context above and below.
        assertEquals(ln(r.subject / w.lo), ln(w.hi / r.subject), 1e-9)
    }

    @Test
    fun `close subjects get a tighter window than distant ones`() {
        val s = state()
        s.moveSubject(1.0 * ft)
        val close = s.distanceWindow(s.compute())
        s.moveSubject(100.0 * ft)
        val distant = s.distanceWindow(s.compute())
        assertTrue(close.hi < distant.hi)
        assertTrue(close.lo < distant.lo)
    }

    @Test
    fun `a depth of field running to infinity settles at the widest view`() {
        val s = state()
        s.moveSubject(200.0 * ft)
        s.fStop = 16.0
        val r = s.compute()
        assertTrue(r.far!!.isInfinite())

        val w = s.distanceWindow(r)
        assertEquals(DofState.MAX_WINDOW_SPAN, ln(w.hi / w.lo), 1e-9)
        // The near limit — the only limit that still means anything — stays in view, and
        // the subject keeps its place at the centre.
        assertTrue(r.near!! in w.lo..w.hi)
        assertEquals(ln(r.subject / w.lo), ln(w.hi / r.subject), 1e-9)
    }

    @Test
    fun `the view follows the subject continuously, with no edge to stop at`() {
        // What a drag does: many small steps, each one a fixed fraction of the scale as
        // it is drawn at that moment. The view must keep up the whole way rather than
        // running out at the edge of wherever it started.
        val s = state()
        val startWindow = s.distanceWindow(s.compute())
        var carried = s.subject

        repeat(400) {
            val w = s.distanceWindow(s.compute())
            // One "pixel" of drag on a 1000px scale, outward.
            carried *= Math.exp(ln(w.hi / w.lo) / 1000.0)
            s.moveSubject(carried)
        }

        // It travelled far past the range it started in, and the view came along.
        assertTrue("subject barely moved: ${s.subject / ft} ft", s.subject > startWindow.hi)
        val end = s.distanceWindow(s.compute())
        assertTrue(s.subject in end.lo..end.hi)
        assertTrue("view did not follow", end.lo > startWindow.lo)
        assertEquals(DofState.DOF_SHARE_OF_SCALE, dofShare(s), 0.001)
    }

    @Test
    fun `the view does not jump as the subject crosses the hyperfocal distance`() {
        // The far limit runs away to infinity here, so an exact quarter share would need
        // an infinitely wide view. Capping the span rather than special-casing infinity
        // is what keeps the crossing smooth.
        val s = state()
        s.fStop = 4.0
        val h = s.compute().hyperfocal
        assertTrue(h.isFinite())

        var previous: Double? = null
        var crossed = false
        var step = 0.90
        while (step <= 1.30) {
            s.moveSubject(h * step)
            val w = s.distanceWindow(s.compute())
            val span = ln(w.hi / w.lo)
            if (s.compute().far?.isFinite() == false) crossed = true
            previous?.let {
                assertTrue(
                    "view jumped from $it to $span at ${step}x hyperfocal",
                    abs(span - it) < 0.05,
                )
            }
            previous = span
            step += 0.005
        }
        assertTrue("never actually reached infinity", crossed)
    }

    @Test
    fun `the view widens smoothly all the way in to the hyperfocal distance`() {
        val s = state()
        s.fStop = 4.0
        val h = s.compute().hyperfocal
        var previous: Double? = null
        var step = 0.2
        while (step <= 1.0) {
            s.moveSubject(h * step)
            val span = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
            previous?.let {
                assertTrue("view narrowed while backing away", span >= it - 1e-9)
                assertTrue("view jumped at ${step}x hyperfocal", span - it < 0.6)
            }
            previous = span
            step += 0.02
        }
    }

    /** Where the subject line is drawn, as a fraction from the top of the scale. */
    private fun subjectPosition(s: DofState): Double {
        val r = s.compute()
        val w = s.distanceWindow(r)
        return 1.0 - ln(r.subject / w.lo) / ln(w.hi / w.lo)
    }

    @Test
    fun `the subject line travels with the finger before the view takes over`() {
        val s = state()
        assertEquals(0.5, subjectPosition(s), 1e-9)

        // A short drag: the line itself should move, and by the amount dragged.
        s.nudgeSubjectAnchor(0.1)
        assertEquals(0.6, subjectPosition(s), 1e-9)
        s.nudgeSubjectAnchor(-0.25)
        assertEquals(0.35, subjectPosition(s), 1e-9)
    }

    @Test
    fun `the subject line stops at the edge band and hands over to the view`() {
        val s = state()
        // Far more movement than the band allows, in both directions.
        s.nudgeSubjectAnchor(2.0)
        assertEquals(DofState.MAX_ANCHOR, subjectPosition(s), 1e-9)
        s.nudgeSubjectAnchor(-5.0)
        assertEquals(DofState.MIN_ANCHOR, subjectPosition(s), 1e-9)

        // Pinned there, the value still moves — that is the drag becoming a scroll.
        val before = s.subject
        s.moveSubject(s.subject * 2.0)
        assertTrue(s.subject > before)
        assertEquals(DofState.MIN_ANCHOR, subjectPosition(s), 1e-9)
    }

    @Test
    fun `the focal length marker travels and then stops at the edge band`() {
        val s = state()
        assertEquals(0.5, s.focalAnchor, 1e-9)

        s.nudgeFocalAnchor(0.15)
        assertEquals(0.65, s.focalAnchor, 1e-9)

        // Pushed past the band it stops, leaving the scale to do the rest of the moving.
        s.nudgeFocalAnchor(1.0)
        assertEquals(DofState.MAX_ANCHOR, s.focalAnchor, 1e-9)
        s.nudgeFocalAnchor(-3.0)
        assertEquals(DofState.MIN_ANCHOR, s.focalAnchor, 1e-9)
    }

    @Test
    fun `each scale keeps its own marker position`() {
        val s = state()
        s.nudgeFocalAnchor(0.2)
        assertEquals(0.7, s.focalAnchor, 1e-9)
        // Moving the focal length marker must not shift the subject line.
        assertEquals(0.5, subjectPosition(s), 1e-9)

        s.nudgeSubjectAnchor(-0.2)
        assertEquals(0.3, subjectPosition(s), 1e-9)
        assertEquals(0.7, s.focalAnchor, 1e-9)
    }

    @Test
    fun `the depth of field keeps its quarter wherever the line is anchored`() {
        val s = state()
        for (anchor in listOf(0.5, 0.2, 0.8, 0.35)) {
            s.nudgeSubjectAnchor(anchor - subjectPosition(s))
            assertEquals(DofState.DOF_SHARE_OF_SCALE, dofShare(s), 0.001)
            assertTrue(s.subject in s.distanceWindow(s.compute()).let { it.lo..it.hi })
        }
    }

    /** A 61 MP full frame body, whose diffraction limit lands at about f/5.6. */
    private fun highResolution(): DofState = DofState(
        Settings(
            cameras = listOf(
                Camera(
                    name = "A7R V",
                    type = CameraType.DIGITAL,
                    frameWidthMm = 35.7,
                    frameHeightMm = 23.8,
                    frameWidthPx = 9504,
                    frameHeightPx = 6336,
                ),
            ),
            lenses = listOf(Lens("100-400", 100.0, 400.0, 4.0, 32.0)),
            targets = listOf(ViewingTarget(kind = TargetKind.PIXELS)),
            focalLength = 100.0,
            // f/5 rather than f/5.6. Judged at pixel level this body is diffraction
            // limited at f/5.598, so f/5.6 sits three hundredths of a per cent the wrong
            // side of it and has no depth of field at all to give these tests a quarter
            // of. A fixture that close to a cliff measures the cliff, not the rule.
            fStop = 5.0,
            subject = 1500.0,
            units = org.kutner.dofpro.model.UnitSystem.METRIC,
        )
    )

    @Test
    fun `a depth of field of a few millimetres still gets its quarter of the scale`() {
        val s = highResolution()
        val r = s.compute()
        // The sharp zone here really is only millimetres deep, and it still gets its
        // quarter — the view closes right down around it rather than the markers
        // bunching up against the subject line.
        assertTrue((r.far!! - r.near!!) < 10.0)
        assertEquals(DofState.DOF_SHARE_OF_SCALE, dofShare(s), 0.001)
    }

    @Test
    fun `past the diffraction limit a high resolution camera reports no limits`() {
        val s = highResolution()
        s.fStop = 11.0
        val r = s.compute()
        // Not a fault: blur from the aperture alone already exceeds the circle of
        // confusion, so no distance meets the criterion. The screen says so.
        assertNull(r.near)
        assertNull(r.far)
        assertTrue(r.blurAtSubject > 1.0)
        assertTrue(11.0 > s.diffractionLimit)
    }

    @Test
    fun `a larger allowable blur is the way back past the diffraction limit`() {
        val s = highResolution()
        s.fStop = 11.0
        assertNull(s.compute().near)

        // Accepting more blur is the documented remedy, and it is a property of the
        // viewing rather than of the camera: a bigger circle of confusion moves the
        // diffraction limit out past the aperture in use.
        s.targets[0] = s.targets[0].copy(allowableBlur = 5.0)
        assertTrue(s.diffractionLimit > 11.0)
        val r = s.compute()
        assertTrue(r.near != null && r.near < r.subject)
        assertTrue(r.far != null && r.far > r.subject)
    }

    @Test
    fun `dragging outward accelerates as the depth of field grows`() {
        // A step is a fixed fraction of the visible scale, and the scale widens as the
        // subject recedes, so equal drags cover more ground the further out you go.
        val s = state()
        fun stepSize(atFeet: Double): Double {
            s.moveSubject(atFeet * ft)
            val w = s.distanceWindow(s.compute())
            return ln(w.hi / w.lo)
        }
        assertTrue(stepSize(100.0) > stepSize(10.0))
        assertTrue(stepSize(10.0) > stepSize(2.0))
    }

    @Test
    fun `stopping down widens the view so the red lines keep their quarter`() {
        val s = state()
        val before = s.distanceWindow(s.compute())
        s.fStop = 11.0
        val after = s.distanceWindow(s.compute())
        // More depth of field means a wider view, not red lines drifting apart.
        assertTrue(ln(after.hi / after.lo) > ln(before.hi / before.lo))
        assertEquals(DofState.DOF_SHARE_OF_SCALE, dofShare(s), 0.001)
    }

    @Test
    fun `the window never inverts or collapses`() {
        val s = state()
        var d = DofState.MIN_DISTANCE
        while (d <= DofState.MAX_DISTANCE) {
            for (stop in listOf(1.4, 4.0, 11.0, 32.0)) {
                s.fStop = stop
                s.moveSubject(d)
                val w = s.distanceWindow(s.compute())
                assertTrue("window ${w.lo}..${w.hi} is inverted", w.hi > w.lo)
                assertTrue("subject outside window", s.subject in w.lo..w.hi)
            }
            d *= 1.7
        }
    }

    // ---- Lenses ----------------------------------------------------------------------

    private fun withLenses(): DofState = DofState(
        state().toSettings().copy(
            lenses = listOf(
                Lens("Any lens"),
                Lens("50mm prime", 50.0, 50.0, 1.8, 22.0),
                Lens("24-70 zoom", 24.0, 70.0, 2.8, 22.0),
            ),
            lensIndex = 0,
        )
    )

    @Test
    fun `a lens confines the focal length to what it covers`() {
        val s = withLenses()
        s.selectLens(2) // 24-70
        s.changeFocalLength(200.0)
        assertEquals(70.0, s.focalLength, 1e-9)
        s.changeFocalLength(10.0)
        assertEquals(24.0, s.focalLength, 1e-9)
        s.changeFocalLength(35.0)
        assertEquals(35.0, s.focalLength, 1e-9)
    }

    @Test
    fun `a prime pins the focal length to its one value`() {
        val s = withLenses()
        s.selectLens(1) // 50mm prime
        assertEquals(50.0, s.focalLength, 1e-9)
        assertFalse(s.lens.isZoom)
        // Even asked directly, it cannot be moved off.
        s.changeFocalLength(85.0)
        assertEquals(50.0, s.focalLength, 1e-9)
    }

    @Test
    fun `a lens offers only the apertures it has`() {
        val s = withLenses()
        s.selectLens(2) // f/2.8 - f/22
        val stops = s.apertureStops()
        assertTrue(stops.all { it in 2.8..22.0 })
        assertTrue(stops.contains(2.8))
        assertTrue(stops.contains(22.0))
        assertFalse(stops.contains(1.4))
        assertFalse(stops.contains(32.0))
    }

    @Test
    fun `selecting a lens winds an out-of-range aperture back into range`() {
        val s = withLenses()
        s.fStop = 32.0
        s.selectLens(2) // narrowest is f/22
        assertEquals(22.0, s.fStop, 1e-9)

        s.fStop = 1.4
        s.selectLens(1) // 50mm prime opens to f/1.8
        assertEquals(1.8, s.fStop, 1e-9)
    }

    @Test
    fun `a dragged limit stays within the lens's apertures`() {
        val s = withLenses()
        s.selectLens(2)
        s.moveSubject(10.0 * ft)
        // Ask for a depth of field far deeper than f/22 can give.
        s.dragNearLimit(s.compute().near!! * 0.05)
        assertTrue("f/${s.fStop} is outside the lens", s.fStop <= 22.0)
        assertTrue(s.apertureStops().contains(s.fStop))
    }

    @Test
    fun `lenses and the selection survive a round trip`() {
        val s = withLenses()
        s.selectLens(2)
        s.lenses[2] = s.lenses[2].copy(name = "renamed", maxFocal = 105.0)

        val restored = DofState(s.toSettings())
        assertEquals(3, restored.lenses.size)
        assertEquals(2, restored.lensIndex)
        assertEquals("renamed", restored.lens.name)
        assertEquals(105.0, restored.lens.maxFocal, 1e-9)
        assertEquals(24.0, restored.lens.minFocal, 1e-9)
        assertEquals(2.8, restored.lens.minFStop, 1e-9)
    }

    @Test
    fun `a lens describes itself the way it is written on the barrel`() {
        assertEquals("50mm  f/1.8-22", Lens("p", 50.0, 50.0, 1.8, 22.0).specification)
        assertEquals("24-70mm  f/2.8-22", Lens("z", 24.0, 70.0, 2.8, 22.0).specification)
    }

    @Test
    fun `settings survive a round trip`() {
        val s = state()
        s.fStop = 11.0
        s.changeFocalLength(85.0)
        s.moveSubject(7.0 * ft)
        val restored = DofState(s.toSettings())
        assertEquals(11.0, restored.fStop, 1e-9)
        assertEquals(85.0, restored.focalLength, 1e-9)
        assertEquals(7.0 * ft, restored.subject, 1e-9)
    }

    // ---- Dragging a limit walks the aperture, it does not hop about ---------------

    /** 35mm film and a 100-400: a big circle of confusion, so the peak is well inside. */
    private fun filmTele(): DofState = DofState(
        Settings(
            cameras = listOf(
                Camera(
                    name = "35mm film",
                    type = CameraType.FILM,
                    frameWidthMm = 36.0,
                    frameHeightMm = 24.0,
                ),
            ),
            lenses = listOf(Lens("100-400", 100.0, 400.0, 5.6, 32.0)),
            targets = listOf(PRINT_12_AT_18),
            apertureStep = ApertureStep.THIRD,
            focalLength = 100.0,
            fStop = 8.0,
            subject = 10_000.0,
        )
    )

    @Test
    fun `easing the near limit down moves one stop at a time`() {
        val s = filmTele()
        // The reported jump: from f/8 a small nudge went to f/22, and the next nudge
        // came back to f/9. f/22 reaches 8.04 m, which falls between f/8's 8.10 m and
        // f/9's 7.95 m — past the peak the stops double back over ones already passed.
        val visited = mutableListOf(s.fStop)
        var request = s.compute().near!!
        repeat(40) {
            request *= 0.995
            s.dragNearLimit(request)
            if (s.fStop != visited.last()) visited.add(s.fStop)
        }
        // Pulling the near limit closer can only ever mean stopping down.
        visited.zipWithNext { a, b ->
            assertTrue("aperture went $a -> $b while widening the depth of field", b > a)
        }
        assertTrue("the drag never left f/8", visited.size > 1)
        // And it walks the engraved series rather than skipping over it.
        val series = s.apertureStops()
        visited.zipWithNext { a, b ->
            val step = series.indexOf(b) - series.indexOf(a)
            assertEquals("skipped from $a to $b", 1, step)
        }
    }

    @Test
    fun `a drag never stops down past the aperture that gives the most depth`() {
        val s = filmTele()
        // Asked for far more depth than any aperture can give, it should settle at the
        // widest depth available and stay there — not carry on into the stops where
        // diffraction is closing the limits back in.
        s.dragNearLimit(1.0)
        val settled = s.fStop
        val widest = s.compute().near!!

        // Every stop the lens offers, including the ones the drag refuses to pick.
        for (stop in s.apertureStops()) {
            val trial = filmTele()
            trial.fStop = stop
            trial.compute().near?.let {
                assertTrue("f/$stop reaches $it, closer than f/$settled at $widest", it >= widest - 1e-9)
            }
        }
        // On 35mm film the product f*Bf peaks at f = c*750/sqrt(2) = f/16.6, so f/16 it is.
        assertEquals(16.0, settled, 1e-9)
    }

    @Test
    fun `the far limit walks the same way`() {
        val s = filmTele()
        val visited = mutableListOf(s.fStop)
        var request = s.compute().far!!
        repeat(40) {
            request *= 1.005
            s.dragFarLimit(request)
            if (s.fStop != visited.last()) visited.add(s.fStop)
        }
        visited.zipWithNext { a, b ->
            assertTrue("aperture went $a -> $b while pushing the far limit out", b > a)
        }
        assertTrue("the drag never left f/8", visited.size > 1)
    }

    // ---- Deleting several at once ---------------------------------------------------

    private fun withCameras(n: Int): DofState {
        val s = state()
        s.cameras.clear()
        repeat(n) { s.cameras.add(Camera(name = "cam$it")) }
        return s
    }

    @Test
    fun `deleting several takes out the ones that were chosen`() {
        // The trap this avoids: removing by ascending index shifts everything after it, so
        // the second removal takes out the wrong one.
        val s = withCameras(5)
        s.removeCameras(setOf(0, 2, 3))
        assertEquals(listOf("cam1", "cam4"), s.cameras.map { it.name })
    }

    @Test
    fun `the camera in use survives, or is replaced by one that exists`() {
        val s = withCameras(5)
        s.cameraIndex = 4
        s.removeCameras(setOf(0, 1))
        assertEquals("cam4", s.camera.name)
        assertEquals(2, s.cameraIndex)

        // And when the one in use is itself deleted, something takes its place rather than
        // the index dangling past the end of the list.
        s.removeCameras(setOf(2))
        assertTrue(s.cameraIndex in s.cameras.indices)
        assertEquals(2, s.cameras.size)
    }

    @Test
    fun `the last camera cannot be deleted`() {
        // No camera means no circle of confusion, and nothing to compute with.
        val s = withCameras(3)
        s.removeCameras(setOf(0, 1, 2))
        assertEquals(3, s.cameras.size)
        s.removeCameras(setOf(0, 1))
        assertEquals(1, s.cameras.size)
        s.removeCameras(setOf(0))
        assertEquals(1, s.cameras.size)
    }

    @Test
    fun `an index that is not there is ignored rather than throwing`() {
        val s = withCameras(2)
        s.removeCameras(setOf(7, -1, 0))
        assertEquals(listOf("cam1"), s.cameras.map { it.name })
    }

    @Test
    fun `deleting lenses re-applies the surviving lens's limits`() {
        val s = state()
        s.lenses.clear()
        s.lenses.add(Lens("wide", 16.0, 35.0, 4.0, 22.0))
        s.lenses.add(Lens("tele", 200.0, 200.0, 2.8, 22.0))
        s.selectLens(1)
        assertEquals(200.0, s.focalLength, 1e-9)

        s.removeLenses(setOf(1))
        // The prime is gone; the zoom that remains must own the focal length now.
        assertEquals("wide", s.lens.name)
        assertTrue("focal ${s.focalLength} is outside 16-35", s.focalLength in 16.0..35.0)
    }

    // ---- Focus stacking -------------------------------------------------------------

    private fun stackOf(s: DofState) = s.frames(s.compute())

    @Test
    fun `a stack covers everything from the closest point to infinity`() {
        val s = state()
        s.changeFocalLength(24.0, settle = false)
        s.fStop = 8.0
        s.moveSubject(500.0)
        val frames = stackOf(s)
        assertTrue(frames.complete)
        assertTrue("expected a handful of frames, got ${frames.count}", frames.count in 2..6)

        // Walk the stack: each frame's far limit must reach at least the next frame's
        // near limit, or there is a gap in the middle that no amount of blending fixes.
        val r = s.compute()
        val l = r.effectiveFocal
        val h = r.hyperfocal
        fun near(a: Double) = a * (h - l) / (h + a - 2 * l)
        fun far(a: Double) = if (a >= h) Double.POSITIVE_INFINITY else a * (h - l) / (h - a)

        assertTrue("the first frame must reach the closest point",
            near(frames.focusPoints.first()) <= r.subject + 1e-6)
        frames.focusPoints.zipWithNext { a, b ->
            assertTrue("gap between $a and $b", far(a) >= near(b) - 1e-6)
        }
        assertTrue("the last frame must hold infinity", far(frames.focusPoints.last()).isInfinite())
    }

    @Test
    fun `the last frame is the hyperfocal distance`() {
        // The closest focus that still holds infinity, so it reaches back furthest while
        // doing so — any nearer and infinity goes soft, any further wastes coverage.
        val s = state()
        s.fStop = 8.0
        s.moveSubject(1_000.0)
        val r = s.compute()
        assertEquals(r.hyperfocal, stackOf(s).focusPoints.last(), r.hyperfocal * 1e-9)
    }

    @Test
    fun `more overlap costs more frames, and none costs fewest`() {
        val s = state()
        s.fStop = 8.0
        s.moveSubject(1_000.0)
        s.stackOverlap = 0.0
        val none = stackOf(s).count
        s.stackOverlap = 0.2
        val some = stackOf(s).count
        s.stackOverlap = 0.5
        val lots = stackOf(s).count
        assertTrue("$none / $some / $lots", none <= some && some <= lots)
        assertTrue("overlap should make a difference", lots > none)
    }

    @Test
    fun `the overlap really is the overlap`() {
        // Each frame doubles back over the one before by the configured fraction of a
        // frame's depth, measured in reciprocal distance — the space a stack is uniform in.
        val s = state()
        s.fStop = 8.0
        s.moveSubject(1_000.0)
        s.stackOverlap = 0.25
        val r = s.compute()
        val l = r.effectiveFocal
        val h = r.hyperfocal
        fun near(a: Double) = a * (h - l) / (h + a - 2 * l)
        fun far(a: Double) = if (a >= h) Double.POSITIVE_INFINITY else a * (h - l) / (h - a)

        val pts = stackOf(s).focusPoints
        // The last frame runs to infinity and overshoots, so it is not held to the figure.
        pts.dropLast(1).zipWithNext { a, b ->
            val width = 1.0 / near(a) - 1.0 / far(a)
            val shared = 1.0 / near(b) - 1.0 / far(a)
            assertEquals("frames at $a and $b", 0.25, shared / width, 1e-6)
        }
    }

    @Test
    fun `one frame is enough when the closest point is already past half the hyperfocal`() {
        val s = state()
        s.fStop = 8.0
        val h = s.compute().hyperfocal
        s.moveSubject(h * 0.6)
        val frames = stackOf(s)
        assertEquals(1, frames.count)
        assertEquals(h, frames.focusPoints.single(), h * 1e-9)
    }

    @Test
    fun `there is nothing to stack past the diffraction limit`() {
        val s = state()
        s.fStop = 32.0
        s.moveSubject(1_000.0)
        val frames = stackOf(s)
        assertTrue(frames.focusPoints.isEmpty())
        assertFalse(frames.complete)
    }

    @Test
    fun `a stack is bounded even when the range needs a great many frames`() {
        val s = state()
        s.changeFocalLength(100.0, settle = false)
        s.fStop = 2.0
        s.moveSubject(s.minSubject)
        val frames = stackOf(s)
        assertTrue("runaway: ${frames.count}", frames.count <= 999)
    }

    // ---- Focusing at the hyperfocal distance ---------------------------------------

    @Test
    fun `focusing at the hyperfocal distance holds everything beyond half of it`() {
        // What tapping the hyperfocal read-out is for. The far limit lands exactly on
        // infinity and the near limit exactly on half the distance — the definition of
        // the hyperfocal distance, and the check that the number is usable as a focus
        // setting rather than only a read-out.
        val s = state()
        for (stop in listOf(2.8, 4.0, 8.0, 16.0)) {
            s.fStop = stop
            val h = s.compute().hyperfocal
            assertTrue("f/$stop has no reachable hyperfocal distance", h.isFinite())
            s.moveSubject(h)
            val r = s.compute()
            assertEquals(h, r.subject, 1e-6)
            assertTrue("f/$stop should hold infinity", r.far!!.isInfinite())
            assertEquals("f/$stop near limit", h / 2.0, r.near!!, h * 1e-9)
        }
    }

    @Test
    fun `an unreachable hyperfocal distance is left alone`() {
        // Past the diffraction limit it is infinite, and there is nothing to focus at.
        val s = state()
        s.fStop = 32.0
        val r = s.compute()
        assertTrue(r.hyperfocal.isInfinite())
        assertNull(r.near)
    }

    // ---- The lens scale is the lens's own range ------------------------------------

    @Test
    fun `a zoom draws its own range and nothing else`() {
        assertEquals(100.0..400.0, Lens("100-400", 100.0, 400.0, 5.6, 32.0).scaleRange())
        assertEquals(16.0..35.0, Lens("16-35", 16.0, 35.0, 4.0, 22.0).scaleRange())
    }

    @Test
    fun `a prime has no range to draw`() {
        // One focal length is not a stretch; the scale stays a read-out around it.
        assertNull(Lens("50", 50.0, 50.0, 1.8, 22.0).scaleRange())
    }

    @Test
    fun `a nominal any-lens is too wide to draw end to end`() {
        // 1 mm to 3 m is not a barrel to engrave — drawn whole it would crush every real
        // focal length into a few pixels, so that one keeps the moving view.
        assertNull(Lens.ANY.scaleRange())
        // But every real lens, up to a superzoom far wider than any that exists, is drawn.
        assertNotNull(Lens("18-400 superzoom", 18.0, 400.0, 3.5, 32.0).scaleRange())
        assertEquals(Lens.WIDEST_DRAWN, 40.0, 1e-9)
    }

    @Test
    fun `every stock zoom shows both of its ends`() {
        // The point of the change: the short end used to fall off the bottom whenever the
        // marker went near the long end.
        for (lens in Lens.defaults().filter { it.isZoom }) {
            val range = lens.scaleRange() ?: continue
            assertEquals(lens.minFocal, range.start, 1e-9)
            assertEquals(lens.maxFocal, range.endInclusive, 1e-9)
        }
    }

    // ---- The markers move, not just the scale --------------------------------------

    private fun shareOf(s: DofState): Double {
        val r = s.compute()
        val w = s.distanceWindow(r)
        return ln(r.far!! / r.near!!) / ln(w.hi / w.lo)
    }

    // ---- Ignoring diffraction -------------------------------------------------------

    @Test
    fun `ignoring diffraction reproduces the classical formula`() {
        // The comparison the setting exists for: with diffraction out of the way the
        // budget is the whole circle of confusion, which is what every calculator that
        // omits it assumes.
        val s = state()
        s.changeFocalLength(16.0, settle = false)
        s.fStop = 8.0
        s.moveSubject(2000.0)
        s.ignoreDiffraction = true
        val r = s.compute()
        val c = r.coc
        assertEquals(Dof.hyperfocal(16.0, 8.0, c), r.hyperfocal, 1e-9)
        assertEquals(Dof.nearLimit(16.0, 8.0, 2000.0, c), r.near!!, 1e-9)
        assertEquals(Dof.farLimit(16.0, 8.0, 2000.0, c), r.far!!, 1e-9)
        // And the blur at the subject is nothing at all: focus blur is zero where you
        // focused, and there is no diffraction left to take its place.
        assertEquals(0.0, r.blurAtSubject, 1e-12)
    }

    @Test
    fun `ignoring diffraction removes every limit on stopping down`() {
        val s = state()
        s.ignoreDiffraction = true
        // No aperture ever spends the circle of confusion on diffraction alone...
        assertTrue(s.diffractionLimit.isInfinite())
        // ...so stopping down never stops buying depth, and the widest view is the
        // narrowest aperture the lens has.
        assertEquals(s.apertureStops().last(), s.bestDepthFStop, 1e-12)
    }

    @Test
    fun `the setting survives a round trip through the saved settings`() {
        val s = state()
        s.ignoreDiffraction = true
        assertTrue(DofState(s.toSettings()).ignoreDiffraction)
    }

    // ---- Where the depth of field peaks ---------------------------------------------

    private fun peakStop(widthPx: Int, widestStop: Double): Double {
        val camera = Camera(
            type = CameraType.DIGITAL,
            frameWidthMm = 36.0, frameHeightMm = 24.0,
            frameWidthPx = widthPx, frameHeightPx = widthPx * 2 / 3,
        )
        val lens = Lens(
            name = "test", minFocal = 50.0, maxFocal = 50.0,
            minFStop = widestStop, maxFStop = 32.0,
        )
        val pixels = ViewingTarget.defaults().first { it.kind == TargetKind.PIXELS }
        return DofState(
            Settings(cameras = listOf(camera), lenses = listOf(lens), targets = listOf(pixels))
        ).bestDepthFStop
    }

    @Test
    fun `a coarser sensor goes on buying depth further down the aperture scale`() {
        // The peak sits at f = c*750/sqrt(2), and judged at pixel level c is twice the
        // pixel pitch, so a sensor with bigger pixels peaks at a higher f number. Asked
        // with a lens wide enough to actually reach the peak.
        val mp33 = peakStop(7008, 1.4)
        val mp45 = peakStop(8256, 1.4)
        val mp61 = peakStop(9504, 1.4)
        assertTrue("33MP peaked at f/$mp33, 45MP at f/$mp45", mp33 > mp45)
        assertTrue("45MP peaked at f/$mp45, 61MP at f/$mp61", mp45 > mp61)
    }

    @Test
    fun `a lens that cannot open to the peak reports the widest stop it has`() {
        // Judged at pixel level a full frame sensor wants somewhere around f/4, which a
        // slow lens cannot reach. Every stop it does have is then past the peak, so the
        // most depth of field on offer is wide open — and every sensor answers the same,
        // which reads as the figure ignoring the camera when it is really the lens
        // running out of aperture.
        for (px in listOf(7008, 8256, 9504)) {
            assertEquals("a f/5.6 lens on a ${px}px sensor", 5.6, peakStop(px, 5.6), 1e-9)
        }
        // Given a lens that can open up, they separate again. Not the 33MP body: its own
        // peak really is f/5.6, which is the coincidence that makes this worth pinning.
        assertTrue(peakStop(9504, 1.4) < 5.6)
        assertEquals(5.6, peakStop(7008, 1.4), 1e-9)
    }

    // ---- Keeping the lines apart ---------------------------------------------------

    @Test
    fun `no setting puts a limit line within a fingertip of the subject line`() {
        // The quarter rule sizes the sharp zone as a whole and says nothing about how it
        // is divided, and it divides very unevenly at any distance past the close-up
        // range. What keeps the near line reachable is that the optics tie the two
        // limits together: the more lopsided the split, the wider the zone, and the two
        // effects very nearly cancel. This pins the margin that leaves, because it is
        // the whole reason a limit can be picked up on its own.
        var worst = Double.MAX_VALUE
        var where = ""
        for (camera in Camera.defaults()) {
            for (lens in Lens.defaults()) {
                val s = DofState(Settings(cameras = listOf(camera), lenses = listOf(lens)))
                for (focal in listOf(lens.minFocal, lens.maxFocal)) {
                    s.changeFocalLength(focal, settle = false)
                    for (stop in s.apertureStops()) {
                        s.fStop = stop
                        var d = s.minSubject
                        while (d < DofState.MAX_DISTANCE) {
                            s.moveSubject(d)
                            val r = s.compute()
                            val near = r.near
                            if (near != null && near.isFinite() && near > 0.0) {
                                val w = s.distanceWindow(r)
                                val gap = ln(r.subject / near) / ln(w.hi / w.lo)
                                if (gap < worst) {
                                    worst = gap
                                    where = "${camera.name} / ${lens.name} $focal mm " +
                                        "f/$stop at $d mm"
                                }
                            }
                            d *= 1.7
                        }
                    }
                }
            }
        }
        assertTrue(
            "the near line came within $worst of the subject at $where",
            worst >= DofState.MIN_MARKER_GAP,
        )
    }

    @Test
    fun `an even depth of field is left alone`() {
        // Close up the sharp zone divides nearly evenly, both gaps clear the floor at the
        // quarter share already, and the view should not tighten past it.
        val s = state()
        s.changeFocalLength(100.0, settle = false)
        s.fStop = 4.0
        s.moveSubject(500.0)
        assertEquals(0.25, shareOf(s), 1e-9)
    }

    private fun screenFractions(s: DofState): Pair<Double, Double> {
        val r = s.compute()
        val w = s.distanceWindow(r)
        val span = ln(w.hi / w.lo)
        fun f(d: Double) = 1.0 - (ln(d) - ln(w.lo)) / span
        return f(r.near!!) to f(r.far!!)
    }

    @Test
    fun `at rest the depth of field gets exactly its quarter`() {
        val s = state()
        for (stop in listOf(2.8, 4.0, 8.0, 16.0)) {
            s.fStop = stop
            assertEquals("f/$stop", 0.25, shareOf(s), 1e-9)
        }
    }

    @Test
    fun `with the span held, stopping down spreads the markers instead of rescaling`() {
        // The complaint this answers: dragging a limit moved the numbers but not the
        // lines, because the scale grew in step with the depth of field and held them
        // nailed to the screen.
        val s = state()
        s.fStop = 4.0
        val (near0, far0) = screenFractions(s)

        val held = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
        s.holdDistanceSpan(held)

        s.fStop = 11.0
        val (near1, far1) = screenFractions(s)
        assertTrue("the near line did not move: $near0 -> $near1", near1 > near0 + 0.02)
        assertTrue("the far line did not move: $far0 -> $far1", far1 < far0 - 0.02)
        assertTrue("the depth of field should now take more than its quarter", shareOf(s) > 0.25)
    }

    @Test
    fun `the markers cannot be pushed off the scale, nor squeezed below a quarter`() {
        val s = state()
        s.fStop = 4.0
        s.holdDistanceSpan(ln(s.distanceWindow(s.compute()).let { it.hi / it.lo }))
        // Far past where the held span would put them, in both directions.
        for (stop in listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)) {
            s.fStop = stop
            val share = shareOf(s)
            assertTrue("f/$stop gave the depth of field $share of the height", share >= 0.25 - 1e-9)
            assertTrue("f/$stop gave the depth of field $share of the height", share <= 0.8 + 1e-9)
            val (near, far) = screenFractions(s)
            assertTrue("f/$stop put the near line at $near", near in 0.0..1.0)
            assertTrue("f/$stop put the far line at $far", far in 0.0..1.0)
        }
    }

    @Test
    fun `pinching zooms past both ends of the band the automatic fit uses`() {
        // The band from a quarter to four fifths is what the fit stays inside when it
        // sizes itself, so the limit markers always have somewhere to move. A deliberate
        // pinch outranks it. Held to the band the gesture was very nearly useless: the
        // resting view already sits at the band's widest, so spreading two fingers did
        // nothing whatever, and closing them ran out after 3.2 times.
        val s = state()
        val rest = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })

        // Fingers spreading apart zoom in: a narrower span, a bigger share.
        repeat(10) { s.zoomDistance(1.5, rest) }
        assertTrue("pinching in stopped at the band", shareOf(s) > 0.8 + 1e-6)

        // And together, to a view wider than the fit would ever have chosen.
        repeat(30) { s.zoomDistance(1 / 1.5, rest) }
        assertTrue("pinching out stopped at the band", shareOf(s) < 0.25 - 1e-6)
    }

    @Test
    fun `holding the span is a one-off, so a second grab does not re-freeze it`() {
        val s = state()
        val rest = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
        s.zoomDistance(2.0, rest)
        val zoomed = s.distanceSpan
        // Picking up a marker afterwards must not throw the user's zoom away.
        s.holdDistanceSpan(rest)
        assertEquals(zoomed, s.distanceSpan, 1e-12)
    }

    @Test
    fun `the window can be sized for any camera, lens and distance with the span held`() {
        // The crash this answers: switching from a full frame body to Micro 4/3 made the
        // depth of field shallow enough that the quarter share needed a window narrower
        // than the pinch-in floor, and the band came out inverted.
        val s = state()
        for (cam in Camera.defaults()) {
            s.cameras[0] = cam
            for (focal in listOf(10.0, 24.0, 50.0, 100.0, 400.0, 1200.0)) {
                s.changeFocalLength(focal, settle = false)
                for (stop in s.apertureStops()) {
                    s.fStop = stop
                    var d = s.minSubject
                    while (d < DofState.MAX_DISTANCE) {
                        s.moveSubject(d)
                        val r = s.compute()
                        // Both with the span left to fit itself, and with it held at
                        // whatever the last view happened to be.
                        val w = s.distanceWindow(r)
                        assertTrue("${cam.name} $focal mm f/$stop at $d mm", w.hi > w.lo)
                        s.holdDistanceSpan(ln(w.hi / w.lo))
                        val held = s.distanceWindow(s.compute())
                        assertTrue("${cam.name} $focal mm f/$stop at $d mm held", held.hi > held.lo)
                        d *= 4.0
                    }
                }
            }
        }
    }

    @Test
    fun `a very shallow depth of field still gets its quarter, floor or no floor`() {
        // Micro 4/3 behind a long lens: the whole view is a fraction of a per cent wide,
        // far narrower than the pinch-in floor. The quarter share wins.
        //
        // Built here rather than taken from the shipped list, which no longer carries a
        // Four Thirds body. What is under test is a small sensor and a long lens, not the
        // catalogue, and the regression this guards should outlive any edit to that list.
        val s = state()
        s.cameras[0] = Camera(
            name = "Micro 4/3 16MP",
            type = CameraType.DIGITAL,
            frameWidthMm = 17.3, frameHeightMm = 13.0,
            frameWidthPx = 4608, frameHeightPx = 3456,
        )
        s.changeFocalLength(400.0, settle = false)
        s.fStop = 2.8
        s.moveSubject(2_000.0)
        val r = s.compute()
        val w = s.distanceWindow(r)
        assertTrue("the view should be narrower than the floor here",
            ln(w.hi / w.lo) < DofState.MIN_WINDOW_SPAN)
        assertEquals(0.25, ln(r.far!! / r.near!!) / ln(w.hi / w.lo), 1e-9)

        // Holding it, without pinching, cannot break out of that.
        s.holdDistanceSpan(ln(w.hi / w.lo))
        val held = s.distanceWindow(s.compute())
        assertEquals(0.25, ln(r.far / r.near) / ln(held.hi / held.lo), 1e-9)
    }

    // ---- Pinch -------------------------------------------------------------------

    @Test
    fun `pinching escapes the band the automatic fit holds the window in`() {
        // The band exists so the depth of field keeps roughly its quarter of the height
        // and the limit markers have somewhere to move. Applied to a deliberate gesture
        // it made the pinch useless: at rest the window already sits at the widest the
        // band allows, so spreading two fingers did nothing at all.
        val s = state()
        s.changeFocalLength(50.0, settle = false)
        s.fStop = 4.0
        s.moveSubject(4.0 * 304.8)
        val rest = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })

        // Fingers together: a wider view than the automatic fit would ever have chosen.
        repeat(6) { s.zoomDistance(0.5, rest) }
        val wide = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
        assertTrue("pinching out gave $wide against a resting $rest", wide > rest * 1.5)

        // And back the other way, well past the 3.2 times the band used to allow.
        repeat(12) { s.zoomDistance(2.0, wide) }
        val tight = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
        assertTrue("pinching in gave $tight against a resting $rest", tight < rest / 4.0)
    }

    @Test
    fun `the scale's absolute limits still hold, however hard it is pinched`() {
        val s = state()
        s.moveSubject(4.0 * 304.8)
        val rest = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
        repeat(60) { s.zoomDistance(2.0, rest) }
        val w = s.distanceWindow(s.compute())
        assertTrue("inverted after pinching in", w.hi > w.lo)
        repeat(120) { s.zoomDistance(0.5, rest) }
        val out = s.distanceWindow(s.compute())
        assertTrue("inverted after pinching out", out.hi > out.lo)
        assertTrue(ln(out.hi / out.lo) <= DofState.MAX_WINDOW_SPAN + 1e-9)
    }

    @Test
    fun `letting go of the zoom hands the window back to the automatic fit`() {
        val s = state()
        s.moveSubject(4.0 * 304.8)
        val rest = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
        repeat(4) { s.zoomDistance(2.0, rest) }
        assertTrue(s.zoomed)
        s.resetZoom()
        assertTrue(!s.zoomed)
        assertEquals(rest, ln(s.distanceWindow(s.compute()).let { it.hi / it.lo }), 1e-9)
    }

    // ---- Focal length ------------------------------------------------------------

    @Test
    fun `the focal length lands on whole millimetres`() {
        // No lens is marked in tenths of a millimetre and no photographer thinks in them,
        // so a scale reading 47.3 mm offers a precision that does not exist.
        val s = state()
        s.lenses[0] = Lens(name = "Any lens", minFocal = 1.0, maxFocal = 3000.0)
        s.selectLens(0)
        for (v in listOf(8.4, 8.6, 24.49, 47.3, 199.5, 1234.7)) {
            s.changeFocalLength(v, settle = false)
            assertEquals(
                "dragging to $v gave ${s.focalLength}",
                s.focalLength, Math.rint(s.focalLength), 0.0,
            )
            s.settleFocalLength()
            assertEquals(
                "settling from $v gave ${s.focalLength}",
                s.focalLength, Math.rint(s.focalLength), 0.0,
            )
        }
    }

    @Test
    fun `a short focal length still moves a whole millimetre at a time`() {
        // Below 10 mm the readable-number rule used to step in tenths.
        val s = state()
        s.lenses[0] = Lens(name = "Any lens", minFocal = 1.0, maxFocal = 3000.0)
        s.selectLens(0)
        s.changeFocalLength(6.0, settle = false)
        val before = s.focalLength
        s.changeFocalLength(6.4, settle = false)
        assertEquals(before, s.focalLength, 0.0)
        s.changeFocalLength(6.6, settle = false)
        assertEquals(7.0, s.focalLength, 0.0)
    }

    // ---- The blur graduations on the distance scale --------------------------------

    @Test
    fun `the reserved blur graduations are exactly the depth of field limits`() {
        // Why the scale always carries a graduation at the allowable blur, and why it
        // lands level with the red lines: that much blur *is* the sharpness criterion, so
        // the distances where it is reached are the limits themselves.
        val s = state()
        for (stop in listOf(2.8, 5.6, 11.0, 16.0)) {
            s.fStop = stop
            val r = s.compute()
            val budget = Dof.focusBlurBudget(
                r.effectiveF, r.coc, 1.0, s.camera.wavelengthNm,
            )!!
            assertEquals(
                r.near!!,
                Dof.nearLimit(r.effectiveFocal, r.effectiveF, r.subject, budget),
                1e-9,
            )
            assertEquals(
                r.far!!,
                Dof.farLimit(r.effectiveFocal, r.effectiveF, r.subject, budget),
                1e-9,
            )
        }
    }

    @Test
    fun `there is always room for the blur 1 graduations`() {
        // The scale is fitted so the depth of field fills a quarter of its height, so the
        // two graduations at 1 are a quarter of the scale apart however shallow it gets.
        // Nothing can crowd them out.
        val s = highResolution()
        val r = s.compute()
        val w = s.distanceWindow(r)
        val span = ln(w.hi / w.lo)
        assertEquals(0.25, ln(r.far!! / r.near!!) / span, 1e-6)
    }

    // ---- What the two notices say, and exactly when ------------------------------

    @Test
    fun `nothing is sharp precisely when the blur read-out reaches the allowable blur`() {
        // The "nothing sharp" notice is shown when there are no limits to draw. That has
        // to line up with the blur read-out crossing the allowance, or the screen
        // contradicts itself: at that point diffraction alone has spent the whole budget.
        // The allowance is 2 here, not 1 — the blur scale counts resolvable details, and
        // how many of them are acceptable is exactly what allowableBlur says.
        val s = filmTele()
        assertEquals(2.0, s.target.allowableBlur, 1e-9)
        for (stop in s.apertureStops()) {
            s.fStop = stop
            val r = s.compute()
            val blur = r.blurAtSubject
            assertEquals(2.0, r.sharpBlur, 1e-9)
            if (blur >= r.sharpBlur) {
                assertNull("f/$stop reads blur $blur yet still draws a near limit", r.near)
                assertNull("f/$stop reads blur $blur yet still draws a far limit", r.far)
            } else {
                assertTrue("f/$stop reads blur $blur but has no near limit", r.near != null)
            }
        }
        // And it does cross, inside this lens's range, or the test proves nothing.
        s.fStop = 32.0
        assertTrue(s.compute().blurAtSubject > 2.0)
        s.fStop = 22.0
        assertTrue(s.compute().blurAtSubject < 2.0)
    }

    @Test
    fun `the allowable blur is what the depth of field limits read`() {
        // The whole point of the change: the setting is visible on the scale rather than
        // being divided out of it. The limits do not move — only the number on them.
        val s = filmTele()
        s.fStop = 8.0
        val doubled = s.compute()
        assertEquals(2.0, doubled.sharpBlur, 1e-9)

        s.targets[0] = s.targets[0].copy(allowableBlur = 1.0)
        val single = s.compute()
        assertEquals(1.0, single.sharpBlur, 1e-9)

        // Halving the allowance halves the circle of confusion, so the depth of field
        // really does narrow.
        assertTrue(single.near!! > doubled.near!!)
        assertTrue(single.far!! < doubled.far!!)
        // But the blur at the subject does not budge, and that is the whole point: it is
        // a measurement in units of one resolvable detail, and how many of those anyone is
        // willing to accept has nothing to do with how many there are. The allowance moves
        // the threshold, not the reading.
        assertEquals(doubled.blurAtSubject, single.blurAtSubject, 1e-12)
        assertEquals(doubled.blurUnit, single.blurUnit, 1e-12)
        // What changed is where that reading counts as unsharp.
        assertEquals(2.0, doubled.sharpBlur, 1e-9)
        assertEquals(1.0, single.sharpBlur, 1e-9)
    }

    @Test
    fun `a custom circle of confusion has no factor to divide out`() {
        val s = filmTele()
        s.targets[0] = ViewingTarget(kind = TargetKind.CUSTOM, customCoc = 0.03)
        val r = s.compute()
        // Custom is given as a literal acceptable blur, so the limits read 1.
        assertEquals(1.0, r.sharpBlur, 1e-9)
        assertEquals(0.03, r.coc, 1e-12)
        assertEquals(0.03, r.blurUnit, 1e-12)
    }

    @Test
    fun `the limit quoted by the notice is the stop where the blur reaches the allowance`() {
        val s = filmTele()
        val limit = s.diffractionLimit
        // Just inside it there is still depth of field; just outside there is none.
        s.fStop = limit * 0.99
        assertTrue(s.compute().near != null)
        s.fStop = limit * 1.01
        assertNull(s.compute().near)
        s.fStop = limit
        val r = s.compute()
        assertEquals(r.sharpBlur, r.blurAtSubject, 1e-9)
    }

    @Test
    fun `the notice names a stop the lens actually has`() {
        val s = filmTele()
        val quoted = s.lastSharpFStop!!
        // Not the camera's exact limit — f/23.6 is not a setting on any lens.
        assertEquals(22.0, quoted, 1e-9)
        assertTrue("f/$quoted is not selectable", s.apertureStops().any { abs(it - quoted) < 1e-9 })
        assertTrue(quoted < s.diffractionLimit)

        // It is the last one that works: it has depth of field, and the next one up does not.
        s.fStop = quoted
        assertTrue(s.compute().near != null)
        val next = s.apertureStops().first { it > quoted }
        s.fStop = next
        assertNull("f/$next still has a depth of field", s.compute().near)
    }

    @Test
    fun `the quoted stop follows the aperture subdivision`() {
        // It is the last *selectable* stop, so it depends on what the ring can be set to.
        val third = filmTele().apply { apertureStep = ApertureStep.THIRD }
        val half = filmTele().apply { apertureStep = ApertureStep.HALF }
        assertEquals(22.0, third.lastSharpFStop!!, 1e-9)
        assertTrue("half stops should quote no finer than third stops", half.lastSharpFStop!! <= 22.0)
        for (s in listOf(third, half)) {
            assertTrue(s.apertureStops().any { abs(it - s.lastSharpFStop!!) < 1e-9 })
        }
    }

    @Test
    fun `a lens with no sharp stop at all has nothing to quote`() {
        // A 61 MP body judged at pixel level asks for a circle of confusion so small that
        // its diffraction limit falls below f/5.6, so a lens that opens no wider has no
        // aperture with any depth of field at all.
        //
        // Judged at pixel level *and* nothing else: the same body seen as a 12 inch print
        // is limited around f/23, which is the whole reason viewing is its own choice.
        val s = DofState(
            Settings(
                cameras = listOf(
                    Camera(
                        name = "61MP",
                        type = CameraType.DIGITAL,
                        frameWidthMm = 35.7,
                        frameHeightMm = 23.8,
                        frameWidthPx = 9504,
                        frameHeightPx = 6336,
                    ),
                ),
                targets = listOf(ViewingTarget(kind = TargetKind.PIXELS)),
                lenses = listOf(Lens("100-400", 100.0, 400.0, 8.0, 32.0)),
                apertureStep = ApertureStep.THIRD,
                focalLength = 100.0,
                fStop = 8.0,
                subject = 10_000.0,
            )
        )
        assertTrue("this camera should be limited below f/8", s.diffractionLimit < 8.0)
        assertNull(s.compute().near)
        assertNull("there is no stop to send the photographer to", s.lastSharpFStop)
        assertEquals(Notice.NOTHING_SHARP, s.noticeFor(s.compute()))
    }

    @Test
    fun `the most-depth notice appears at the peak and not before`() {
        val s = filmTele()
        for (stop in s.apertureStops()) {
            s.fStop = stop
            assertEquals(
                "f/$stop misreported against the peak f/${s.bestDepthFStop}",
                stop >= s.bestDepthFStop,
                s.atBestDepth,
            )
        }
        s.fStop = 8.0
        assertFalse(s.atBestDepth)
        s.fStop = 16.0
        assertTrue(s.atBestDepth)
    }

    @Test
    fun `losing the depth of field outranks having the most of it`() {
        // The conditions do overlap — everything past the diffraction limit is also past
        // the peak — so exactly one notice has to be chosen, and it is the worse news.
        val s = filmTele()
        var sawEach = 0
        for (stop in s.apertureStops()) {
            s.fStop = stop
            val r = s.compute()
            val shown = s.noticeFor(r)
            if (r.near == null) {
                assertTrue("f/$stop is past the peak as well", s.atBestDepth)
                assertEquals("f/$stop has no depth of field to be the most of", Notice.NOTHING_SHARP, shown)
            }
            if (shown == Notice.BEST_DEPTH) {
                assertTrue(r.near != null)
                sawEach++
            }
        }
        assertTrue("the peak notice never showed at all", sawEach > 0)
    }

    @Test
    fun `no notice while stopping down is still buying depth`() {
        val s = filmTele()
        s.fStop = 8.0
        assertEquals(Notice.NONE, s.noticeFor(s.compute()))
        s.fStop = 16.0
        assertEquals(Notice.BEST_DEPTH, s.noticeFor(s.compute()))
        s.fStop = 32.0
        assertEquals(Notice.NOTHING_SHARP, s.noticeFor(s.compute()))
    }

    @Test
    fun `a looser sharpness criterion moves both walls down the scale`() {
        // The remedy both notices offer: the peak sits at c*750/sqrt(2) and the limit at
        // c*750, so a bigger circle of confusion buys back both.
        val s = filmTele()
        val strictPeak = s.bestDepthFStop
        val strictLimit = s.diffractionLimit

        s.targets[0] = s.targets[0].copy(allowableBlur = s.targets[0].allowableBlur * 2.0)
        assertTrue("peak did not move: $strictPeak", s.bestDepthFStop > strictPeak)
        assertTrue("limit did not move", s.diffractionLimit > strictLimit)
    }

    @Test
    fun `the stops past the peak are still there to be set by hand`() {
        // Only the drag refuses them; f/22 remains a real setting with a real answer,
        // because a photographer may want it for reasons the depth of field cannot see.
        val s = filmTele()
        assertTrue(s.apertureStops().contains(22.0))
        s.fStop = 22.0
        assertEquals(22.0, s.fStop, 1e-9)
        assertTrue(s.compute().near!! > 0.0)
    }
}
