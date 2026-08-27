package org.kutner.dofpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kutner.dofpro.model.DistanceUnit
import org.kutner.dofpro.model.UnitSystem
import org.kutner.dofpro.model.formatDistance

/**
 * The unit a distance is written in is not a setting — only the family is. These pin the
 * thresholds where one unit hands over to the next, and the precision each one carries.
 */
class UnitsTest {

    private val inch = 25.4
    private val foot = 304.8

    // ---- Metric --------------------------------------------------------------------

    @Test
    fun `metres from half a metre up`() {
        assertEquals("0.5 m", formatDistance(500.0, UnitSystem.METRIC))
        assertEquals("2 m", formatDistance(2_000.0, UnitSystem.METRIC))
        // Just below the threshold it is centimetres, not 0.49 m.
        assertEquals("49 cm", formatDistance(490.0, UnitSystem.METRIC))
    }

    @Test
    fun `no decimals past ten metres`() {
        assertEquals("13 m", formatDistance(13_060.0, UnitSystem.METRIC))
        assertEquals("4000 m", formatDistance(4_000_000.0, UnitSystem.METRIC))
        // And at ten metres or below they are still allowed.
        assertTrue(formatDistance(9_990.0, UnitSystem.METRIC).contains('.'))
    }

    @Test
    fun `whole centimetres between twenty and fifty`() {
        assertEquals("20 cm", formatDistance(200.0, UnitSystem.METRIC))
        assertEquals("35 cm", formatDistance(348.0, UnitSystem.METRIC))
        assertEquals("49 cm", formatDistance(494.0, UnitSystem.METRIC))
    }

    @Test
    fun `tenths of a centimetre below twenty`() {
        assertEquals("19.9 cm", formatDistance(199.0, UnitSystem.METRIC))
        assertEquals("5.0 cm", formatDistance(50.0, UnitSystem.METRIC))
        assertEquals("0.4 cm", formatDistance(4.0, UnitSystem.METRIC))
    }

    @Test
    fun `every metric distance is metres or centimetres`() {
        var mm = 1.0
        while (mm < 1e7) {
            val unit = UnitSystem.METRIC.formatFor(mm).unit
            assertTrue("$mm mm came out in ${unit.label}", unit == DistanceUnit.M || unit == DistanceUnit.CM)
            mm *= 1.07
        }
    }

    // ---- Imperial ------------------------------------------------------------------

    @Test
    fun `feet from two feet up, inches below`() {
        assertEquals("2 ft", formatDistance(2 * foot, UnitSystem.IMPERIAL))
        assertEquals("3 ft", formatDistance(3 * foot, UnitSystem.IMPERIAL))
        // A foot and a half is spoken as eighteen inches, not as 1.5 ft.
        assertEquals("18 in", formatDistance(18 * inch, UnitSystem.IMPERIAL))
        assertEquals("12 in", formatDistance(foot, UnitSystem.IMPERIAL))
        assertEquals("8 in", formatDistance(8 * inch, UnitSystem.IMPERIAL))
        // Right up against the changeover it is still inches.
        assertEquals("23.9 in", formatDistance(2 * foot - 0.1 * inch, UnitSystem.IMPERIAL))
    }

    @Test
    fun `every imperial distance is feet or inches`() {
        var mm = 1.0
        while (mm < 1e7) {
            val unit = UnitSystem.IMPERIAL.formatFor(mm).unit
            assertTrue("$mm mm came out in ${unit.label}", unit == DistanceUnit.FT || unit == DistanceUnit.IN)
            mm *= 1.07
        }
    }

    @Test
    fun `no decimals past thirty feet`() {
        // Imperial's own round number, not a translation of metric's ten metres.
        assertEquals("40 ft", formatDistance(40.2 * foot, UnitSystem.IMPERIAL))
        assertTrue(formatDistance(20.4 * foot, UnitSystem.IMPERIAL).contains('.'))
        assertEquals(null, UnitSystem.IMPERIAL.formatFor(29.0 * foot).decimals)
        assertEquals(0, UnitSystem.IMPERIAL.formatFor(31.0 * foot).decimals)
    }

    @Test
    fun `inches keep the precision they need`() {
        // They only ever cover the foot below a foot, and rounding them whole would be a
        // quantum two and a half times coarser than the whole centimetres metric uses.
        assertEquals("11 in", formatDistance(11 * inch, UnitSystem.IMPERIAL))
        assertEquals("7.87 in", formatDistance(200.0, UnitSystem.IMPERIAL))
        assertEquals("1.5 in", formatDistance(1.5 * inch, UnitSystem.IMPERIAL))
        assertEquals("0.2 in", formatDistance(0.2 * inch, UnitSystem.IMPERIAL))
    }

    @Test
    fun `the two families are independent`() {
        // Each is built from the numbers its own readers use, so their changeovers fall at
        // different distances. Nothing should be tempted to line them up again.
        assertEquals(DistanceUnit.M, UnitSystem.METRIC.formatFor(550.0).unit)
        assertEquals(DistanceUnit.IN, UnitSystem.IMPERIAL.formatFor(550.0).unit)
        // 9.5 m is inside metric's ten metres but past imperial's thirty feet.
        assertEquals(null, UnitSystem.METRIC.formatFor(9_500.0).decimals)
        assertEquals(0, UnitSystem.IMPERIAL.formatFor(9_500.0).decimals)
    }

    // ---- One unit for a whole scale ------------------------------------------------

