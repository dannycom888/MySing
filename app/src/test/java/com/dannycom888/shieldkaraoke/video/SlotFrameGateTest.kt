package com.dannycom888.shieldkaraoke.video

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.0.78: the first-frame gate's pure decision — a video
 * texture slot stays invisible from each media load until
 * the new item's first frame has rendered (before that the
 * texture samples black, and black does not key — the
 * cold-open / song-start black box), then fades in over
 * [SlotFrameGate.APPEAR_FADE_MS].
 */
class SlotFrameGateTest {

    @Test
    fun `no frame yet means invisible at any time`() {
        assertEquals(0f, SlotFrameGate.alphaFactor(0L, 0L), 0f)
        assertEquals(0f, SlotFrameGate.alphaFactor(0L, 60_000L), 0f)
        // A non-positive stamp is "never", however it arose.
        assertEquals(0f, SlotFrameGate.alphaFactor(-5L, 60_000L), 0f)
    }

    @Test
    fun `the fade starts at zero on the first frame`() {
        val t0 = 10_000L
        assertEquals(0f, SlotFrameGate.alphaFactor(t0, t0), 0f)
        // A clock reading before the stamp (skew between
        // the callback post and the GL thread) stays shut.
        assertEquals(0f, SlotFrameGate.alphaFactor(t0, t0 - 1L), 0f)
    }

    @Test
    fun `the fade ramps linearly and completes`() {
        val t0 = 10_000L
        val fade = SlotFrameGate.APPEAR_FADE_MS
        assertEquals(0.5f, SlotFrameGate.alphaFactor(t0, t0 + fade / 2), 0.001f)
        assertEquals(1f, SlotFrameGate.alphaFactor(t0, t0 + fade), 0f)
        assertEquals(1f, SlotFrameGate.alphaFactor(t0, t0 + fade * 40), 0f)
    }

    @Test
    fun `the fade is short`() {
        // A softening of the figure's appearance, not an
        // effect: a quarter second, well under the dance
        // crossfade's own timescale.
        assertEquals(250L, SlotFrameGate.APPEAR_FADE_MS)
    }
}
