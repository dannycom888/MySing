package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseCycleTest {

    @Test
    fun startsOnFirstFrame() {
        assertEquals(0, PoseCycle.frameIndex(0L, 120.0, 10))
        assertEquals(0, PoseCycle.frameIndex(100L, 120.0, 10))
    }

    @Test
    fun advancesOneFramePerBeat() {
        // 120 BPM: a beat is 500 ms. Half a beat in, the frame has
        // NOT advanced yet — one frame per beat since v1.0.6.
        assertEquals(0, PoseCycle.frameIndex(250L, 120.0, 10))
        assertEquals(0, PoseCycle.frameIndex(499L, 120.0, 10))
        assertEquals(1, PoseCycle.frameIndex(500L, 120.0, 10))
        assertEquals(2, PoseCycle.frameIndex(1_000L, 120.0, 10))
        assertEquals(3, PoseCycle.frameIndex(1_500L, 120.0, 10))
        // 60 BPM: a beat is 1000 ms.
        assertEquals(0, PoseCycle.frameIndex(500L, 60.0, 10))
        assertEquals(1, PoseCycle.frameIndex(1_000L, 60.0, 10))
        assertEquals(2, PoseCycle.frameIndex(2_000L, 60.0, 10))
    }

    @Test
    fun cyclesInOrderAndWraps() {
        val seen = (0 until 12).map { PoseCycle.frameIndex(it * 500L, 120.0, 10) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1), seen)
    }

    @Test
    fun cyclesThroughAllFortyFrames() {
        // v1.0.7: every dancer carries 40 frames; at 120 BPM one
        // beat (500 ms) advances exactly one frame, wrapping after 40.
        val seen = (0 until 42).map { PoseCycle.frameIndex(it * 500L, 120.0, 40) }
        assertEquals((0 until 40).toList() + listOf(0, 1), seen)
    }

    @Test
    fun pauseFreezesFrame() {
        // Pure function of position: same position, same frame, always.
        repeat(3) {
            assertEquals(
                PoseCycle.frameIndex(12_345L, 96.0, 10),
                PoseCycle.frameIndex(12_345L, 96.0, 10),
            )
        }
    }

    @Test
    fun frameAlwaysInRange() {
        var pos = -1_000L
        while (pos < 120_000L) {
            val f = PoseCycle.frameIndex(pos, 133.0, 10)
            assertTrue("frame $f at $pos", f in 0 until 10)
            pos += 37
        }
    }

    @Test
    fun degenerateFrameCountsAreSafe() {
        assertEquals(0, PoseCycle.frameIndex(5_000L, 120.0, 0))
        assertEquals(0, PoseCycle.frameIndex(5_000L, 120.0, 1))
        assertEquals(0, PoseCycle.frameIndex(5_000L, 120.0, -3))
    }

    @Test
    fun phaseOffsetShiftsTheCycle() {
        // A frame step is a whole beat: quarter- and half-beat
        // offsets are not enough to change the frame; a full-beat
        // offset moves exactly one frame.
        assertEquals(
            PoseCycle.frameIndex(0L, 120.0, 10),
            PoseCycle.frameIndex(0L, 120.0, 10, phaseOffsetBeats = 0.25),
        )
        assertEquals(
            PoseCycle.frameIndex(0L, 120.0, 10),
            PoseCycle.frameIndex(0L, 120.0, 10, phaseOffsetBeats = 0.5),
        )
        assertEquals(
            PoseCycle.frameIndex(500L, 120.0, 10),
            PoseCycle.frameIndex(0L, 120.0, 10, phaseOffsetBeats = 1.0),
        )
    }
}
