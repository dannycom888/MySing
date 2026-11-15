package com.dannycom888.shieldkaraoke.audio

/**
 * Decides whether the red "播放错误" banner stays on screen.
 *
 * v1.0.3's rule was "an error never auto-hides". That is right for
 * a real fault but wrong for the self-healed case: when the first
 * error triggers the plain-retry rebuild and that retry then plays
 * fine, the banner outlived its usefulness and sat over an entire
 * healthy song (user screenshot 2026-11-15: Youngblood playing
 * normally, position advancing, sink=plain-retry, red error text
 * still up).
 *
 * Policy (pure, unit-tested):
 * - no recorded error → never show.
 * - FAILED path → always show: the plain retry died too — a real
 *   fault the user must see.
 * - PLAIN_RETRY path → show until playback is confirmed
 *   progressing: READY + isPlaying + the position advanced
 *   between two observations. The clearance then latches for the
 *   rest of the song — a later user pause or a brief stall must
 *   not resurrect the banner; the diagnostics small print still
 *   shows sink=plain-retry, so the degradation stays on record.
 * - CUSTOM path with an error → show (that state only exists
 *   transiently inside the error handler; be conservative).
 */
class ErrorDisplayPolicy {

    private var recovered = false
    private var lastPositionMs: Long? = null

    /** Forget any latched recovery (new song load / new error). */
    fun reset() {
        recovered = false
        lastPositionMs = null
    }

    /**
     * Feed one observation of the live player; returns true while
     * the banner should be visible.
     */
    fun shouldShowError(
        hasError: Boolean,
        path: FallbackStateMachine.Path,
        ready: Boolean,
        playing: Boolean,
        positionMs: Long,
    ): Boolean {
        if (!hasError) {
            reset()
            return false
        }
        return when (path) {
            FallbackStateMachine.Path.FAILED -> true
            FallbackStateMachine.Path.CUSTOM -> true
            FallbackStateMachine.Path.PLAIN_RETRY -> {
                if (!recovered) {
                    val prev = lastPositionMs
                    if (ready && playing && prev != null && positionMs > prev) {
                        recovered = true
                    }
                    lastPositionMs = positionMs
                }
                !recovered
            }
        }
    }
}
