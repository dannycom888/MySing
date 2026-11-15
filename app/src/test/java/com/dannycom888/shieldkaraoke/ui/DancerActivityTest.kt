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
    fun shortIntroDancesFromTheStart() {
        // v1.0.22: "如果没前奏就直接跳" — a first line at 2.4 s
        // leaves no room for a bow, so the song dances from 0.
        val first = 2_400L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(0L, first, end))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(first - 1, first, end))
    }

    @Test
    fun introJustPastTheThresholdStillInvites() {
        // A first line at 2.6 s is past the threshold: invite
        // until the line starts, then dance.
        val first = 2_600L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, first, end))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(first - 1, first, end))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(first, first, end))
    }

    @Test
    fun hasIntroThreshold() {
        assertEquals(2_500L, DancerActivity.MIN_INTRO_MS)
        assertFalse(DancerActivity.hasIntro(null))
        assertFalse(DancerActivity.hasIntro(0L))
        assertFalse(DancerActivity.hasIntro(2_499L))
        assertTrue(DancerActivity.hasIntro(2_500L))
        assertTrue(DancerActivity.hasIntro(12_500L))
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
        // v1.0.22: the standing frame only takes over once the
        // 2 s outro hold has run out.
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS, first, end),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS + 60_000L, first, end),
        )
    }

    // ---- v1.0.22: the outro hold — last pose held 2 s, then a
    // short settle fade into the standing breathing phase.

    @Test
    fun outroHoldStartsTheMomentTheSingingEnds() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(end, first, end))
        assertEquals(DancerActivity.Phase.OUTRO_HOLD, DancerActivity.phase(end + 1, first, end))
        assertEquals(
            DancerActivity.Phase.OUTRO_HOLD,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS - 1, first, end),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS, first, end),
        )
    }

    @Test
    fun outroHoldPoseSettlesToNeutral() {
        val end = 184_500L
        val frozen = DanceMotion.Pose(
            bounceFraction = 0.8f, rotationDeg = 6f, scale = 1.05f, sinkFraction = 0.2f,
        )
        // At the boundary the frozen pose is untouched…
        assertEquals(frozen, DancerActivity.outroHoldPose(frozen, end, end))
        // …half-way it has relaxed part of the way (smoothstep:
        // exactly half at the midpoint)…
        val mid = DancerActivity.outroHoldPose(frozen, end + DancerActivity.OUTRO_HOLD_MS / 2, end)
        assertEquals(0.4f, mid.bounceFraction, 0.001f)
        assertEquals(3f, mid.rotationDeg, 0.001f)
        assertEquals(1.025f, mid.scale, 0.001f)
        assertEquals(0.1f, mid.sinkFraction, 0.001f)
        // …and at the hold's end it is fully neutral.
        val done = DancerActivity.outroHoldPose(frozen, end + DancerActivity.OUTRO_HOLD_MS, end)
        assertEquals(DanceMotion.Pose(0f, 0f, 1f, 0f), done)
    }

    @Test
    fun outroSettleFadesOnlyInItsWindow() {
        val end = 184_500L
        val settleStart = end + DancerActivity.OUTRO_HOLD_MS
        assertNull(DancerActivity.outroSettleAlpha(settleStart - 1, end))
        assertEquals(0f, DancerActivity.outroSettleAlpha(settleStart, end))
        assertEquals(
            0.5f,
            DancerActivity.outroSettleAlpha(settleStart + DancerActivity.OUTRO_SETTLE_MS / 2, end)!!,
            0.001f,
        )
        assertNull(DancerActivity.outroSettleAlpha(settleStart + DancerActivity.OUTRO_SETTLE_MS, end))
        assertNull(DancerActivity.outroSettleAlpha(settleStart, null))
    }

    @Test
    fun breathingPhaseContinuesAcrossTheSettle() {
        // breathPose is an absolute-position function, so the
        // breath the dancer breathes right after the settle is
        // the same one it would have breathed had the standing
        // frame been up all along — no phase jump at the seam.
        val end = 184_500L
        val seam = end + DancerActivity.OUTRO_HOLD_MS + DancerActivity.OUTRO_SETTLE_MS
        val atSeam = DancerActivity.breathPose(seam, DanceMotion.Profile.MODERN)
        val onePeriodLater = DancerActivity.breathPose(
            seam + DancerActivity.BREATH_PERIOD_MS, DanceMotion.Profile.MODERN,
        )
        assertEquals(atSeam, onePeriodLater)
    }

    @Test
    fun shortOutroEndsInsideTheHold() {
        // Outro of only 0.5 s: the song is over before the hold
        // could finish — positions inside the tail still read
        // HOLD, positions at/past the duration read STILL, and
        // nothing forces the full hold to play out.
        val first = 12_500L
        val end = 184_500L
        val duration = end + 500L
        assertEquals(
            DancerActivity.Phase.OUTRO_HOLD,
            DancerActivity.phase(end + 250, first, end, duration),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(duration, first, end, duration),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(duration + 5_000, first, end, duration),
        )
    }

    @Test
    fun noLyricSongNeverEntersTheOutroHold() {
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(999_999L, null, null))
        assertNull(DancerActivity.outroSettleAlpha(999_999L, null))
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
    fun invitePeriodIsAboutFourSeconds() {
        // v1.0.22: the user asked for the intro invite bow to run
        // "慢些再慢些" — one full lean cycle now takes 4 s, twice
        // the v1.0.7 period; amplitude and shape are unchanged.
        assertEquals(4_000L, DancerActivity.INVITE_PERIOD_MS)
    }
}
