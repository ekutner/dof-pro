package org.kutner.dofpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kutner.dofpro.calc.Dof
import org.kutner.dofpro.model.Camera
import org.kutner.dofpro.model.CameraType
import org.kutner.dofpro.model.TargetKind
import org.kutner.dofpro.model.ViewingTarget
import org.kutner.dofpro.model.circleOfConfusion

/**
 * These reproduce the worked examples in the DoF 4.0 reference manual and the two
 * screenshots on the product page. If the engine drifts, these are what catch it.
 */
class DofTest {

    private val ft = 304.8
    private val green = 550.0

    /**
     * The two viewing conditions the reference screenshots were judged under, now that
     * viewing is a thing of its own rather than a mode of the camera. The numbers below
     * are unchanged from before the split, which is the point of checking them here: the
     * separation was meant to be a rearrangement, not a change of physics.
     */
    private val pixelLevel = ViewingTarget(kind = TargetKind.PIXELS)
    private val print12at18 = ViewingTarget(
        kind = TargetKind.PRINT,
        widthMm = 12.0 * 25.4,
        viewingDistanceMm = 18.0 * 25.4,
        visualResolution = 1.0,
        allowableBlur = 2.0,
    )

    private fun Camera.cocOn(target: ViewingTarget) = circleOfConfusion(this, target)

    // ---- Circle of confusion -----------------------------------------------------

    @Test
    fun `sharp image CoC for a Nikon D810 is about 0-01mm`() {
        val cam = Camera(
            type = CameraType.DIGITAL,
            frameWidthMm = 36.0, frameHeightMm = 24.0,
            frameWidthPx = 7360, frameHeightPx = 4912,
        )
        assertEquals(0.0098, cam.cocOn(pixelLevel), 0.0005)
    }

    @Test
    fun `sharp print CoC for 35mm is the conventional 0-03mm`() {
        val cam = Camera(
            frameWidthMm = 36.0,
        )
        assertEquals(0.0314, cam.cocOn(print12at18), 0.0005)
    }

    @Test
    fun `micro four thirds sharp image CoC matches the OM-D screenshot`() {
        val cam = Camera(
            type = CameraType.DIGITAL,
            frameWidthMm = 17.3, frameHeightMm = 13.0,
            frameWidthPx = 4608, frameHeightPx = 3456,
        )
        // Diffraction blur at f/4 reads 0.71 in the screenshot.
        assertEquals(0.71, Dof.diffractionBlur(4.0, green) / cam.cocOn(pixelLevel), 0.005)
    }

    // ---- Screenshot 1: 35mm film (sharp print), 50mm, f/4, focused at 4 ft --------

    @Test
    fun `35mm film screenshot reproduces near far and hyperfocal`() {
        val cam = Camera(frameWidthMm = 36.0)
        val l = 50.0
        val f = 4.0
        val a = 4.0 * ft

        val budget = Dof.focusBlurBudget(f, cam.cocOn(print12at18), 1.0, green)!!
        assertEquals(3.78, Dof.nearLimit(l, f, a, budget) / ft, 0.01)
        assertEquals(4.25, Dof.farLimit(l, f, a, budget) / ft, 0.01)
        assertEquals(66.4, Dof.hyperfocal(l, f, budget) / ft, 0.3)
        assertEquals(0.17, Dof.diffractionBlur(f, green) / cam.cocOn(print12at18), 0.005)
    }

    @Test
    fun `35mm film screenshot reproduces the magnification readout`() {
        val a = 4.0 * ft
        val l = 50.0
        val m = l / (a - l)
        assertEquals(23.38, 1.0 / m, 0.01)
    }

    // ---- Screenshot 2: OM-D EM-1 (sharp image), 50mm, f/4, focused at 10 m --------

    @Test
    fun `OM-D screenshot reproduces near and far limits`() {
        val cam = Camera(
            type = CameraType.DIGITAL,
            frameWidthMm = 17.3, frameHeightMm = 13.0,
            frameWidthPx = 4608, frameHeightPx = 3456,
        )
        val l = 50.0
        val f = 4.0
        val a = 10_000.0

        val budget = Dof.focusBlurBudget(f, cam.cocOn(pixelLevel), 1.0, green)!!
        assertEquals(9.23, Dof.nearLimit(l, f, a, budget) / 1000.0, 0.02)
        assertEquals(10.9, Dof.farLimit(l, f, a, budget) / 1000.0, 0.02)
    }

