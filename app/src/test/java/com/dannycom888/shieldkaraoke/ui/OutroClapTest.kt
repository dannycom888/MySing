package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outro applause trigger — an ordered song whose last sung
 * line ends more than 5 s before the song does gets the five
 * figures out clapping for the tail (v1.0.51). The 5 s are the
 * tail's first two stages: the 2 s frozen-pose hold
 * (DancerActivity.OUTRO_HOLD_MS, v1.0.22) plus 3 s of standing
 * breathing (OutroClap.BREATH_MS) — v1.0.53 moved the trigger
 * from +3 s to +5 s when the breathing stretch got its own
 * defined length. Pure position arithmetic: pausing freezes
 * the position, so the seconds are music time and can never
 * elapse while paused.
 */
class OutroClapTest {

    @Test
    fun `long outro triggers five seconds after the last line`() {
        // Last line ends at 180 s, song runs to 240 s.
        assertEquals(185_000L, OutroClap.triggerMs(180_000L, 240_000L))
    }

    @Test
    fun `exactly five seconds of outro never triggers`() {
        // +5 s lands exactly on the song's end: already over.
        assertNull(OutroClap.triggerMs(180_000L, 185_000L))
    }

    @Test
    fun `five seconds and a hair of outro triggers`() {
        assertEquals(185_000L, OutroClap.triggerMs(180_000L, 185_001L))
    }

    @Test
    fun `the old three second trigger point no longer fires`() {
        // v1.0.51 fired at +3 s; with the hold + breathing stages
        // counted separately, a 3–5 s tail ends before the row is
        // due — the end-of-song flow owns those.
        assertNull(OutroClap.triggerMs(180_000L, 183_000L))
        assertNull(OutroClap.triggerMs(180_000L, 184_999L))
        assertFalse(OutroClap.isActive(183_000L, 180_000L, 240_000L))
    }

    @Test
    fun `short or absent outro never triggers`() {
        assertNull(OutroClap.triggerMs(180_000L, 182_999L)) // < 5 s of tail
        assertNull(OutroClap.triggerMs(180_000L, 180_000L)) // line ends with the song
        assertNull(OutroClap.triggerMs(180_000L, 100_000L)) // lyric past the audio (bad LRC)
    }

    @Test
    fun `no lyrics or unknown duration never triggers`() {
        assertNull(OutroClap.triggerMs(null, 240_000L)) // no LRC
        assertNull(OutroClap.triggerMs(180_000L, 0L)) // duration not known yet
        assertNull(OutroClap.triggerMs(180_000L, -1L)) // player reports -1 while loading
    }

    @Test
    fun `active window is trigger to song end`() {
        val end = 180_000L
        val dur = 240_000L
        assertFalse(OutroClap.isActive(0L, end, dur))
        assertFalse(OutroClap.isActive(184_999L, end, dur)) // still inside the 5 s
        assertTrue(OutroClap.isActive(185_000L, end, dur)) // the moment itself
        assertTrue(OutroClap.isActive(239_999L, end, dur)) // last ms of the song
        assertFalse(OutroClap.isActive(240_000L, end, dur)) // song over: end flow owns it
        assertFalse(OutroClap.isActive(300_000L, end, dur))
    }

    @Test
    fun `the wait is position time, so a pause holds it`() {
        // Pausing parks positionMs; evaluating the same parked
        // position any number of times (the wall clock running
        // on) can never flip the answer — the 5 s only advance
        // while the song itself advances.
        val end = 180_000L
        val dur = 240_000L
        repeat(100) {
            assertFalse(OutroClap.isActive(181_000L, end, dur))
        }
        // And once the position does reach the trigger, it stays
        // active for the rest of the tail.
        repeat(100) {
            assertTrue(OutroClap.isActive(200_000L, end, dur))
        }
    }

    @Test
    fun `delay is the hold plus the breathing`() {
        assertEquals(3_000L, OutroClap.BREATH_MS)
        assertEquals(DancerActivity.OUTRO_HOLD_MS + OutroClap.BREATH_MS, OutroClap.DELAY_MS)
        assertEquals(5_000L, OutroClap.DELAY_MS)
    }

    @Test
    fun `the three tail stages run hold, breathing, then the row`() {
        // The v1.0.53 sequence, pinned against DancerActivity's
        // own phases: the dancer the row replaces is in the
        // frozen-pose hold for the first 2 s of the tail, in the
        // standing breathing phase for the next 3 s, and only at
        // +5 s does the row come out.
        val first = 10_000L
        val end = 180_000L
        val dur = 240_000L
        // Stage 1 — hold: still the frozen dance pose (OUTRO_HOLD).
        assertEquals(
            DancerActivity.Phase.OUTRO_HOLD,
            DancerActivity.phase(end + 1, first, end),
        )
        assertEquals(
            DancerActivity.Phase.OUTRO_HOLD,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS - 1, first, end),
        )
        assertFalse(OutroClap.isActive(end + DancerActivity.OUTRO_HOLD_MS - 1, end, dur))
        // Stage 2 — breathing: standing still (STILL) for 3 s,
        // the row not yet out — including at the old +3 s point.
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS, first, end),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + OutroClap.DELAY_MS - 1, first, end),
        )
        assertFalse(OutroClap.isActive(end + OutroClap.DELAY_MS - 1, end, dur))
        // Stage 3 — at +5 s the row comes out over the same stage.
        assertTrue(OutroClap.isActive(end + OutroClap.DELAY_MS, end, dur))
    }
}
