package org.kutner.dofpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kutner.dofpro.model.DistanceWindow
import org.kutner.dofpro.ui.DistanceAxis
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * How a drag on the distance scale converts finger travel into distance.
 *
 * The scale is sized so the depth of field always fills a quarter of the height, which up
 * close can leave the whole view a few millimetres wide. A constant rate cannot serve both
 * ends of that: matching such a view crawls, and outrunning it loses the precision the
 * close-up work needs. So the rate grows with how far the finger has already gone.
 */
class DragCurveTest {

    private val height = 1786f

    /**
      * A view a few millimetres wide, as a 61 MP body at f/5.6 produces at two metres:
      * 3.6 mm of depth given a quarter of the height, so about 14 mm end to end.
      */
    private fun tight() = DistanceAxis(DistanceWindow(1992.8, 2007.2))

    /** An ordinary view, half a decade or so across. */
    private fun ordinary() = DistanceAxis(DistanceWindow(3.16e3, 5.06e3))

    @Test
    fun `a short drag runs at the visible scale's own rate`() {
        val axis = ordinary()
        val plainRate = axis.lnSpan / height
        // Within the first tenth of a screen the curve must be indistinguishable from the
        // plain rate, or the graduations accelerate out from under the finger and fine
        // placement becomes impossible.
        for (travel in listOf(20f, 50f, 100f)) {
            val carried = axis.travelToLn(travel, height)
            val plain = plainRate * travel
            assertTrue(
                "at $travel px the curve ran ${carried / plain} times the plain rate",
                carried / plain < 1.05,
            )
        }
    }

    @Test
    fun `a long drag crosses from near to far`() {
        // A whole screen of travel should cover orders of magnitude, whatever the zoom.
        assertTrue(exp(tight().travelToLn(height, height)) > 8.0)
        assertTrue(exp(ordinary().travelToLn(height, height)) > 8.0)
    }

    @Test
    fun `close up, a deliberate nudge resolves less than the depth of field`() {
        // The point of the fine end: at two metres with 3.6 mm of depth, a short drag has
        // to move the subject by less than that, or it cannot be placed inside its own
        // depth of field at all.
        val nudged = 2000.0 - 2000.0 * exp(-tight().travelToLn(60f, height))
        assertTrue("a 60 px nudge moved $nudged mm", nudged < 3.6)
        assertTrue("and it should move at all", nudged > 0.1)

        // A longer drag is deliberately coarser: this is the trade that lets one gesture
        // still reach the far distance.
        val pulled = 2000.0 - 2000.0 * exp(-tight().travelToLn(600f, height))
        assertTrue("a 600 px drag moved only $pulled mm", pulled > 50.0)
    }

    @Test
    fun `the rate only ever grows with travel`() {
        val axis = ordinary()
        var previous = 0.0
        var travel = 20f
        while (travel <= 2200f) {
            val rate = axis.travelToLn(travel, height) / travel
            assertTrue("rate fell at $travel px", rate >= previous - 1e-12)
            previous = rate
            travel += 20f
        }
    }

    @Test
    fun `dragging out and back returns exactly where it started`() {
        // The curve is a function of total travel, not a running sum of rates, so a drag
        // that wanders and comes back leaves the value untouched.
        val axis = ordinary()
        assertEquals(0.0, axis.travelToLn(0f, height), 1e-12)
        for (travel in listOf(37f, 240f, 900f, 1500f)) {
            assertEquals(
                -axis.travelToLn(travel, height),
                axis.travelToLn(-travel, height),
                1e-12,
            )
        }
    }

    @Test
    fun `the curve has no corner where it changes to its coarse rate`() {
        val axis = ordinary()
        var previous: Double? = null
        var travel = 1600f
        // Straddling a full screen of travel, where the cubic hands over to a straight
        // line, successive steps must stay even — the two pieces meet in slope as well
        // as in value.
        while (travel <= 2000f) {
            val step = axis.travelToLn(travel + 10f, height) - axis.travelToLn(travel, height)
            previous?.let { assertTrue("kink at $travel px", abs(step - it) < it * 0.05) }
            previous = step
            travel += 10f
        }
    }

    @Test
    fun `a wide view is never dragged slower than it reads`() {
        // Past the hyperfocal distance the view opens to four decades. The curve must not
        // slow that down in the name of accelerating it.
        val wide = DistanceAxis(DistanceWindow(1.0, 10_000_000.0))
        val plainRate = wide.lnSpan / height
        assertTrue(wide.travelToLn(500f, height) >= plainRate * 500f - 1e-9)
    }

    @Test
    fun `the rate the drag actually uses integrates back to the documented curve`() {
        // The drag steps the value along by the rate at each moment, so that rate has to
        // be this curve's derivative or the two descriptions drift apart.
        val axis = ordinary()
        var travel = 0f
        var summed = 0.0
        val step = 0.5f
        while (travel < 1500f) {
            summed += axis.slopeAt(travel + step / 2f, height) * step
            travel += step
        }
        assertEquals(axis.travelToLn(1500f, height), summed, 1e-4)
    }

    @Test
    fun `a flick covers more ground than the same drag made slowly`() {
        val slow = DistanceAxis.speedBoost(0f)
        val brisk = DistanceAxis.speedBoost(1400f)
        val flick = DistanceAxis.speedBoost(6000f)
        assertEquals(1.0, slow, 1e-9)
        assertTrue("a brisk drag should tell", brisk > slow * 1.5)
        assertTrue("a flick should tell more", flick > brisk)
        // But bounded, or a stray fast sample would throw the subject across the scale.
        assertTrue("boost ran away at $flick", flick <= 4.0)
        assertEquals(flick, DistanceAxis.speedBoost(60000f), 1e-9)
    }

    @Test
    fun `speed is read the same in both directions`() {
        assertEquals(DistanceAxis.speedBoost(2000f), DistanceAxis.speedBoost(-2000f), 1e-12)
    }

    @Test
    fun `every zoom reaches the same speed once fully under way`() {
        // Whatever it started from, a long drag ends up covering ground at the same rate,
        // so the feel does not depend on how deep the depth of field happened to be.
        val far = 2400f
        val a = tight().travelToLn(far + 10f, height) - tight().travelToLn(far, height)
        val b = ordinary().travelToLn(far + 10f, height) - ordinary().travelToLn(far, height)
        assertEquals(a, b, a * 0.05)
        assertEquals(ln(1000.0) / height * 10f, a, a * 0.05)
    }
}
