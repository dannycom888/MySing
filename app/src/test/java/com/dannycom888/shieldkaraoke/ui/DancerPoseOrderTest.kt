package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.state.DancerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.17: the dance order (see [DancerPoseOrder]). The tables are
 * generated offline by dancers/resequence.py; these tests pin the
 * invariants the player relies on — every table is a true
 * permutation of the 40 frames (no frame shown twice per loop, no
 * frame dropped), every tour starts from the neutral standing
 * frame, and [PoseCycle]'s ordered overloads visit the frames in
 * exactly the table's order, one per beat.
 */
class DancerPoseOrderTest {

    @Test
    fun everyDancerOrderIsATruePermutationOfAllFortyFrames() {
        for (key in DancerKeys.ALL) {
            val order = DancerPoseOrder.orderFor(key)
            assertEquals("size for $key", 40, order.size)
            assertEquals("permutation for $key", (0 until 40).toList(), order.sorted())
        }
    }

    @Test
    fun everyTourStartsFromTheNeutralStandingFrame() {
        // Frame 0 is the standing pose (also the outro still frame);
        // the offline optimiser pinned it to the head of each tour
        // so dancing starts from standing, and the loop's wrap edge
        // lands back on it too.
        for (key in DancerKeys.ALL) {
            assertEquals("head for $key", 0, DancerPoseOrder.orderFor(key).first())
        }
    }

    @Test
    fun unknownKeyFallsBackToSheetOrder() {
        assertEquals(DancerPoseOrder.IDENTITY, DancerPoseOrder.orderFor(null))
        assertEquals(DancerPoseOrder.IDENTITY, DancerPoseOrder.orderFor("girl")) // retired v1.0.12 key
        assertEquals(DancerPoseOrder.IDENTITY, DancerPoseOrder.orderFor("nope"))
        assertEquals((0 until 40).toList(), DancerPoseOrder.IDENTITY)
    }

    @Test
    fun orderedFrameIndexVisitsTheTourOneFramePerBeat() {
        val order = DancerPoseOrder.orderFor(DancerKeys.MODERN)
        // 120 BPM: beat k starts at k*500 ms and shows order[k].
        val seen = (0 until 40).map { PoseCycle.frameIndex(it * 500L, 120.0, order) }
        assertEquals(order, seen)
        // Beat 40 wraps to the head of the tour.
        assertEquals(order[0], PoseCycle.frameIndex(40 * 500L, 120.0, order))
        assertEquals(order[1], PoseCycle.frameIndex(41 * 500L, 120.0, order))
    }

    @Test
    fun orderedFrameIndexIsTheSheetStepMappedThroughTheOrder() {
        val order = DancerPoseOrder.orderFor(DancerKeys.SUMO)
        var pos = -1_000L
        while (pos < 120_000L) {
            val step = PoseCycle.frameIndex(pos, 133.0, order.size)
            assertEquals("at $pos", order[step], PoseCycle.frameIndex(pos, 133.0, order))
            pos += 37
        }
    }

    @Test
    fun orderedBlendFadesBetweenDanceNeighbours() {
        val order = DancerPoseOrder.orderFor(DancerKeys.ANCIENT)
        // On a beat boundary the fade has not run: current is the
        // step's tour entry, previous is the tour entry before it.
        val b = PoseCycle.blend(1_500L, 120.0, order) // beat 3
        assertEquals(order[3], b.currentIndex)
        assertEquals(order[2], b.previousIndex)
        assertEquals(0f, b.currentAlpha, 0.0001f)
        // Mid-fade the alpha matches the sheet-order blend exactly —
        // only the frames changed, never the timing.
        val sheet = PoseCycle.blend(1_587L, 120.0, order.size)
        val mapped = PoseCycle.blend(1_587L, 120.0, order)
        assertEquals(sheet.currentAlpha, mapped.currentAlpha, 0.0001f)
        assertEquals(order[sheet.currentIndex], mapped.currentIndex)
        assertEquals(order[sheet.previousIndex], mapped.previousIndex)
        // Across the wrap the previous frame is the tour's last one.
        val wrap = PoseCycle.blend(40 * 500L, 120.0, order)
        assertEquals(order[0], wrap.currentIndex)
        assertEquals(order[39], wrap.previousIndex)
    }

    @Test
    fun identityOrderReproducesSheetOrderExactly() {
        var pos = -500L
        while (pos < 60_000L) {
            assertEquals(
                PoseCycle.frameIndex(pos, 133.0, 40),
                PoseCycle.frameIndex(pos, 133.0, DancerPoseOrder.IDENTITY),
            )
            assertEquals(
                PoseCycle.blend(pos, 133.0, 40),
                PoseCycle.blend(pos, 133.0, DancerPoseOrder.IDENTITY),
            )
            pos += 37
        }
    }

    @Test
    fun orderedOverloadsAreSafeOnEmptyOrder() {
        assertEquals(0, PoseCycle.frameIndex(5_000L, 120.0, emptyList()))
        assertEquals(
            PoseCycle.Blend(0, 0, 1f),
            PoseCycle.blend(5_000L, 120.0, emptyList()),
        )
    }

    @Test
    fun orderedCycleStillFreezesWithPosition() {
        val order = DancerPoseOrder.orderFor(DancerKeys.BABY)
        repeat(3) {
            assertEquals(
                PoseCycle.frameIndex(12_345L, 96.0, order),
                PoseCycle.frameIndex(12_345L, 96.0, order),
            )
            assertEquals(
                PoseCycle.blend(12_345L, 96.0, order),
                PoseCycle.blend(12_345L, 96.0, order),
            )
        }
        assertTrue(PoseCycle.frameIndex(12_345L, 96.0, order) in 0 until 40)
    }
}
