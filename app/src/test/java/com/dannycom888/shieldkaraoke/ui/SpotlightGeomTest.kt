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

    // ---- v1.0.45: the six-dancer row --------------------------
    // The six stand at a centre pitch of 0.60 × height (frames
    // overlap their transparent margins), so the row spans
    // 0.78 + 5 × 0.60 = 3.78 × height. Height targets 0.46 of the
    // screen height, capped so the span fits in 98% of the width.

    @Test
    fun sixRowPitchAndSpanArePinned() {
        assertEquals(0.60f, SpotlightGeom.SIX_ROW_PITCH_FRACTION, 0.001f)
        assertEquals(3.78f, SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT, 0.001f)
        // Pitch is tighter than a frame width: the frames overlap.
        assertTrue(SpotlightGeom.SIX_ROW_PITCH_FRACTION < SpotlightGeom.FRAME_ASPECT)
    }

    @Test
    fun sixRowOn16x9TvReachesTheTarget() {
        // With the tightened pitch the width cap no longer binds
        // on 16:9: at 1920×1080 dp the height is the full 0.46
        // target and the span is ≈97.8% of the width.
        val h = SpotlightGeom.sixRowHeightDp(1920f, 1080f)
        assertEquals(0.46f * 1080f, h, 0.5f)
        assertTrue(h * SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT <= 0.98f * 1920f)
        // Same screen at half the density (960×540 dp): still the
        // target, span ≈97.8% of the width.
        val h2 = SpotlightGeom.sixRowHeightDp(960f, 540f)
        assertEquals(0.46f * 540f, h2, 0.5f)
        assertEquals(0.46f, h2 / 540f, 0.002f)
    }

    @Test
    fun sixRowOnWidePhoneReachesTheTarget() {
        // The user's phone in landscape, ≈869×411 dp: height is
        // the 0.46 target; the span uses only ≈82% of the width.
        val h = SpotlightGeom.sixRowHeightDp(869f, 411f)
        assertEquals(0.46f * 411f, h, 0.5f)
        assertEquals(0.46f, h / 411f, 0.002f)
    }

    @Test
    fun sixRowOnNarrowScreenIsWidthCapped() {
        // 4:3, 1024×768 dp: width budget 1003.52 / 3.78 ≈ 265.5.
        val h = SpotlightGeom.sixRowHeightDp(1024f, 768f)
        assertEquals(265.48f, h, 0.5f)
        assertEquals(0.346f, h / 768f, 0.002f)
        assertTrue(h < 0.46f * 768f)
    }

    @Test
    fun sixRowNeverExceedsEitherBudget() {
        val sizes = listOf(
            1920f to 1080f, 960f to 540f, 869f to 411f,
            1280f to 720f, 800f to 360f, 640f to 480f, 1024f to 768f,
        )
        for ((w, hh) in sizes) {
            val h = SpotlightGeom.sixRowHeightDp(w, hh)
            val span = h * SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT
            assertTrue("row too wide on $w x $hh", span <= 0.98f * w + 0.5f)
            assertTrue("row too tall on $w x $hh", h <= 0.46f * hh + 0.01f)
            assertTrue("row not positive on $w x $hh", h > 0f)
        }
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
