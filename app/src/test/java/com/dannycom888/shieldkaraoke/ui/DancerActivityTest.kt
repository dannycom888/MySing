package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import com.dannycom888.shieldkaraoke.lyrics.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DancerActivityTest {

    private fun plainLine(timeMs: Long) = LyricLine(timeMs = timeMs, text = "句")

    private fun enhancedLine(timeMs: Long, endMarkerMs: Long?) = LyricLine(
        timeMs = timeMs,
        text = "句子",
        words = buildList {
            add(WordTiming(timeMs, "句"))
            add(WordTiming(timeMs + 500, "子"))
            if (endMarkerMs != null) add(WordTiming(endMarkerMs, ""))
        },
    )

    @Test
    fun noLyricsMeansNoEnd() {
        assertNull(DancerActivity.lastLyricEndMs(emptyList()))
    }

    @Test
    fun noLyricsDancesTheWholeSong() {
        assertTrue(DancerActivity.isDancing(0L, null))
        assertTrue(DancerActivity.isDancing(3_600_000L, null))
    }

    @Test
    fun plainLastLineFallsBackToItsStart() {
        val lines = listOf(plainLine(10_000), plainLine(180_000))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun enhancedLastLineUsesItsEndMarker() {
        val lines = listOf(plainLine(10_000), enhancedLine(180_000, 184_500))
        assertEquals(184_500L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun enhancedLastLineWithoutMarkerFallsBackToStart() {
        val lines = listOf(enhancedLine(180_000, null))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun endMarkerBeforeLineStartIsIgnored() {
        val lines = listOf(enhancedLine(180_000, 179_000))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun dancingUntilTheEndThenStill() {
        val end = 184_500L
        assertTrue(DancerActivity.isDancing(end - 1, end))
        assertTrue(DancerActivity.isDancing(end, end))
        assertFalse(DancerActivity.isDancing(end + 1, end))
        assertFalse(DancerActivity.isDancing(end + 60_000, end))
    }

    @Test
    fun stillFrameIsTheNeutralFirstFrame() {
        assertEquals(0, DancerActivity.STILL_FRAME)
    }

    // --- breathing while standing still (v1.0.7) ---

    private val human = DanceMotion.Profile.MODERN

    @Test
    fun breathStartsNeutral() {
        val pose = DancerActivity.breathPose(0L, human)
        assertEquals(1f, pose.scale, 0.0001f)
        assertEquals(0f, pose.bounceFraction, 0.0001f)
        assertEquals(0f, pose.rotationDeg, 0.0001f)
        assertEquals(0f, pose.sinkFraction, 0.0001f)
    }

    @Test
    fun breathPeaksHalfwayThroughThePeriod() {
        val pose = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS / 2, human)
        assertEquals(1f + DancerActivity.BREATH_SCALE_AMP, pose.scale, 0.0001f)
        assertEquals(DancerActivity.BREATH_RISE_AMP, pose.bounceFraction, 0.0001f)
    }

    @Test
    fun breathReturnsToNeutralAfterAFullPeriod() {
        val start = DancerActivity.breathPose(0L, human)
        val after = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS, human)
        assertEquals(start.scale, after.scale, 0.0001f)
        assertEquals(start.bounceFraction, after.bounceFraction, 0.0001f)
    }

    @Test
    fun breathIsPeriodic() {
        for (t in listOf(1L, 400L, 1599L, 2000L, 3199L, 123_456L)) {
            val a = DancerActivity.breathPose(t, human)
            val b = DancerActivity.breathPose(t + DancerActivity.BREATH_PERIOD_MS, human)
            assertEquals(a.scale, b.scale, 0.0001f)
            assertEquals(a.bounceFraction, b.bounceFraction, 0.0001f)
        }
    }

    @Test
    fun breathRisesThenFallsSymmetrically() {
        val quarter = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS / 4, human)
        val peak = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS / 2, human)
        val threeQuarter = DancerActivity.breathPose(3 * DancerActivity.BREATH_PERIOD_MS / 4, human)
        assertTrue(quarter.scale < peak.scale)
        assertTrue(threeQuarter.scale < peak.scale)
        assertEquals(quarter.scale, threeQuarter.scale, 0.0001f)
        assertEquals(quarter.bounceFraction, threeQuarter.bounceFraction, 0.0001f)
    }

    @Test
    fun breathNeverRotatesOrSinks() {
        var t = 0L
        while (t <= DancerActivity.BREATH_PERIOD_MS) {
            val pose = DancerActivity.breathPose(t, human)
            assertEquals(0f, pose.rotationDeg, 0.0001f)
            assertEquals(0f, pose.sinkFraction, 0.0001f)
            t += 137L
        }
    }

    @Test
    fun breathStaysSubtleAcrossTheWholeCycle() {
        var t = 0L
        while (t <= DancerActivity.BREATH_PERIOD_MS) {
            val pose = DancerActivity.breathPose(t, human)
            assertTrue(pose.scale >= 1f && pose.scale <= 1f + DancerActivity.BREATH_SCALE_AMP + 0.0001f)
            assertTrue(pose.bounceFraction >= 0f && pose.bounceFraction <= DancerActivity.BREATH_RISE_AMP + 0.0001f)
            t += 100L
        }
    }

    @Test
    fun robotBreathesAtHalfAmplitude() {
        val half = DancerActivity.BREATH_PERIOD_MS / 2
        val robot = DancerActivity.breathPose(half, DanceMotion.Profile.ROBOT)
        assertEquals(1f + DancerActivity.BREATH_SCALE_AMP / 2, robot.scale, 0.0001f)
        assertEquals(DancerActivity.BREATH_RISE_AMP / 2, robot.bounceFraction, 0.0001f)
    }

    @Test
    fun breathPeriodIsAboutTwoSeconds() {
        assertEquals(2_000L, DancerActivity.BREATH_PERIOD_MS)
    }

    // --- three phases + the intro invite (v1.0.7) ---

    @Test
    fun noLyricsMeansNoStart() {
        assertNull(DancerActivity.firstLyricStartMs(emptyList()))
    }

    @Test
    fun firstLyricStartIsTheFirstLinesStart() {
        val lines = listOf(plainLine(12_500), plainLine(180_000))
        assertEquals(12_500L, DancerActivity.firstLyricStartMs(lines))
    }

    @Test
    fun noLyricsIsDancingThroughout() {
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(0L, null, null))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(3_600_000L, null, null))
    }

    @Test
    fun beforeTheFirstLineIsInviting() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, first, end))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(first - 1, first, end))
    }

    @Test
    fun firstLineStartBeginsTheDance() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(first, first, end))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(end, first, end))
    }

    @Test
    fun afterTheLastLineIsStill() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.STILL, DancerActivity.phase(end + 1, first, end))
    }

    @Test
    fun inviteStartsUpright() {
        val pose = DancerActivity.invitePose(0L, human)
        assertEquals(0f, pose.rotationDeg, 0.0001f)
        assertEquals(0f, pose.sinkFraction, 0.0001f)
        assertEquals(0f, pose.bounceFraction, 0.0001f)
        assertEquals(1f, pose.scale, 0.0001f)
    }

    @Test
    fun inviteBowsDeepestHalfwayThroughThePeriod() {
        val pose = DancerActivity.invitePose(DancerActivity.INVITE_PERIOD_MS / 2, human)
        assertEquals(DancerActivity.INVITE_LEAN_DEG, pose.rotationDeg, 0.0001f)
        assertEquals(DancerActivity.INVITE_SINK_AMP, pose.sinkFraction, 0.0001f)
    }

    @Test
    fun inviteReturnsUprightAfterAFullPeriod() {
        val start = DancerActivity.invitePose(0L, human)
        val after = DancerActivity.invitePose(DancerActivity.INVITE_PERIOD_MS, human)
        assertEquals(start.rotationDeg, after.rotationDeg, 0.0001f)
        assertEquals(start.sinkFraction, after.sinkFraction, 0.0001f)
    }

    @Test
    fun inviteIsPeriodic() {
        for (t in listOf(1L, 500L, 1999L, 2000L, 87_654L)) {
            val a = DancerActivity.invitePose(t, human)
            val b = DancerActivity.invitePose(t + DancerActivity.INVITE_PERIOD_MS, human)
            assertEquals(a.rotationDeg, b.rotationDeg, 0.0001f)
            assertEquals(a.sinkFraction, b.sinkFraction, 0.0001f)
        }
    }

    @Test
    fun inviteNeverBouncesOrSwells() {
        var t = 0L
        while (t <= DancerActivity.INVITE_PERIOD_MS) {
            val pose = DancerActivity.invitePose(t, human)
            assertEquals(0f, pose.bounceFraction, 0.0001f)
            assertEquals(1f, pose.scale, 0.0001f)
            assertTrue(pose.rotationDeg >= 0f && pose.rotationDeg <= DancerActivity.INVITE_LEAN_DEG + 0.0001f)
            assertTrue(pose.sinkFraction >= 0f && pose.sinkFraction <= DancerActivity.INVITE_SINK_AMP + 0.0001f)
            t += 100L
        }
    }

    @Test
    fun robotInvitesAtHalfAmplitude() {
        val half = DancerActivity.INVITE_PERIOD_MS / 2
        val robot = DancerActivity.invitePose(half, DanceMotion.Profile.ROBOT)
        assertEquals(DancerActivity.INVITE_LEAN_DEG / 2, robot.rotationDeg, 0.0001f)
        assertEquals(DancerActivity.INVITE_SINK_AMP / 2, robot.sinkFraction, 0.0001f)
    }

    @Test
    fun invitePeriodIsAboutTwoSeconds() {
        assertEquals(2_000L, DancerActivity.INVITE_PERIOD_MS)
    }
}
