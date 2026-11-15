package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.state.DancerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1.0.23 handover ceremony timeline: segment boundaries,
 * total length, the exit cross-fade alphas, the fitted invite
 * gesture, the keeper resolution and the interruption rule — all
 * pure functions of elapsed time / queue state.
 */
class CeremonyTest {

    @Test
    fun totalIsTheSumOfTheSegments() {
        assertEquals(
            Ceremony.CLAP_MS + Ceremony.GROUP_INVITE_MS +
                Ceremony.EXIT_MS + Ceremony.SOLO_INVITE_MS,
            Ceremony.TOTAL_MS,
        )
        // The brief's ≈ 8.5 s: 3.8 + 2 + 0.6 + 2.
        assertEquals(8_400L, Ceremony.TOTAL_MS)
        assertEquals(3_800L, Ceremony.CLAP_MS)
        assertEquals(2_000L, Ceremony.GROUP_INVITE_MS)
        assertEquals(600L, Ceremony.EXIT_MS)
        assertEquals(2_000L, Ceremony.SOLO_INVITE_MS)
    }

    @Test
    fun phasesAtSegmentBoundaries() {
        assertEquals(Ceremony.Phase.CLAPPING, Ceremony.phaseAt(0L))
        assertEquals(Ceremony.Phase.CLAPPING, Ceremony.phaseAt(3_799L))
        assertEquals(Ceremony.Phase.GROUP_INVITE, Ceremony.phaseAt(3_800L))
        assertEquals(Ceremony.Phase.GROUP_INVITE, Ceremony.phaseAt(5_799L))
        assertEquals(Ceremony.Phase.EXITING, Ceremony.phaseAt(5_800L))
        assertEquals(Ceremony.Phase.EXITING, Ceremony.phaseAt(6_399L))
        assertEquals(Ceremony.Phase.SOLO_INVITE, Ceremony.phaseAt(6_400L))
        assertEquals(Ceremony.Phase.SOLO_INVITE, Ceremony.phaseAt(8_399L))
        assertEquals(Ceremony.Phase.DONE, Ceremony.phaseAt(8_400L))
        assertEquals(Ceremony.Phase.DONE, Ceremony.phaseAt(60_000L))
    }

    @Test
    fun negativeElapsedReadsAsTheOpeningClap() {
        assertEquals(Ceremony.Phase.CLAPPING, Ceremony.phaseAt(-500L))
    }

    @Test
    fun phaseProgressRunsWithinEachSegment() {
        assertEquals(0f, Ceremony.phaseProgress(0L), 0.001f)
        assertEquals(0.5f, Ceremony.phaseProgress(1_900L), 0.001f)
        assertEquals(0f, Ceremony.phaseProgress(3_800L), 0.001f)
        assertEquals(0.5f, Ceremony.phaseProgress(4_800L), 0.001f)
        assertEquals(0.5f, Ceremony.phaseProgress(6_100L), 0.001f)
        assertEquals(0.5f, Ceremony.phaseProgress(7_400L), 0.001f)
        assertEquals(1f, Ceremony.phaseProgress(8_400L), 0.001f)
    }

    @Test
    fun rowFadesOutAcrossTheExitOnly() {
        assertEquals(1f, Ceremony.leavingAlpha(0L), 0.001f)
        assertEquals(1f, Ceremony.leavingAlpha(5_799L), 0.001f)
        assertEquals(1f, Ceremony.leavingAlpha(5_800L), 0.001f)
        assertEquals(0.5f, Ceremony.leavingAlpha(6_100L), 0.001f)
        assertEquals(0f, Ceremony.leavingAlpha(6_400L), 0.001f)
        assertEquals(0f, Ceremony.leavingAlpha(8_400L), 0.001f)
    }