    // ---- Appendix C identities ----------------------------------------------------

    @Test
    fun `diffraction blur is the Airy disc, 2 point 44 lambda f`() {
        // f/745 at 550 nm. The reference manual rounds that to f/750; this does not,
        // because the details panel shows its reader the derivation.
        assertEquals(2.44 * 550e-6 * 8.0, Dof.diffractionBlur(8.0, green), 1e-12)
        assertEquals(8.0 / 745.2, Dof.diffractionBlur(8.0, green), 1e-6)
        // Red light diffracts about 20% more than green.
        assertTrue(Dof.diffractionBlur(8.0, 660.0) > Dof.diffractionBlur(8.0, green) * 1.19)
    }

    @Test
    fun `best f stop is where focus blur and diffraction blur are equal`() {
        val l = 45.0
        val dn = 4.5 * 12.0 * 25.4 / 12.0 // 4.5 ft in mm
        val a = 5.0 * ft
        val f = Dof.bestFStop(l, a, dn, green)
        assertEquals(Dof.diffractionBlur(f, green), Dof.focusBlur(l, f, a, dn), 1e-9)
    }

    @Test
    fun `best f stop minimises combined blur`() {
        val l = 45.0
        val a = 5.0 * ft
        val dn = 4.5 * ft
        val best = Dof.bestFStop(l, a, dn, green)
        fun combined(f: Double) = Dof.combine(Dof.diffractionBlur(f, green), Dof.focusBlur(l, f, a, dn))
        assertTrue(combined(best) < combined(best * 1.2))
        assertTrue(combined(best) < combined(best / 1.2))
    }

    @Test
    fun `focus distance blurs the near and far subjects equally`() {
        val dn = 3.0 * ft
        val df = 30.0 * ft
        val a = Dof.bestFocus(dn, df)
        assertEquals(2.0 * dn * df / (dn + df), a, 1e-9)
        val l = 50.0
        val f = 8.0
        assertEquals(Dof.focusBlur(l, f, a, dn), Dof.focusBlur(l, f, a, df), 1e-9)
    }

    @Test
    fun `focusing at the hyperfocal distance holds infinity`() {
        val l = 50.0
        val f = 8.0
        val c = 0.03
        val h = Dof.hyperfocal(l, f, c)
        assertEquals(c, Dof.focusBlur(l, f, h, Dof.INF), 1e-9)
        assertEquals(h / 2.0, Dof.nearLimit(l, f, h, c), h * 0.01)
    }

    @Test
    fun `near far and focus determine each other`() {
        val dn = 3.0 * ft
        val df = 12.0 * ft
        val a = Dof.bestFocus(dn, df)
        assertEquals(dn, Dof.nearFromFocusAndFar(a, df), 1e-6)
        assertEquals(df, Dof.farFromFocusAndNear(a, dn), 1e-6)
    }

    @Test
    fun `beyond the diffraction limit nothing is critically sharp`() {
        val c = 0.0075
        val limit = Dof.fStopForDiffraction(c, green)
        assertEquals(null, Dof.focusBlurBudget(limit * 1.01, c, 1.0, green))
        assertTrue(Dof.focusBlurBudget(limit * 0.5, c, 1.0, green) != null)
        // Raising the allowed blur brings the depth of field back.
        assertTrue(Dof.focusBlurBudget(limit * 1.01, c, 2.0, green) != null)
    }

    @Test
    fun `macro depth of field follows the magnification equation`() {
        val f = 8.0
        val c = 0.0075
        val m = 0.5
        val dof = Dof.macroDof(f, c, m)
        assertEquals(2.0 * f * c * (m + 1.0) / (m * m), dof, 1e-12)
        // The inverse: at half the depth away from focus, blur is exactly the CoC.
        assertEquals(c, Dof.macroFocusBlur(f, m, 100.0, 100.0 + dof / 2.0), 1e-12)
    }

