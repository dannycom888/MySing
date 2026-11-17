package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The error ladder (v1.0.58): CUSTOM → PLAIN_RETRY → BARE →
 * FAILED, and the sticky floor — once a tier has failed this
 * run, later loads start at the tier that was stepped down to
 * instead of re-climbing to custom and paying one guaranteed
 * error per song (the user's tablet threw
 * ERROR_CODE_FAILED_RUNTIME_CHECK at every song start that way).
 */
class FallbackStateMachineTest {

    @Test
    fun startsOnCustomSink() {
        val m = FallbackStateMachine()
        assertEquals(FallbackStateMachine.Path.CUSTOM, m.path)
        assertEquals(FallbackStateMachine.Path.CUSTOM, m.startPath)
        assertEquals("custom", m.sinkLabel)
    }

    @Test
    fun ladderStepsDownOneRungPerError() {
        val m = FallbackStateMachine()
        assertTrue(m.onError())
        assertEquals(FallbackStateMachine.Path.PLAIN_RETRY, m.path)
        assertEquals("plain-retry", m.sinkLabel)
        assertTrue(m.onError())
        assertEquals(FallbackStateMachine.Path.BARE, m.path)
        assertEquals("bare", m.sinkLabel)
        assertFalse(m.onError())
        assertEquals(FallbackStateMachine.Path.FAILED, m.path)
        assertEquals("bare-failed", m.sinkLabel)
        assertFalse(m.onError())
        assertEquals(FallbackStateMachine.Path.FAILED, m.path)
    }

    @Test
    fun downgradeSticksForLaterLoads() {
        val m = FallbackStateMachine()
        m.onError() // custom died on this device; retry on plain
        assertEquals(FallbackStateMachine.Path.PLAIN_RETRY, m.startPath)
        // The next song starts straight on plain — no fresh
        // guaranteed error on custom first.
        assertEquals(FallbackStateMachine.Path.PLAIN_RETRY, m.onNewLoad())
        assertEquals(FallbackStateMachine.Path.PLAIN_RETRY, m.path)
        assertEquals("plain-retry", m.sinkLabel)
        // And the song after that too.
        assertEquals(FallbackStateMachine.Path.PLAIN_RETRY, m.onNewLoad())
    }

    @Test
    fun stickinessFollowsEachStepDown() {
        val m = FallbackStateMachine()
        m.onError(); m.onError() // down to BARE
        assertEquals(FallbackStateMachine.Path.BARE, m.startPath)
        assertEquals(FallbackStateMachine.Path.BARE, m.onNewLoad())
        assertEquals("bare", m.sinkLabel)
    }

    @Test
    fun afterFailedTheNextLoadStartsOnBareNotCustom() {
        val m = FallbackStateMachine()
        m.onError(); m.onError(); m.onError()
        assertEquals(FallbackStateMachine.Path.FAILED, m.path)
        // FAILED is not a build: the floor stays at BARE, the
        // last real build, and an error there fails again.
        assertEquals(FallbackStateMachine.Path.BARE, m.startPath)
        assertEquals(FallbackStateMachine.Path.BARE, m.onNewLoad())
        assertFalse(m.onError())
        assertEquals(FallbackStateMachine.Path.FAILED, m.path)
    }

    @Test
    fun healthyRunNeverLeavesCustom() {
        val m = FallbackStateMachine()
        repeat(3) {
            assertEquals(FallbackStateMachine.Path.CUSTOM, m.onNewLoad())
        }
        assertEquals(FallbackStateMachine.Path.CUSTOM, m.startPath)
        assertEquals("custom", m.sinkLabel)
    }
}
