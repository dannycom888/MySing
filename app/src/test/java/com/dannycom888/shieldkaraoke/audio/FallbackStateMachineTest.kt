package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackStateMachineTest {

    @Test
    fun startsOnCustomSink() {
        val m = FallbackStateMachine()
        assertEquals(FallbackStateMachine.Path.CUSTOM, m.path)
        assertEquals("custom", m.sinkLabel)
    }

    @Test
    fun firstErrorRetriesPlainExactlyOnce() {
        val m = FallbackStateMachine()
        assertTrue(m.onError())
        assertEquals(FallbackStateMachine.Path.PLAIN_RETRY, m.path)
        assertEquals("plain-retry", m.sinkLabel)
        assertFalse(m.onError())
        assertEquals(FallbackStateMachine.Path.FAILED, m.path)
        assertEquals("plain-failed", m.sinkLabel)
        assertFalse(m.onError())
        assertEquals(FallbackStateMachine.Path.FAILED, m.path)
    }

    @Test
    fun newLoadResetsToCustom() {
        val m = FallbackStateMachine()
        m.onError(); m.onError()
        m.onNewLoad()
        assertEquals(FallbackStateMachine.Path.CUSTOM, m.path)
        assertTrue(m.onError())
    }
}