    @Test
    fun `harmonic scale round trips`() {
        val lo = 1.0 * ft
        val hi = 100.0 * ft
        for (x in listOf(0.0, 0.25, 0.5, 0.9, 1.0)) {
            val d = Dof.harmonicToDistance(x, lo, hi)
            assertEquals(x, Dof.distanceToHarmonic(d, lo, hi), 1e-9)
        }
        // With an infinite upper limit the top of the scale is infinity.
        assertTrue(Dof.harmonicToDistance(1.0, lo, Dof.INF).isInfinite())
    }

    @Test
    fun `focus stacking splits the range into equal focus ring rotations`() {
        val l = 50.0
        val c = 0.0075
        val dn = 3.0 * ft
        val df = 30.0 * ft
        val stack = Dof.focusStack(l, dn, df, 4, c, green)

        assertEquals(4, stack.focusPoints.size)
        // Equal steps in reciprocal distance — equal rotations of the focus ring.
        val gaps = stack.focusPoints.map { 1.0 / it }.zipWithNext { a, b -> a - b }
        gaps.forEach { assertEquals(gaps.first(), it, 1e-9) }
        // Focus points run from near to far and stay inside the range.
        assertTrue(stack.focusPoints.first() > dn)
        assertTrue(stack.focusPoints.last() < df)
        assertTrue(stack.focusPoints.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `more images in a stack means less blur`() {
        val l = 50.0
        val c = 0.0075
        val dn = 3.0 * ft
        val df = Dof.INF
        val two = Dof.focusStack(l, dn, df, 2, c, green)
        val eight = Dof.focusStack(l, dn, df, 8, c, green)
        assertTrue(eight.worstBlur < two.worstBlur)
        assertTrue(eight.fStop < two.fStop)
    }

    @Test
    fun `blur is zero at the plane of focus and grows on both sides`() {
        val l = 50.0
        val f = 4.0
        val a = 10_000.0
        assertEquals(0.0, Dof.focusBlur(l, f, a, a), 0.0)
        assertTrue(Dof.focusBlur(l, f, a, a * 0.9) > 0.0)
        assertTrue(Dof.focusBlur(l, f, a, a * 1.1) > 0.0)
        // Blur climbs faster on the near side than the far side.
        assertTrue(Dof.focusBlur(l, f, a, a - 1000.0) > Dof.focusBlur(l, f, a, a + 1000.0))
    }

    @Test
    fun `35mm equivalent focal lengths are divided by the crop factor`() {
        val cam = Camera(
            focalMode = org.kutner.dofpro.model.FocalMode.EQUIV_35,
            frameWidthMm = 18.0,
        )
        assertEquals(2.0, cam.cropFactor, 1e-9)
        assertEquals(25.0, cam.actualFocalLength(50.0), 1e-9)
        assertEquals(50.0, cam.enteredFocalLength(25.0), 1e-9)
    }

    // ---- Cross-check against an independent calculator ------------------------------

    @Test
    fun `with diffraction out of the way we match Cambridge in Colour`() {
        // Their calculator, at a 710 cm print viewed from 50 cm, 35mm full frame, 16 mm,
        // f/8, focused at 2 m, reports 1.75 m / 2.33 m and a hyperfocal of 13.98 m.
        //
        // Inverting the hyperfocal gives the circle of confusion their criterion implies,
        // c = L^2 / (f * (H - L)). Given that same c and no diffraction, these equations
        // land on their published figures to the millimetre - which says the difference
        // between the two apps is entirely in what is fed to the formulas, never in the
        // formulas themselves.
        val l = 16.0
        val f = 8.0
        val a = 2000.0
        val h = 13980.0
        val c = l * l / (f * (h - l))

        assertEquals(0.0022916, c, 1e-7)
        assertEquals(13980.0, Dof.hyperfocal(l, f, c), 0.5)
        assertEquals(1750.0, Dof.nearLimit(l, f, a, c), 5.0)
        assertEquals(2330.0, Dof.farLimit(l, f, a, c), 5.0)

        // With diffraction in play the same criterion gives noticeably more depth in
        // front and much less behind, which is the whole disagreement.
        val bf = Dof.focusBlurBudget(f, c, 1.0, 550.0)
        assertTrue("diffraction alone already exceeds so strict a criterion", bf == null)
    }

}
