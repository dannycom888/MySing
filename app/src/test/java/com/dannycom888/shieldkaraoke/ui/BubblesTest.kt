package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.25: the ambient bubble field is a pure function of a fixed
 * seed and elapsed time — deterministic, in-range, endlessly
 * recycling.
 */
class BubblesTest {

    @Test
    fun generationIsDeterministic() {
        assertEquals(Bubbles.generate(20), Bubbles.generate(20))
        assertEquals(Bubbles.generate(14, 42L), Bubbles.generate(14, 42L))
    }

    @Test
    fun countsMatchTheScreenModes() {
        assertEquals(Bubbles.COUNT_FULL, Bubbles.generate(Bubbles.COUNT_FULL).size)
        assertEquals(Bubbles.COUNT_COMPACT, Bubbles.generate(Bubbles.COUNT_COMPACT).size)
        assertTrue(Bubbles.COUNT_COMPACT < Bubbles.COUNT_FULL)
    }

    @Test
    fun everyBubbleStaysInSpecRanges() {
        for (b in Bubbles.generate(64)) {
            assertTrue(b.radiusDp in Bubbles.MIN_RADIUS_DP..Bubbles.MAX_RADIUS_DP)
            assertTrue(b.alpha in Bubbles.MIN_ALPHA..Bubbles.MAX_ALPHA)
            assertTrue(b.cycleSec in Bubbles.MIN_CYCLE_SEC..Bubbles.MAX_CYCLE_SEC)
            assertTrue(b.xFrac in 0f..1f)
            assertTrue(b.phase in 0f..1f)
            assertTrue(b.swayAmpDp in Bubbles.MIN_SWAY_DP..Bubbles.MAX_SWAY_DP)
        }
    }

    @Test
    fun slowEndCrossesInEightToFourteenSeconds() {
        // The slowest bubble in a big field must be in the stately
        // band the design calls for.
        val slowest = Bubbles.generate(64).maxOf { it.cycleSec }
        assertTrue(slowest >= 8f)
    }

    @Test
    fun bubblesDriftDownAndWrap() {
        val b = Bubbles.generate(1)[0]
        val h = 400f
        val y0 = Bubbles.yDpAt(b, 0f, h)
        val y1 = Bubbles.yDpAt(b, 1f, h)
        assertTrue("must move down: $y0 -> $y1", y1 > y0)
        // One full cycle later the bubble is back where it started.
        val yWrap = Bubbles.yDpAt(b, b.cycleSec, h)
        assertEquals(y0, yWrap, 0.5f)
        // And it never leaves the travel band.
        for (t in listOf(0f, 3f, 9f, 27f, 100f)) {
            val y = Bubbles.yDpAt(b, t, h)
            assertTrue(y >= -b.radiusDp - 0.01f && y <= h + b.radiusDp + 0.01f)
        }
    }

    @Test
    fun swayNeverWandersFarFromTheRestingSpot() {
        val b = Bubbles.generate(1)[0]
        val w = 800f
        for (t in listOf(0f, 1.3f, 2.7f, 5.9f, 40f)) {
            val x = Bubbles.xDpAt(b, t, w)
            val rest = b.xFrac * w
            assertTrue(kotlin.math.abs(x - rest) <= b.swayAmpDp + 0.01f)
        }
    }

    @Test
    fun fieldIsSpreadOverTheHeightAtTimeZero() {
        // Phases stagger the field: at t=0 bubbles must not all be
        // stacked at the top.
        val h = 400f
        val ys = Bubbles.generate(Bubbles.COUNT_FULL).map { Bubbles.yDpAt(it, 0f, h) }
        assertTrue(ys.max() - ys.min() > h * 0.5f)
    }
}
