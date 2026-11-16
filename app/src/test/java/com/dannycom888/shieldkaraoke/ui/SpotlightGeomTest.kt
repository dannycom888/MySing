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

    // ---- the six-dancer row (v1.0.45, reworked v1.0.48) -------
    // v1.0.48: pitch 0.48 × height (from the measured figure
    // widths — see SpotlightGeom), so the row spans
    // 0.78 + 5 × 0.48 = 3.18 × height. Height targets 0.58 of the
    // screen height, capped so the span fits in the width left of
    // the queue panel's reserve band (0.19 × screenW + 12 dp);
    // the row is centred in that available width, so its right
    // edge can never slide under the panel.

    @Test
    fun sixRowPitchAndSpanArePinned() {
        assertEquals(0.48f, SpotlightGeom.SIX_ROW_PITCH_FRACTION, 0.001f)
        assertEquals(3.18f, SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT, 0.001f)
        // Pitch is tighter than a frame width: the frames overlap.
        assertTrue(SpotlightGeom.SIX_ROW_PITCH_FRACTION < SpotlightGeom.FRAME_ASPECT)
    }

    @Test
    fun sixRowPitchLeavesOnlyTheWidestPairSlightlyOverlapping() {
        // Measured neighbouring-pair half-sums of the figure
        // widths (max of the clapping and invite cutouts, units
        // of height): Amy–Xiuying, Xiuying–sumo, sumo–Hannah,
        // Hannah–baby, baby–robot. At the 0.48 pitch every pair
        // keeps an edge gap of at least +0.02 except the widest
        // pair (Xiuying's spread sleeves against the sumo), whose
        // bounding boxes overlap by at most 0.05 — the slight
        // wide-arm overlap the user allows.
        val pairHalfSums = listOf(0.4517f, 0.5242f, 0.3767f, 0.2917f, 0.3333f)
        val gaps = pairHalfSums.map { SpotlightGeom.SIX_ROW_PITCH_FRACTION - it }
        assertEquals(1, gaps.count { it < 0f })
        assertTrue(gaps.min() >= -0.05f)
        assertTrue(gaps.filter { it >= 0f }.min() >= 0.02f)
    }

    @Test
    fun sixRowOn16x9TvIsPanelCapped() {
        // Reserve = 0.19 × 1920 + 12 = 376.8, leaving 1543.2;
        // 1543.2 / 3.18 ≈ 485.3 — the panel cap binds below the
        // 0.58 target (626.4), at ≈0.449 of the screen height.
        val reserve = LyricsBand.reserveWidthDp(1920f)
        assertEquals(376.8f, reserve, 0.01f)
        val h = SpotlightGeom.sixRowHeightDp(1920f, 1080f, reserve)
        assertEquals(485.3f, h, 0.5f)
        assertEquals(0.449f, h / 1080f, 0.002f)
        assertTrue(h < 0.58f * 1080f)
        // Width-bound: the row starts at the left edge and its
        // right edge lands exactly on the reserve band's left edge.
        assertEquals(0f, SpotlightGeom.sixRowLeftDp(1920f, h, reserve), 0.5f)
        // Same screen at half the density (960×540 dp): reserve
        // 194.4, cap 765.6 / 3.18 ≈ 240.8 ≈ 0.446 of the height.
        val reserve2 = LyricsBand.reserveWidthDp(960f)
        val h2 = SpotlightGeom.sixRowHeightDp(960f, 540f, reserve2)
        assertEquals(240.8f, h2, 0.5f)
        assertEquals(0.446f, h2 / 540f, 0.002f)
    }

    @Test
    fun sixRowOnWidePhoneIsPanelCapped() {
        // The user's phone in landscape, ≈869×411 dp: reserve
        // 177.11, cap 691.89 / 3.18 ≈ 217.6 ≈ 0.529 of the height
        // (up from v1.0.45's 0.46, still below the 0.58 target).
        val reserve = LyricsBand.reserveWidthDp(869f)
        val h = SpotlightGeom.sixRowHeightDp(869f, 411f, reserve)
        assertEquals(217.6f, h, 0.5f)
        assertEquals(0.529f, h / 411f, 0.002f)
    }

    @Test
    fun sixRowOnNarrowScreenIsWidthCapped() {
        // 4:3, 1024×768 dp: reserve 206.56, width budget
        // 817.44 / 3.18 ≈ 257.1.
        val reserve = LyricsBand.reserveWidthDp(1024f)
        val h = SpotlightGeom.sixRowHeightDp(1024f, 768f, reserve)
        assertEquals(257.1f, h, 0.5f)
        assertEquals(0.335f, h / 768f, 0.002f)
        assertTrue(h < 0.58f * 768f)
    }

    @Test
    fun sixRowLeftCentresInTheAvailableWidth() {
        // Ultrawide 2560×1080: the 0.58 target binds (cap would
        // allow 648.3), span = 3.18 × 626.4 ≈ 1992.0, and the row
        // centres in the 2061.6 left of the reserve: left ≈ 34.8.
        val reserve = LyricsBand.reserveWidthDp(2560f)
        val h = SpotlightGeom.sixRowHeightDp(2560f, 1080f, reserve)
        assertEquals(0.58f * 1080f, h, 0.5f)
        val left = SpotlightGeom.sixRowLeftDp(2560f, h, reserve)
        assertEquals(34.8f, left, 0.5f)
        assertTrue(left > 0f)
    }

    @Test
    fun sixRowNeverReachesUnderThePanel() {
        val sizes = listOf(
            1920f to 1080f, 960f to 540f, 869f to 411f,
            1280f to 720f, 800f to 360f, 640f to 480f, 1024f to 768f,
            2560f to 1080f,
        )
        for ((w, hh) in sizes) {
            val reserve = LyricsBand.reserveWidthDp(w)
            val h = SpotlightGeom.sixRowHeightDp(w, hh, reserve)
            val span = h * SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT
            val left = SpotlightGeom.sixRowLeftDp(w, h, reserve)
            assertTrue("row too tall on $w x $hh", h <= 0.58f * hh + 0.01f)
            assertTrue("row wider than the available width on $w x $hh", span <= (w - reserve) + 0.5f)
            assertTrue("row starts off-screen on $w x $hh", left >= 0f)
            // THE panel rule: the row's right edge never passes
            // the reserve band's left edge.
            assertTrue(
                "row reaches under the panel on $w x $hh",
                left + span <= w - reserve + 0.5f,
            )
            assertTrue("row not positive on $w x $hh", h > 0f)
        }
    }

    // ---- v1.0.48: the robot stands a touch left in the row ---
    // Measured on the shipped still cutouts (alpha > 8 bbox):
    // Amy's visual left margin inside the row is 0.2358 × height,
    // the robot's visual right margin is 0.1975 × height — his
    // wider canvas (243/600 vs her 199/600) sits shallower in the
    // fixed 0.78 slot. The shift is the measured difference, so
    // after it the two margins agree.

    @Test
    fun sixRowRobotShiftIsTheMeasuredDifference() {
        assertEquals(0.038f, SpotlightGeom.SIX_ROW_ROBOT_LEFT_SHIFT_FRACTION, 0.0005f)
        val amyVisualLeft = 0.2358f
        val robotVisualRight = 0.1975f
        assertEquals(
            amyVisualLeft,
            robotVisualRight + SpotlightGeom.SIX_ROW_ROBOT_LEFT_SHIFT_FRACTION,
            0.001f,
        )
    }

    @Test
    fun sixRowRobotShiftScalesWithHeight() {
        assertEquals(7.6f, SpotlightGeom.sixRowRobotShiftDp(200f), 0.01f)
        assertEquals(0f, SpotlightGeom.sixRowRobotShiftDp(0f), 0.001f)
        assertTrue(SpotlightGeom.sixRowRobotShiftDp(300f) > SpotlightGeom.sixRowRobotShiftDp(200f))
        // A nudge, never a reshuffle: well under one pitch (0.60).
        assertTrue(
            SpotlightGeom.SIX_ROW_ROBOT_LEFT_SHIFT_FRACTION <
                SpotlightGeom.SIX_ROW_PITCH_FRACTION / 4f,
        )
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
