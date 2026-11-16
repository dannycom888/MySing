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

    // ---- the celebration row of five (v1.0.50) ---------------
    // v1.0.50: Amy holds Hannah, so the row of six becomes a row
    // of five figures on a pitch of 0.52 × height (re-derived
    // from the measured figure widths of the art the row draws,
    // bow frames included — see SpotlightGeom), spanning
    // 0.78 + 4 × 0.52 = 2.86 × height. Height targets 0.58 of
    // the screen height, capped so the span fits in the width
    // left of the queue panel's reserve band
    // (0.19 × screenW + 12 dp); the row is centred in that
    // available width, so its right edge can never slide under
    // the panel.

    @Test
    fun rowPitchAndSpanArePinned() {
        assertEquals(5, SpotlightGeom.ROW_COUNT)
        assertEquals(0.52f, SpotlightGeom.ROW_PITCH_FRACTION, 0.001f)
        assertEquals(2.86f, SpotlightGeom.ROW_SPAN_PER_HEIGHT, 0.001f)
        // Pitch is tighter than a frame width: the frames overlap.
        assertTrue(SpotlightGeom.ROW_PITCH_FRACTION < SpotlightGeom.FRAME_ASPECT)
    }

    @Test
    fun rowPitchLeavesOnlyTheWidestPairSlightlyOverlapping() {
        // Measured neighbouring-pair just-touch centre distances
        // (the worst simultaneous state binds: standing, invite,
        // bow and clap frames all measured, units of height):
        // combo–Xiuying, Xiuying–sumo, sumo–Nezha, Nezha–robot.
        // At the 0.52 pitch the widest pair's bounding boxes
        // overlap by 0.033 (a translucent sleeve tip over the
        // combo's edge — the slight wide-arm overlap the user
        // allows), the second pair just kisses, and the rest
        // keep clear air.
        val pairTouch = listOf(0.553f, 0.521f, 0.382f, 0.333f)
        val gaps = pairTouch.map { SpotlightGeom.ROW_PITCH_FRACTION - it }
        assertEquals(listOf(-0.033f, -0.001f, 0.138f, 0.187f).size, gaps.size)
        assertTrue(gaps[0] in -0.04f..-0.02f)
        assertTrue(gaps[1] in -0.005f..0.005f)
        assertTrue(gaps[2] >= 0.10f)
        assertTrue(gaps[3] >= 0.10f)
    }

    @Test
    fun rowOn16x9TvIsPanelCapped() {
        // Reserve = 0.19 × 1920 + 12 = 376.8, leaving 1543.2;
        // 1543.2 / 2.86 ≈ 539.6 — the panel cap binds below the
        // 0.58 target (626.4), at ≈0.500 of the screen height
        // (up from the six-figure row's 0.449: one slot fewer
        // buys real size).
        val reserve = LyricsBand.reserveWidthDp(1920f)
        assertEquals(376.8f, reserve, 0.01f)
        val h = SpotlightGeom.rowHeightDp(1920f, 1080f, reserve)
        assertEquals(539.6f, h, 0.5f)
        assertEquals(0.500f, h / 1080f, 0.002f)
        assertTrue(h < 0.58f * 1080f)
        // Width-bound: the row starts at the left edge and its
        // right edge lands exactly on the reserve band's left edge.
        assertEquals(0f, SpotlightGeom.rowLeftDp(1920f, h, reserve), 0.5f)
        // Same screen at half the density (960×540 dp): reserve
        // 194.4, cap 765.6 / 2.86 ≈ 267.7 ≈ 0.496 of the height.
        val reserve2 = LyricsBand.reserveWidthDp(960f)
        val h2 = SpotlightGeom.rowHeightDp(960f, 540f, reserve2)
        assertEquals(267.7f, h2, 0.5f)
        assertEquals(0.496f, h2 / 540f, 0.002f)
    }

    @Test
    fun rowOnWidePhoneReachesTheTarget() {
        // The user's phone in landscape, ≈869×411 dp: reserve
        // 177.11, the width cap would allow 691.89 / 2.86 ≈ 241.9,
        // so the 0.58 target itself binds at 238.4 (the
        // six-figure row was capped at 0.529 here).
        val reserve = LyricsBand.reserveWidthDp(869f)
        val h = SpotlightGeom.rowHeightDp(869f, 411f, reserve)
        assertEquals(238.4f, h, 0.5f)
        assertEquals(0.580f, h / 411f, 0.002f)
    }

    @Test
    fun rowOnNarrowScreenIsWidthCapped() {
        // 4:3, 1024×768 dp: reserve 206.56, width budget
        // 817.44 / 2.86 ≈ 285.8.
        val reserve = LyricsBand.reserveWidthDp(1024f)
        val h = SpotlightGeom.rowHeightDp(1024f, 768f, reserve)
        assertEquals(285.8f, h, 0.5f)
        assertEquals(0.372f, h / 768f, 0.002f)
        assertTrue(h < 0.58f * 768f)
    }

    @Test
    fun rowLeftCentresInTheAvailableWidth() {
        // Ultrawide 2560×1080: the 0.58 target binds (cap would
        // allow 720.8), span = 2.86 × 626.4 ≈ 1791.5, and the
        // row centres in the 2061.6 left of the reserve:
        // left ≈ 135.1.
        val reserve = LyricsBand.reserveWidthDp(2560f)
        val h = SpotlightGeom.rowHeightDp(2560f, 1080f, reserve)
        assertEquals(0.58f * 1080f, h, 0.5f)
        val left = SpotlightGeom.rowLeftDp(2560f, h, reserve)
        assertEquals(135.1f, left, 0.5f)
        assertTrue(left > 0f)
    }

    @Test
    fun rowNeverReachesUnderThePanel() {
        val sizes = listOf(
            1920f to 1080f, 960f to 540f, 869f to 411f,
            1280f to 720f, 800f to 360f, 640f to 480f, 1024f to 768f,
            2560f to 1080f,
        )
        for ((w, hh) in sizes) {
            val reserve = LyricsBand.reserveWidthDp(w)
            val h = SpotlightGeom.rowHeightDp(w, hh, reserve)
            val span = h * SpotlightGeom.ROW_SPAN_PER_HEIGHT
            val left = SpotlightGeom.rowLeftDp(w, h, reserve)
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

    // ---- v1.0.50: the robot's personal shift is retired -------
    // With Amy solo in the first slot the ends were asymmetric
    // (her narrow canvas sat deeper in the fixed slot) and
    // v1.0.48 shifted the robot left by the measured 0.038. The
    // five-figure row's first slot is the Amy + Hannah combo on
    // a full 468-wide canvas: measured on the standing frames
    // (alpha > 8 bbox), the combo's visual left margin inside
    // the row is 0.39 − 0.190 = 0.2000 × height and the robot's
    // visual right margin is 2.86 − (4 × 0.52 + 0.39 + 0.193)
    // = 0.1975 × height — a 0.0025 difference, about a dp at
    // real row heights. The composition is symmetric on its
    // own, so the shift is 0 (mechanism kept).

    @Test
    fun rowEndMarginsAreSymmetricWithoutAnyShift() {
        assertEquals(0f, SpotlightGeom.ROW_ROBOT_LEFT_SHIFT_FRACTION, 0.0005f)
        assertEquals(0f, SpotlightGeom.rowRobotShiftDp(200f), 0.001f)
        assertEquals(0f, SpotlightGeom.rowRobotShiftDp(540f), 0.001f)
        val comboVisualLeft = 0.39f - 0.190f
        val robotVisualRight = SpotlightGeom.ROW_SPAN_PER_HEIGHT -
            (4f * SpotlightGeom.ROW_PITCH_FRACTION + 0.39f + 0.193f)
        assertEquals(0.200f, comboVisualLeft, 0.002f)
        assertEquals(0.1975f, robotVisualRight, 0.002f)
        assertTrue(kotlin.math.abs(comboVisualLeft - robotVisualRight) <= 0.003f)
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
