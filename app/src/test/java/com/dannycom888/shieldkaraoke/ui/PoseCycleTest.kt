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

    // --- v1.0.10: cross-fade between frames (PoseCycle.blend) ---

    @Test
    fun blendStartsFullyOnPreviousFrame() {
        // 120 BPM: a beat is 500 ms. Exactly on a beat boundary the
        // fade has not run at all — the previous frame shows whole.
        val b = PoseCycle.blend(500L, 120.0, 10)
        assertEquals(1, b.currentIndex)
        assertEquals(0, b.previousIndex)
        assertEquals(0f, b.currentAlpha, 0.0001f)
        // At the very start, the "previous" of frame 0 wraps around
        // to the last frame of the cycle.
        val start = PoseCycle.blend(0L, 120.0, 10)
        assertEquals(0, start.currentIndex)
        assertEquals(9, start.previousIndex)
        assertEquals(0f, start.currentAlpha, 0.0001f)
    }

    @Test
    fun blendIsLinearThroughTheFadeWindow() {
        // Half-way through the 35% window (phase ≈0.175 of the
        // beat, ≈87 ms into a 500 ms beat): exactly half blended.
        val b = PoseCycle.blend(587L, 120.0, 10)
        assertEquals(1, b.currentIndex)
        assertEquals(0, b.previousIndex)
        assertEquals(0.5f, b.currentAlpha, 0.01f)
    }

    @Test
    fun blendCompletesAtThirtyFivePercentAndStays() {
        // Phase 0.35 = 175 ms into the beat; from there until the
        // next beat the current frame stands alone.
        assertEquals(1f, PoseCycle.blend(675L, 120.0, 10).currentAlpha, 0.001f)
        assertEquals(1f, PoseCycle.blend(750L, 120.0, 10).currentAlpha, 0.0001f)
        assertEquals(1f, PoseCycle.blend(999L, 120.0, 10).currentAlpha, 0.0001f)
        // Just before the window closes the fade is nearly done.
        assertTrue(PoseCycle.blend(670L, 120.0, 10).currentAlpha > 0.95f)
    }

    @Test
    fun blendCurrentFrameAlwaysMatchesFrameIndex() {
        var pos = -1_000L
        while (pos < 120_000L) {
            val b = PoseCycle.blend(pos, 133.0, 40)
            assertEquals("at $pos", PoseCycle.frameIndex(pos, 133.0, 40), b.currentIndex)
            assertTrue(b.currentIndex in 0 until 40)
            assertTrue(b.previousIndex in 0 until 40)
            assertTrue(b.currentAlpha in 0f..1f)
            pos += 37
        }
    }

    @Test
    fun blendPreviousIsOneStepBehindAcrossTheWrap() {
        // 10 frames at 120 BPM: beat 10 (5000 ms) lands back on
        // frame 0, and its previous frame is 9 — never negative.
        val b = PoseCycle.blend(5_000L, 120.0, 10)
        assertEquals(0, b.currentIndex)
        assertEquals(9, b.previousIndex)
    }

    @Test
    fun blendDegenerateFrameCountsAreSafe() {
        assertEquals(PoseCycle.Blend(0, 0, 1f), PoseCycle.blend(5_000L, 120.0, 0))
        assertEquals(PoseCycle.Blend(0, 0, 1f), PoseCycle.blend(5_000L, 120.0, 1))
        assertEquals(PoseCycle.Blend(0, 0, 1f), PoseCycle.blend(5_000L, 120.0, -3))
    }

    @Test
    fun blendHonoursPhaseOffset() {
        // A full-beat offset shifts the whole blend by one beat,
        // same as it shifts the frame cycle.
        assertEquals(
            PoseCycle.blend(500L, 120.0, 10),
            PoseCycle.blend(0L, 120.0, 10, phaseOffsetBeats = 1.0),
        )
    }

    @Test
    fun blendFreezesWithPosition() {
        // Pure function of position: a paused player hands in the
        // same position forever, so the fade cannot creep on while
        // paused — it resumes exactly where it froze.
        repeat(3) {
            assertEquals(
                PoseCycle.blend(12_345L, 96.0, 40),
                PoseCycle.blend(12_345L, 96.0, 40),
            )
        }
    }

    // --- v1.0.36: the fade window widens with tempo ---

    @Test
    fun fadeFractionPins() {
        // The normal-speed window is the v1.0.10 value, flat
        // through 120 BPM.
        assertEquals(0.35, PoseCycle.fadeFraction(96.0), 1e-9)
        assertEquals(0.35, PoseCycle.fadeFraction(120.0), 1e-9)
        // Smoothstep up to 0.50 at 150 BPM; 135 is the midpoint.
        assertEquals(0.425, PoseCycle.fadeFraction(135.0), 1e-9)
        assertEquals(0.50, PoseCycle.fadeFraction(150.0), 1e-9)
        assertEquals(0.50, PoseCycle.fadeFraction(200.0), 1e-9)
    }

    @Test
    fun blendAtFastTempoMeltsLonger() {
        // 150 BPM: a beat is 400 ms and the fade window is half
        // the beat. A quarter-beat in, the blend is only half
        // done (at 120 BPM it would already be ~71% done).
        val b = PoseCycle.blend(500L, 150.0, 10)
        assertEquals(1, b.currentIndex)
        assertEquals(0.5f, b.currentAlpha, 0.01f)
        // Half a beat in, the fade completes exactly.
        assertEquals(1f, PoseCycle.blend(600L, 150.0, 10).currentAlpha, 0.001f)
        // The same phase at 120 BPM still uses the 0.35 window:
        // phase 0.25 is ~71% blended there.
        assertEquals(0.25f / 0.35f, PoseCycle.blend(625L, 120.0, 10).currentAlpha, 0.01f)
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