    @Test
    fun keeperFadesInAsTheMirrorOfTheRow() {
        assertEquals(0f, Ceremony.keeperSoloAlpha(0L), 0.001f)
        assertEquals(0f, Ceremony.keeperSoloAlpha(5_800L), 0.001f)
        assertEquals(0.5f, Ceremony.keeperSoloAlpha(6_100L), 0.001f)
        assertEquals(1f, Ceremony.keeperSoloAlpha(6_400L), 0.001f)
        assertEquals(1f, Ceremony.keeperSoloAlpha(8_399L), 0.001f)
        // The two alphas always add up to 1 during the exit: a
        // dissolve, never a moment with both or neither visible.
        for (t in 5_800L..6_400L step 37L) {
            assertEquals(
                1f,
                Ceremony.leavingAlpha(t) + Ceremony.keeperSoloAlpha(t),
                0.002f,
            )
        }
    }

    @Test
    fun ceremonyInviteIsOneFullBowInsideThePhase() {
        val mid = Ceremony.GROUP_INVITE_MS / 2
        val start = Ceremony.invitePose(DanceMotion.Profile.MODERN, 0L, Ceremony.GROUP_INVITE_MS)
        val peak = Ceremony.invitePose(DanceMotion.Profile.MODERN, mid, Ceremony.GROUP_INVITE_MS)
        val end = Ceremony.invitePose(DanceMotion.Profile.MODERN, Ceremony.GROUP_INVITE_MS, Ceremony.GROUP_INVITE_MS)
        // Lean starts and ends at zero — segments join without a snap.
        assertEquals(0f, start.rotationDeg, 0.001f)
        assertEquals(0f, end.rotationDeg, 0.001f)
        // …and reaches the full invite lean in the middle.
        assertEquals(DancerActivity.INVITE_LEAN_DEG, peak.rotationDeg, 0.001f)
        assertTrue(peak.sinkFraction > start.sinkFraction)
    }

    @Test
    fun ceremonyInviteKeepsTheRobotAtHalfAmplitude() {
        val mid = Ceremony.SOLO_INVITE_MS / 2
        val human = Ceremony.invitePose(DanceMotion.Profile.MODERN, mid, Ceremony.SOLO_INVITE_MS)
        val robot = Ceremony.invitePose(DanceMotion.Profile.ROBOT, mid, Ceremony.SOLO_INVITE_MS)
        assertEquals(human.rotationDeg / 2f, robot.rotationDeg, 0.001f)
    }

    @Test
    fun keeperResolutionFollowsTheStoredKey() {
        assertEquals(0, Ceremony.keeperIndex(DancerKeys.MODERN))
        assertEquals(2, Ceremony.keeperIndex(DancerKeys.SUMO))
        assertEquals(5, Ceremony.keeperIndex(DancerKeys.ROBOT))
        // The keeper index lines up with the key order the UI uses.
        DancerKeys.ALL.forEachIndexed { index, key ->
            assertEquals(index, Ceremony.keeperIndex(key))
        }
    }

    @Test
    fun keeperResolutionFallsBackToTheDefaultDancer() {
        assertEquals(0, Ceremony.keeperIndex(null))
        assertEquals(0, Ceremony.keeperIndex("girl")) // retired v1.0.11 key
        assertEquals(0, Ceremony.keeperIndex("nonsense"))
    }

    @Test
    fun boundaryRuleKeepsACeremonyWhoseLockHolds() {
        assertEquals(
            Ceremony.BoundaryAction.CONTINUE,
            Ceremony.boundaryAction(lockedStillCurrent = true, hasCurrent = true),
        )
        assertEquals(
            Ceremony.BoundaryAction.CONTINUE,
            Ceremony.boundaryAction(lockedStillCurrent = true, hasCurrent = false),
        )
    }

    @Test
    fun boundaryRuleRestartsOnTheNewHeadWhenTheLockIsGone() {
        assertEquals(
            Ceremony.BoundaryAction.START_CURRENT,
            Ceremony.boundaryAction(lockedStillCurrent = false, hasCurrent = true),
        )
    }

    @Test
    fun boundaryRuleEndsToIdleWhenTheQueueDrained() {
        assertEquals(
            Ceremony.BoundaryAction.END_TO_IDLE,
            Ceremony.boundaryAction(lockedStillCurrent = false, hasCurrent = false),
        )
    }
}
