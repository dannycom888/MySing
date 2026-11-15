package com.dannycom888.shieldkaraoke.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filler session entry-edge rules (v1.0.24). The bug these pin:
 * filler singing with no player screen and no lyrics on TV,
 * because starts only ever surfaced the player screen from Home
 * and rollovers never did. The session's first start is the one
 * and only screen-moving edge; everything else leaves the screen
 * alone.
 */
class FillerSessionTest {

    @Test
    fun `first filler start of a session is the entry edge`() {
        val session = FillerSession()
        assertFalse(session.active)
        assertTrue("first start must be the edge", session.onFillerStarted())
        assertTrue(session.active)
    }

    @Test
    fun `rollovers inside a session are not edges`() {
        val session = FillerSession()
        assertTrue(session.onFillerStarted())
        assertFalse(session.onFillerStarted())
        assertFalse(session.onFillerStarted())
        assertTrue(session.active)
    }

    @Test
    fun `an ordered song ends the session - the next filler start is an edge again`() {
        val session = FillerSession()
        assertTrue(session.onFillerStarted())
        session.onOrderedStarted()
        assertFalse(session.active)
        assertTrue("filler after an ordered song opens a new session", session.onFillerStarted())
    }

    @Test
    fun `stopping filler ends the session`() {
        val session = FillerSession()
        assertTrue(session.onFillerStarted())
        session.onStopped()
        assertFalse(session.active)
        assertTrue(session.onFillerStarted())
    }

    @Test
    fun `ordered start with no session running is harmless`() {
        val session = FillerSession()
        session.onOrderedStarted()
        assertFalse(session.active)
        assertTrue(session.onFillerStarted())
    }

    @Test
    fun `stop with no session running is harmless`() {
        val session = FillerSession()
        session.onStopped()
        assertFalse(session.active)
        assertTrue(session.onFillerStarted())
    }

    @Test
    fun `a full party arc - filler, order cuts in, drains, new filler session`() {
        val session = FillerSession()
        // Cold start: filler session opens on the player screen.
        assertTrue(session.onFillerStarted())
        // Rollover behind wherever the host wandered: no yank.
        assertFalse(session.onFillerStarted())
        // A real order cuts in: session over.
        session.onOrderedStarted()
        // The ordered song drains: a NEW session opens, edge again.
        assertTrue(session.onFillerStarted())
        // And its rollovers are quiet again.
        assertFalse(session.onFillerStarted())
    }
}
