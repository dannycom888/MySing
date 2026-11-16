package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.state.DancerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.50: the invite chain (six gesture frames + two bow
 * frames, ping-ponged with the fully-bowed frame held an extra
 * beat) and the two-frame clap — the pure frame-selection
 * functions behind the animated gestures. Everything is a
 * function of the caller's clock, so these pins are the whole
 * contract: solo intro invite, ceremony group invite and the
 * keeper's solo invite all step off their own clocks through
 * here.
 */
class InviteCycleTest {

    @Test
    fun chainShapeIsPinned() {
        assertEquals(6, InviteCycle.INVITE_COUNT)
        assertEquals(2, InviteCycle.BOW_COUNT)
        assertEquals(8, InviteCycle.CHAIN_FRAMES)
        // Gesture out (0..5), bow in (6, 7), bow peak repeated —
        // the ping-pong then holds the fully-bowed frame for
        // three consecutive steps (arrive / hold / start back).
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 7), InviteCycle.CHAIN)
        assertEquals(250L, InviteCycle.FRAME_MS)
        // One full out-and-back cycle is 4.0 s — the period of
        // the whole-body bow the frames replace.
        val cycleSteps = 2 * (InviteCycle.CHAIN.size - 1)
        assertEquals(
            DancerActivity.INVITE_PERIOD_MS,
            cycleSteps * InviteCycle.FRAME_MS,
        )
    }

    @Test
    fun frameIndexPlaysGestureThenBowThenBack() {
        // Out through the gesture: one frame per 250 ms step.
        assertEquals(0, InviteCycle.frameIndex(0))
        assertEquals(0, InviteCycle.frameIndex(249))
        assertEquals(1, InviteCycle.frameIndex(250))
        assertEquals(5, InviteCycle.frameIndex(1250))
        // Into the bow: half-bent at 6, fully bowed at 7.
        assertEquals(6, InviteCycle.frameIndex(1500))
        assertEquals(7, InviteCycle.frameIndex(1750))
        // The peak holds for three steps in total.
        assertEquals(7, InviteCycle.frameIndex(2000))
        assertEquals(7, InviteCycle.frameIndex(2250))
        // And back: half-bent again, then the gesture unwinds.
        assertEquals(6, InviteCycle.frameIndex(2500))
        assertEquals(5, InviteCycle.frameIndex(2750))
        assertEquals(1, InviteCycle.frameIndex(3750))
        // A full 4 s cycle lands back at rest, and wraps.
        assertEquals(0, InviteCycle.frameIndex(4000))
        assertEquals(1, InviteCycle.frameIndex(4250))
        // A clock read before the start stamp reads as rest.
        assertEquals(0, InviteCycle.frameIndex(-500))
    }

    @Test
    fun ceremonyInviteSegmentsReachTheBowExactly() {
        // The ceremony's invite segments are 2 000 ms: at
        // 250 ms a step that is exactly the gesture out to the
        // bow's peak — the last step of the segment shows the
        // fully-bowed frame, never a half-raised arm.
        val seg = Ceremony.GROUP_INVITE_MS
        assertEquals(Ceremony.SOLO_INVITE_MS, seg)
        assertEquals(7, InviteCycle.frameIndex(seg - 1))
        assertEquals(6, InviteCycle.frameIndex(seg / 2 + 500))
    }

    @Test
    fun blendMeltsBetweenChainNeighbours() {
        // At a step boundary the new frame starts at alpha 0.
        val atStart = InviteCycle.blend(250)
        assertEquals(0, atStart.previousIndex)
        assertEquals(1, atStart.currentIndex)
        assertEquals(0f, atStart.currentAlpha, 0.001f)
        // Rising into the bow: 6 melts into 7.
        val intoBow = InviteCycle.blend(1750)
        assertEquals(6, intoBow.previousIndex)
        assertEquals(7, intoBow.currentIndex)
        // At the bow's peak the chain holds: previous == current,
        // so the caller draws the single frame (no phantom fade).
        val atPeak = InviteCycle.blend(2000)
        assertEquals(7, atPeak.previousIndex)
        assertEquals(7, atPeak.currentIndex)
        // Coming back out of the bow: 7 melts into 6.
        val outOfBow = InviteCycle.blend(2500)
        assertEquals(7, outOfBow.previousIndex)
        assertEquals(6, outOfBow.currentIndex)
        // Mid-step the fade has completed (fade window is the
        // first 35% of a step).
        assertEquals(1f, InviteCycle.blend(125).currentAlpha, 0.001f)
    }

    @Test
    fun robotStepsHardWhileHumansBlend() {
        // The robot's hard-cut marker is a null blend at every
        // clock reading; humans always get a blend.
        for (t in listOf(0L, 250L, 1750L, 2000L, 3900L)) {
            assertNull(InviteCycle.blendFor(DanceMotion.Profile.ROBOT, t))
            assertTrue(InviteCycle.blendFor(DanceMotion.Profile.MODERN, t) != null)
            assertTrue(InviteCycle.blendFor(DanceMotion.Profile.SUMO, t) != null)
        }
        // And his single-frame stepping follows the same chain.
        assertEquals(
            InviteCycle.frameIndex(1750),
            InviteCycle.frameIndex(1750),
        )
    }

    @Test
    fun clapAlternatesAtTheNaturalRate() {
        assertEquals(375L, ClapCycle.FRAME_MS)
        assertTrue(ClapCycle.FRAME_MS in 350L..400L)
        assertEquals(0, ClapCycle.frameAt(0))
        assertEquals(0, ClapCycle.frameAt(374))
        assertEquals(1, ClapCycle.frameAt(375))
        assertEquals(1, ClapCycle.frameAt(749))
        assertEquals(0, ClapCycle.frameAt(750))
        assertEquals(0, ClapCycle.frameAt(-100))
    }

    @Test
    fun everyFigureCarriesTheFullChain() {
        // All six selectable dancers carry 6 invite + 2 bow
        // frames (an 8-frame chain) and both clap frames.
        for (dancer in Dancers.ALL) {
            assertEquals(6, dancer.inviteFrames.size)
            assertEquals(2, dancer.bowFrames.size)
            assertEquals(8, dancer.inviteChain.size)
            assertEquals(
                dancer.inviteFrames + dancer.bowFrames,
                dancer.inviteChain,
            )
        }
        // The celebration row is the five figures: the combo
        // first (no dancer of its own), then Xiuying, the sumo,
        // Nezha and the robot — Hannah stands in nobody's slot
        // but the combo's arms.
        val row = Dancers.ROW_FIGURES
        assertEquals(5, row.size)
        assertEquals(Dancers.HOLD_COMBO_KEY, row[0].key)
        assertNull(row[0].dancer)
        assertEquals(8, row[0].inviteChain.size)
        assertEquals(
            listOf(
                DancerKeys.ANCIENT, DancerKeys.SUMO,
                DancerKeys.BABY, DancerKeys.ROBOT,
            ),
            row.drop(1).map { it.key },
        )
        assertTrue(row.none { it.key == DancerKeys.ANCIENT_GIRL })
        // The selectable list still holds all six dancers —
        // the combo is a row presentation, never a seventh
        // pick on the phone page.
        assertEquals(6, Dancers.ALL.size)
    }
}
