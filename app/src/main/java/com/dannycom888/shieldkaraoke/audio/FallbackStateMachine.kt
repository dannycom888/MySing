package com.dannycom888.shieldkaraoke.audio

/**
 * Playback-error self-healing policy, kept pure so it can be unit
 * tested: a song starts on the custom audio sink (vocal-cancel DSP
 * chain). The first playback error rebuilds the player ONCE with
 * the plain default renderer (no custom sink, no processor) and
 * retries; a second error gives up and stays visible in the
 * diagnostics line.
 *
 * v1.0.40 restores exactly this two-chain semantics after the
 * v1.0.38 regression: v1.0.38 made the retry use the SAME custom
 * factory build as the first attempt ("one build point"), and on
 * the user's device — where the custom-sink build has failed
 * first-try all along (every field screenshot showed
 * sink=plain-retry) — both attempts then died identically and
 * playback was completely dead (sink=plain-failed). The bare
 * retry is the lifeboat; it must stay bare.
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
