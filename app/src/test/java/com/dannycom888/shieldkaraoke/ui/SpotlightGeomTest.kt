package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.25: spotlight pool geometry — radius 1.1–1.3× the dancer's
 * width, squashed to 0.28 vertically, sized off the dancer's
 * rendered height through the frame canvas aspect.
 */
class SpotlightGeomTest {

    @Test
    fun radiusIsInTheSpecBandPerDancerWidth() {
        assertTrue(SpotlightGeom.RADIUS_PER_DANCER_WIDTH in 1.1f..1.3f)
        assertEquals(0.28f, SpotlightGeom.FLATTEN, 0.001f)
    }

    @Test
    fun poolScalesWithDancerHeight() {
        val h = 234f // compact solo dancer: 0.72 × ~325dp stage
        val dancerW = SpotlightGeom.dancerWidthDp(h)
        assertEquals(h * 468f / 600f, dancerW, 0.01f)
        val poolW = SpotlightGeom.poolWidthDp(h)
        assertEquals(dancerW * 2f * 1.2f, poolW, 0.01f)
        assertEquals(poolW * 0.28f, SpotlightGeom.poolHeightDp(h), 0.01f)
        // Bigger dancer, proportionally bigger pool.
        assertTrue(SpotlightGeom.poolWidthDp(300f) > poolW)
    }

    @Test
    fun poolIsWiderThanTheDancerButFlatterThanHer() {
        val h = 200f
        assertTrue(SpotlightGeom.poolWidthDp(h) > SpotlightGeom.dancerWidthDp(h))
        // Height = 2 × radius-factor × flatten × dancer width
        // ≈ 0.52 × dancer height: a wide, flat pool of light.
        assertEquals(
            SpotlightGeom.dancerWidthDp(h) * 2f *
                SpotlightGeom.RADIUS_PER_DANCER_WIDTH * SpotlightGeom.FLATTEN,
            SpotlightGeom.poolHeightDp(h),
            0.01f,
        )
        assertTrue(SpotlightGeom.poolHeightDp(h) < h)
    }
}
