package com.dannycom888.shieldkaraoke.audio

/**
 * Playback-error self-healing policy, kept pure so it can be unit
 * tested: a song starts on the primary player build. The first
 * playback error rebuilds the player ONCE and retries; a second
 * error gives up and stays visible in the diagnostics line.
 * (Pre-v1.0.38 the retry was a processor-less plain build; since
 * v1.0.38 both builds carry the same vocal-cancel processor, so
 * the paths here name the ATTEMPT — first vs. retry — not two
 * different audio chains.)
 */
class FallbackStateMachine {

    enum class Path { CUSTOM, PLAIN_RETRY, FAILED }

    var path: Path = Path.CUSTOM
        private set

    /** A fresh song load resets to the custom sink. */
    fun onNewLoad() {
        path = Path.CUSTOM
    }

    /**
     * Record a playback error. Returns true exactly once — for the
     * first error — meaning "rebuild with the plain renderer and
     * retry the current song".
     */
    fun onError(): Boolean = when (path) {
        Path.CUSTOM -> {
            path = Path.PLAIN_RETRY
            true
        }
        Path.PLAIN_RETRY -> {
            path = Path.FAILED
            false
        }
        Path.FAILED -> false
    }

    /** Diagnostics label for the path currently in use. */
    val sinkLabel: String
        get() = when (path) {
            Path.CUSTOM -> "custom"
            Path.PLAIN_RETRY -> "plain-retry"
            Path.FAILED -> "plain-failed"
        }
}