    @Test
    fun `the changeover is where the subject is, wherever the scale has scrolled to`() {
        // The whole column takes one unit from the subject distance. Taking it from the
        // window instead moved the changeover about as the marker was dragged off the
        // middle of the scale, so feet arrived somewhere short of two.
        assertEquals(DistanceUnit.IN, UnitSystem.IMPERIAL.formatFor(2 * foot - 1.0).unit)
        assertEquals(DistanceUnit.FT, UnitSystem.IMPERIAL.formatFor(2 * foot).unit)
        assertEquals(DistanceUnit.CM, UnitSystem.METRIC.formatFor(499.0).unit)
        assertEquals(DistanceUnit.M, UnitSystem.METRIC.formatFor(500.0).unit)
    }

    @Test
    fun `the format leaves precision open where the rules do not fix it`() {
        // Between half a metre and ten, the read-outs may take as many digits as they need
        // to tell a shallow depth of field apart. Outside that band the rules are absolute.
        assertEquals(null, UnitSystem.METRIC.formatFor(2_000.0).decimals)
        assertEquals(0, UnitSystem.METRIC.formatFor(20_000.0).decimals)
        assertEquals(0, UnitSystem.METRIC.formatFor(300.0).decimals)
        assertEquals(1, UnitSystem.METRIC.formatFor(100.0).decimals)
    }

    @Test
    fun `a tight depth of field still reads as three different numbers`() {
        val f = UnitSystem.METRIC.formatFor(2_000.0)
        val shown = listOf(1_996.4, 2_000.0, 2_003.6).map { f.text(it, sig = 6) }
        assertEquals(3, shown.toSet().size)
    }

    // ---- Typing a distance in ------------------------------------------------------

    @Test
    fun `a typed distance is read in the unit on screen`() {
        assertEquals(3_000.0, UnitSystem.METRIC.formatFor(2_000.0).parse("3")!!, 1e-9)
        // The same "3" means something else when the column is in centimetres.
        assertEquals(30.0, UnitSystem.METRIC.formatFor(100.0).parse("3")!!, 1e-9)
        assertEquals(3 * foot, UnitSystem.IMPERIAL.formatFor(2_000.0).parse("3")!!, 1e-9)
        assertEquals(3 * inch, UnitSystem.IMPERIAL.formatFor(300.0).parse("3")!!, 1e-9)
    }

    @Test
    fun `a typed distance survives being written back out`() {
        // What is typed is what the read-out then shows, so the field is not silently
        // rounding the value away.
        val f = UnitSystem.METRIC.formatFor(2_000.0)
        assertEquals("2.5 m", f.text(f.parse("2.5")!!))
        val g = UnitSystem.IMPERIAL.formatFor(3_000.0)
        assertEquals("9.5 ft", g.text(g.parse("9.5")!!))
    }

    @Test
    fun `a comma is taken as a decimal point`() {
        // The numbers are written with a point whatever the phone's locale, so a keyboard
        // offering a comma must not produce something that reads back as nonsense.
        assertEquals(2_500.0, UnitSystem.METRIC.formatFor(2_000.0).parse("2,5")!!, 1e-9)
    }

    @Test
    fun `nonsense and impossible distances are refused`() {
        val f = UnitSystem.METRIC.formatFor(2_000.0)
        for (typed in listOf("", "   ", "abc", "-3", "0", "1.2.3", "∞", "NaN")) {
            assertNull("\"$typed\" was accepted", f.parse(typed))
        }
        // Whitespace around a real number is fine.
        assertEquals(4_000.0, f.parse("  4 ")!!, 1e-9)
    }

    @Test
    fun `a distance is not written with a unit it cannot be read back in`() {
        // number() is what fills the editor and text() is what the read-out shows; the
        // first must be the second with the unit taken off, or typing over a value would
        // change it.
        for (mm in listOf(30.0, 150.0, 300.0, 900.0, 4_000.0, 40_000.0)) {
            for (system in UnitSystem.entries) {
                val f = system.formatFor(mm)
                assertEquals(f.text(mm), "${f.number(mm)} ${f.unit.label}")
                assertEquals(mm, f.parse(f.number(mm))!!, mm * 0.005)
            }
        }
    }

    // ---- Settings carried over ------------------------------------------------------

    @Test
    fun `an older stored unit is carried to its family`() {
        // The setting used to name one fixed unit. An imperial user must not be quietly
        // switched to metric by upgrading.
        for (old in listOf("FT", "IN", "YD")) {
            assertEquals(old, UnitSystem.IMPERIAL, UnitSystem.parse(old))
        }
        for (old in listOf("M", "CM", "MM")) {
            assertEquals(old, UnitSystem.METRIC, UnitSystem.parse(old, UnitSystem.IMPERIAL))
        }
        assertEquals(UnitSystem.METRIC, UnitSystem.parse("METRIC"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.parse("IMPERIAL", UnitSystem.METRIC))
        // Anything unrecognised, missing or blank keeps what the app already had.
        assertEquals(UnitSystem.METRIC, UnitSystem.parse("nonsense", UnitSystem.METRIC))
        assertEquals(UnitSystem.METRIC, UnitSystem.parse(null, UnitSystem.METRIC))
        assertEquals(UnitSystem.METRIC, UnitSystem.parse("", UnitSystem.METRIC))
        // The stored value is what Settings writes, so a round trip has to survive.
        for (s in UnitSystem.entries) assertEquals(s, UnitSystem.parse(s.name))
    }
}
