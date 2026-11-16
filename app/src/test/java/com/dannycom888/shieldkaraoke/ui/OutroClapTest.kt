package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.51: the outro applause trigger — an ordered song whose
 * last sung line ends more than 3 s before the song does gets
 * the five figures out clapping for the tail. Pure position
 * arithmetic: pausing freezes the position, so the 3 s are
 * music time and can never elapse while paused.
 */
class OutroClapTest {

    @Test
    fun `long outro triggers three seconds after the last line`() {
        // Last line ends at 180 s, song runs to 240 s.
        assertEquals(183_000L, OutroClap.triggerMs(180_000L, 240_000L))
    }

    @Test
    fun `exactly three seconds of outro never triggers`() {
        // +3 s lands exactly on the song's end: already over.
        assertNull(OutroClap.triggerMs(180_000L, 183_000L))
    }

    @Test
    fun `three seconds and a hair of outro triggers`() {
        assertEquals(183_000L, OutroClap.triggerMs(180_000L, 183_001L))
    }

    @Test
    fun `short or absent outro never triggers`() {
        assertNull(OutroClap.triggerMs(180_000L, 182_999L)) // < 3 s of tail
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
        assertFalse(OutroClap.isActive(182_999L, end, dur)) // still inside the 3 s
        assertTrue(OutroClap.isActive(183_000L, end, dur)) // the moment itself
        assertTrue(OutroClap.isActive(239_999L, end, dur)) // last ms of the song
        assertFalse(OutroClap.isActive(240_000L, end, dur)) // song over: end flow owns it
        assertFalse(OutroClap.isActive(300_000L, end, dur))
    }

    @Test
    fun `the wait is position time, so a pause holds it`() {
        // Pausing parks positionMs; evaluating the same parked
        // position any number of times (the wall clock running
        // on) can never flip the answer — the 3 s only advance
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
    fun `delay is three seconds`() {
        assertEquals(3_000L, OutroClap.DELAY_MS)
    }
}
