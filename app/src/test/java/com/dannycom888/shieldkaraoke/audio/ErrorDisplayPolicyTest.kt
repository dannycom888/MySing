package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1.0.15 banner rule: a self-healed error clears once the
 * plain retry is confirmed progressing; a real failure never does.
 */
class ErrorDisplayPolicyTest {

    private val custom = FallbackStateMachine.Path.CUSTOM
    private val retry = FallbackStateMachine.Path.PLAIN_RETRY
    private val failed = FallbackStateMachine.Path.FAILED

    @Test
    fun noErrorNeverShows() {
        val p = ErrorDisplayPolicy()
        assertFalse(p.shouldShowError(false, custom, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(false, retry, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(false, failed, ready = true, playing = true, positionMs = 0))
    }

    @Test
    fun unrecoveredRetryKeepsShowing() {
        val p = ErrorDisplayPolicy()
        // Retry player still buffering.
        assertTrue(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        // Ready but paused: no confirmed progress.
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 1_000))
        // Playing but the position is not moving (stalled).
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 1_000))
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 1_000))
    }

    @Test
    fun recoveredProgressingClearsAndLatches() {
        val p = ErrorDisplayPolicy()
        // First playing sample only arms the position tracker.
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 5_000))
        // Position advanced while READY + playing: recovered, clear.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 5_033))
        // Latched: a later user pause or stall must not resurrect it.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 5_033))
        assertFalse(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 5_033))
    }

    @Test
    fun advanceMustBeObservedWhilePlaying() {
        val p = ErrorDisplayPolicy()
        // Position moved across observations that were not playing
        // (e.g. a seek while paused): that alone is not recovery.
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 100))
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 200))
        // Playing now, but no advance since the playing baseline.
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 200))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 240))
    }

    @Test
    fun failedPathAlwaysShowsEvenWhenPlaying() {
        val p = ErrorDisplayPolicy()
        assertTrue(p.shouldShowError(true, failed, ready = true, playing = true, positionMs = 0))
        assertTrue(p.shouldShowError(true, failed, ready = true, playing = true, positionMs = 5_000))
        assertTrue(p.shouldShowError(true, failed, ready = true, playing = true, positionMs = 9_000))
    }

    @Test
    fun customPathWithErrorShows() {
        val p = ErrorDisplayPolicy()
        assertTrue(p.shouldShowError(true, custom, ready = true, playing = true, positionMs = 0))
        assertTrue(p.shouldShowError(true, custom, ready = true, playing = true, positionMs = 5_000))
    }

    @Test
    fun resetForgetsLatchedRecovery() {
        val p = ErrorDisplayPolicy()
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 500))
        p.reset() // new song load / new error
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 500))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 900))
    }

    @Test
    fun clearingTheErrorItselfResetsForTheNextOne() {
        val p = ErrorDisplayPolicy()
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 500))
        // New load cleared the recorded error: latch wipes too, so a
        // later error starts unrecovered rather than pre-cleared.
        assertFalse(p.shouldShowError(false, retry, ready = true, playing = true, positionMs = 600))
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 600))
    }
}
