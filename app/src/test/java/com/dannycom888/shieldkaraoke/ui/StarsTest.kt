package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v1.0.57: the star field twinkles SLOWLY — the user asked for
 * stars that "慢慢地闪亮". These pins make the slowness a
 * contract: a hard floor on the twinkle period, a sine that
 * never reaches darkness, and a smoothness bound no strobe
 * could satisfy.
 */
class StarsTest {

    @Test
    fun generationIsDeterministic() {
        assertEquals(Stars.generate(32), Stars.generate(32))
        assertEquals(Stars.generate(16, 7L), Stars.generate(16, 7L))
    }

    @Test
    fun countsMatchTheScreenModes() {
        assertEquals(Stars.COUNT_FULL, Stars.generate(Stars.COUNT_FULL).size)
        assertEquals(Stars.COUNT_COMPACT, Stars.generate(Stars.COUNT_COMPACT).size)
        assertTrue(Stars.COUNT_COMPACT < Stars.COUNT_FULL)
        // Restrained next to the bubbles' 20: enough to read as
        // a sky, never a snowstorm.
        assertTrue(Stars.COUNT_FULL in 40..90)
    }

    @Test
    fun everyStarStaysInSpecRanges() {
        for (s in Stars.generate(128)) {
            assertTrue(s.xFrac in 0f..1f)
            assertTrue(s.yFrac in 0f..1f)
            assertTrue(s.phase in 0f..1f)
            assertTrue(s.radiusDp in Stars.MIN_RADIUS_DP..Stars.MAX_RADIUS_DP)
            assertTrue(s.peakAlpha in Stars.MIN_PEAK_ALPHA..Stars.MAX_PEAK_ALPHA)
            assertTrue(s.periodSec in Stars.MIN_PERIOD_SEC..Stars.MAX_PERIOD_SEC)
        }
    }

    @Test
    fun twinklePeriodHasAHardSlowFloor() {
        // The anti-strobe pin: several seconds per cycle, and
        // the floor itself may never drop below three seconds.
        assertTrue(Stars.MIN_PERIOD_SEC >= 3f)
        assertTrue(Stars.generate(256).all { it.periodSec >= 3f })
        assertTrue(Stars.MAX_PERIOD_SEC > Stars.MIN_PERIOD_SEC)
    }

    @Test
    fun starsNeverGoDarkAndNeverExceedTheirPeak() {
        for (s in Stars.generate(Stars.COUNT_FULL)) {
            var t = 0f
            while (t < 60f) {
                val a = Stars.alphaAt(s, t)
                assertTrue(
                    "alpha $a outside the star's glow band at t=$t",
                    a >= s.peakAlpha * Stars.MIN_ALPHA_FRACTION - 0.001f &&
                        a <= s.peakAlpha + 0.001f,
                )
                assertTrue("a star never fully winks out", a > 0f)
                t += 0.37f
            }
        }
        assertTrue(Stars.MIN_ALPHA_FRACTION > 0f)
    }

    @Test
    fun twinkleIsSmoothNeverAFlash() {
        // Over a 100 ms step (three ambient frames) no star may
        // change brightness by more than a tenth of its peak —
        // a strobe or a blink would blow straight through this.
        for (s in Stars.generate(Stars.COUNT_FULL)) {
            var t = 0f
            while (t < 30f) {
                val jump = abs(Stars.alphaAt(s, t + 0.1f) - Stars.alphaAt(s, t))
                assertTrue(
                    "brightness jump $jump too fast at t=$t (period ${s.periodSec}s)",
                    jump <= s.peakAlpha * 0.10f,
                )
                t += 0.53f
            }
        }
    }

    @Test
    fun twinkleActuallyBreathes() {
        // Slow, but alive: over one full period each star must
        // sweep most of its glow band.
        for (s in Stars.generate(Stars.COUNT_FULL)) {
            var lo = Float.MAX_VALUE
            var hi = 0f
            var t = 0f
            while (t < s.periodSec) {
                val a = Stars.alphaAt(s, t)
                lo = minOf(lo, a)
                hi = maxOf(hi, a)
                t += s.periodSec / 40f
            }
            assertTrue(
                "star barely twinkles: $lo..$hi of peak ${s.peakAlpha}",
                hi - lo > s.peakAlpha * 0.5f,
            )
        }
    }

    @Test
    fun fieldIsSpreadAcrossTheSky() {
        val stars = Stars.generate(Stars.COUNT_FULL)
        assertTrue(stars.maxOf { it.xFrac } - stars.minOf { it.xFrac } > 0.5f)
        assertTrue(stars.maxOf { it.yFrac } - stars.minOf { it.yFrac } > 0.5f)
        // Independent phases: the field never pulses in unison.
        assertTrue(stars.map { it.phase }.distinct().size > stars.size / 2)
    }
}
