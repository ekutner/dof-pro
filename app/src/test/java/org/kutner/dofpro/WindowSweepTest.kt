package org.kutner.dofpro

import org.junit.Assert.assertTrue
import org.junit.Test
import org.kutner.dofpro.model.Camera
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.Lens
import org.kutner.dofpro.model.Settings
import kotlin.math.ln

/**
 * The distance scale's window, over every combination the app can be put in.
 *
 * This exists because of a crash that only appeared when a camera was *changed*: switching
 * from a full frame body to Micro 4/3 made the depth of field shallow enough that giving
 * it its guaranteed quarter of the height needed a window narrower than the pinch-in
 * floor, and the two bounds came out inverted. The arithmetic was right for every state
 * the app had been left sitting in and wrong for one it could be moved to, which is
 * exactly the kind of gap that tapping through a few screens does not find.
 *
 * So this sweeps rather than samples: every stock camera against every stock lens, at
 * apertures the lens has and distances it can focus, stacking on and off, and — the case
 * that actually broke — with the span held from a previous camera while a new one is
 * chosen underneath it.
 */
class WindowSweepTest {

    private fun stateWith(camera: Camera, lens: Lens): DofState = DofState(
        Settings(cameras = listOf(camera), lenses = listOf(lens))
    )

    /** Everything the scale needs from a window, and every way it can be malformed. */
    private fun check(where: String, s: DofState) {
        val r = s.compute()
        val w = s.distanceWindow(r)
        assertTrue("$where: window lo ${w.lo} is not a positive number", w.lo > 0.0 && w.lo.isFinite())
        assertTrue("$where: window hi ${w.hi} is not a positive number", w.hi > 0.0 && w.hi.isFinite())
        assertTrue("$where: window ${w.lo}..${w.hi} is inverted or empty", w.hi > w.lo)
        val span = ln(w.hi / w.lo)
        assertTrue("$where: span $span is not usable", span > 0.0 && span.isFinite())
        // The subject has to be somewhere the scale can draw it, or the marker is off-scale.
        assertTrue("$where: subject ${r.subject} outside ${w.lo}..${w.hi}",
            r.subject >= w.lo * 0.999 && r.subject <= w.hi * 1.001)
    }

    private fun sweep(stacking: Boolean, holdSpan: Boolean) {
        var cases = 0
        for (camera in Camera.defaults()) {
            for (lens in Lens.defaults()) {
                val s = stateWith(camera, lens)
                s.stacking = stacking
                val focals = if (lens.isZoom) {
                    listOf(lens.minFocal, (lens.minFocal + lens.maxFocal) / 2.0, lens.maxFocal)
                } else {
                    listOf(lens.minFocal)
                }
                for (focal in focals) {
                    s.changeFocalLength(focal, settle = false)
                    for (stop in s.apertureStops()) {
                        s.fStop = stop
                        var d = s.minSubject
                        while (d < DofState.MAX_DISTANCE) {
                            s.moveSubject(d)
                            if (holdSpan) {
                                val w = s.distanceWindow(s.compute())
                                s.holdDistanceSpan(ln(w.hi / w.lo))
                            }
                            check("${camera.name} / ${lens.name} / $focal mm / f/$stop / $d mm", s)
                            cases++
                            d *= 6.0
                        }
                    }
                }
            }
        }
        assertTrue("the sweep did not actually run", cases > 2_000)
    }

    @Test
    fun `every camera, lens, aperture and distance gives a drawable window`() {
        sweep(stacking = false, holdSpan = false)
    }

    @Test
    fun `and again with the span held, which is what the crash needed`() {
        sweep(stacking = false, holdSpan = true)
    }

    @Test
    fun `and again while focus stacking`() {
        sweep(stacking = true, holdSpan = false)
    }

    @Test
    fun `changing camera underneath a held span never leaves an inverted window`() {
        // The reported failure, in the order it happened: settle on one camera, let the
        // span become the user's, then choose a different body from the list.
        val cameras = Camera.defaults()
        for (lens in Lens.defaults()) {
            for (from in cameras) {
                val s = DofState(Settings(cameras = cameras, lenses = listOf(lens)))
                s.cameraIndex = cameras.indexOf(from)
                s.changeFocalLength(lens.maxFocal, settle = false)
                s.fStop = s.apertureStops().first()
                s.moveSubject(2_000.0)
                val w = s.distanceWindow(s.compute())
                s.holdDistanceSpan(ln(w.hi / w.lo))

                for (to in cameras.indices) {
                    s.cameraIndex = to
                    check("${from.name} -> ${cameras[to].name} on ${lens.name}", s)
                }
            }
        }
    }

    @Test
    fun `pinching to either end never leaves an inverted window`() {
        for (camera in Camera.defaults()) {
            val s = stateWith(camera, Lens.defaults().first { it.isZoom })
            s.changeFocalLength(s.lens.maxFocal, settle = false)
            s.moveSubject(1_000.0)
            val rest = ln(s.distanceWindow(s.compute()).let { it.hi / it.lo })
            repeat(30) { s.zoomDistance(1.6, rest) }
            check("${camera.name} pinched all the way in", s)
            repeat(60) { s.zoomDistance(1 / 1.6, rest) }
            check("${camera.name} pinched all the way out", s)
        }
    }
}
