package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1.0.74 banner rule: an error the fallback heals is NEVER
 * displayed, at any poll — the v1.0.15 rule (show until the retry
 * is confirmed progressing) still flashed the banner at every
 * startup on the user's phone, where the custom build fails
 * first-try and the plain retry heals a fraction of a second
 * later. Only a final failure (FAILED path) or an error still
 * unresolved after the retry's settle window ever shows.
 */
class ErrorDisplayPolicyTest {

    private val custom = FallbackStateMachine.Path.CUSTOM
    private val retry = FallbackStateMachine.Path.PLAIN_RETRY
    private val bare = FallbackStateMachine.Path.BARE
    private val failed = FallbackStateMachine.Path.FAILED

    @Test
    fun noErrorNeverShows() {
        val p = ErrorDisplayPolicy()
        assertFalse(p.shouldShowError(false, custom, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(false, retry, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(false, failed, ready = true, playing = true, positionMs = 0))
    }

    @Test
    fun healedStartupErrorNeverShowsAtAnyPoll() {
        // The user's startup, poll by poll: the custom build has
        // just errored and the ladder is stepping down…
        val p = ErrorDisplayPolicy()
        assertFalse(p.shouldShowError(true, custom, ready = false, playing = false, positionMs = 0))
        // …the plain retry is buffering…
        assertFalse(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        assertFalse(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        // …ready but not yet playing…
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 0))
        // …playing, and the position advances while READY +
        // playing: healed — and it was hidden at every poll
        // before the heal landed, too.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 120))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 153))
        // Latched: a later user pause or stall must not resurrect it.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 153))
        assertFalse(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 153))
    }

    @Test
    fun bareRecoveryHealsSilentlyLikeThePlainRetry() {
        // v1.0.58's third rung under the v1.0.74 rule: hidden
        // while BARE settles, healed on confirmed progression.
        val p = ErrorDisplayPolicy()
        assertFalse(p.shouldShowError(true, bare, ready = false, playing = false, positionMs = 2_000))
        assertFalse(p.shouldShowError(true, bare, ready = true, playing = true, positionMs = 2_000))
        assertFalse(p.shouldShowError(true, bare, ready = true, playing = true, positionMs = 2_040))
        assertFalse(p.shouldShowError(true, bare, ready = true, playing = false, positionMs = 2_040))
    }

    @Test
    fun advanceMustBeObservedWhilePlaying() {
        val p = ErrorDisplayPolicy()
        // Position moved across observations that were not playing
        // (e.g. a seek while paused): that alone is not recovery —
        // but under the new rule nothing shows either way while
        // the retry is inside its settle window.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 100))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 200))
        // Playing now, but no advance since the playing baseline.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 200))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 240))
    }

    @Test
    fun unresolvedRetryShowsOnlyAfterTheSettleWindow() {
        val p = ErrorDisplayPolicy()
        // The retry never gets going: not ready, not playing, the
        // position frozen. Every poll inside the settle window
        // stays hidden — the retry is still getting its chance.
        repeat(ErrorDisplayPolicy.SETTLE_OBSERVATIONS) {
            assertFalse(
                "poll $it must stay hidden inside the settle window",
                p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0),
            )
        }
        // The window has passed with no progression: the error is
        // unresolved and the banner finally shows, and keeps
        // showing while it stays stuck (playing, but the position
        // frozen solid — a stalled decoder is not a heal).
        assertTrue(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
    }

    @Test
    fun lateHealStillClearsAndLatches() {
        val p = ErrorDisplayPolicy()
        repeat(ErrorDisplayPolicy.SETTLE_OBSERVATIONS + 1) {
            p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0)
        }
        assertTrue(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        // The retry finally starts (a very slow disk): playing
        // but the position has not advanced past the tracked
        // baseline yet — still unresolved, still showing…
        assertTrue(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
        // …the advance confirms the heal and the banner clears,
        // latched against a later pause.
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 40))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = false, positionMs = 40))
    }

    @Test
    fun failedPathAlwaysShowsEvenWhenPlaying() {
        val p = ErrorDisplayPolicy()
        assertTrue(p.shouldShowError(true, failed, ready = true, playing = true, positionMs = 0))
        assertTrue(p.shouldShowError(true, failed, ready = true, playing = true, positionMs = 5_000))
        assertTrue(p.shouldShowError(true, failed, ready = true, playing = true, positionMs = 9_000))
    }

    @Test
    fun customPathNeverShows() {
        // The ladder has not run yet: the error's fate is
        // undecided, so the banner stays down whatever the
        // player happens to be doing.
        val p = ErrorDisplayPolicy()
        assertFalse(p.shouldShowError(true, custom, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(true, custom, ready = false, playing = false, positionMs = 0))
        assertFalse(p.shouldShowError(true, custom, ready = true, playing = true, positionMs = 5_000))
    }

    @Test
    fun resetForgetsLatchedRecovery() {
        val p = ErrorDisplayPolicy()
        // Heal one error (latch set)…
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 500))
        p.reset() // new song load / new error
        // …a fresh unresolved error must be able to surface again
        // once its own settle window passes.
        repeat(ErrorDisplayPolicy.SETTLE_OBSERVATIONS) {
            assertFalse(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        }
        assertTrue(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
    }

    @Test
    fun clearingTheErrorItselfResetsForTheNextOne() {
        val p = ErrorDisplayPolicy()
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 0))
        assertFalse(p.shouldShowError(true, retry, ready = true, playing = true, positionMs = 500))
        // New load cleared the recorded error: latch wipes too, so a
        // later error starts unrecovered rather than pre-cleared —
        // it stays hidden through its settle window, then shows.
        assertFalse(p.shouldShowError(false, retry, ready = true, playing = true, positionMs = 600))
        repeat(ErrorDisplayPolicy.SETTLE_OBSERVATIONS) {
            assertFalse(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
        }
        assertTrue(p.shouldShowError(true, retry, ready = false, playing = false, positionMs = 0))
    }
}
