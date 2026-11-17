package com.dannycom888.shieldkaraoke.audio

/**
 * Playback-error self-healing policy, kept pure so it can be unit
 * tested: a song starts on the custom audio sink (vocal-cancel DSP
 * chain). Each playback error steps one rung down a ladder of
 * plainer builds and retries the song; only when the last rung
 * errors too does the fault stay visible in the diagnostics line.
 *
 * v1.0.40 restored the two-chain semantics after the v1.0.38
 * regression: v1.0.38 made the retry use the SAME custom factory
 * build as the first attempt ("one build point"), and on the
 * user's device — where the custom-sink build has failed
 * first-try all along (every field screenshot showed
 * sink=plain-retry) — both attempts then died identically and
 * playback was completely dead (sink=plain-failed). The bare
 * retry is the lifeboat; it must stay bare.
 *
 * v1.0.58 hardens the ladder for the user's tablet, whose
 * v1.0.55 diagnostics line showed the custom build throwing
 * ERROR_CODE_FAILED_RUNTIME_CHECK at song start and self-healing
 * to plain-retry — on EVERY song, because a fresh load always
 * climbed back to custom first. Two changes:
 *
 *  - Sticky floor: the ladder only goes DOWN within a run. Once
 *    a tier has failed and the machine stepped down, later loads
 *    start at the tier that was stepped down TO (the best build
 *    this device has not yet failed on) instead of re-climbing
 *    to custom and paying one guaranteed error per song. A fresh
 *    app run starts at custom again.
 *  - A third rung, BARE: a stock build with float output off,
 *    no processors and no LoudnessEnhancer — everything the
 *    PLAIN retry still carries (float sink, session enhancer)
 *    stripped, aimed at the two remaining suspects on the
 *    tablet (the float/AudioTrack layer and the enhancer).
 *    CUSTOM → PLAIN → BARE → FAILED.
 */
class FallbackStateMachine {

    enum class Path { CUSTOM, PLAIN_RETRY, BARE, FAILED }

    /** The build the current song is playing (or failing) on. */
    var path: Path = Path.CUSTOM
        private set

    /**
     * The sticky floor: the build later loads start from. It
     * moves down exactly when [onError] steps the ladder down,
     * and never moves back up within a run. FAILED is not a
     * build, so a song that died on BARE leaves the floor at
     * BARE — the next song starts on the last real build rather
     * than re-climbing past two tiers this device already failed.
     */
    var startPath: Path = Path.CUSTOM
        private set

    /**
     * A fresh song load starts at the sticky floor — custom on a
     * healthy device, the stepped-down-to tier on one that has
     * already failed this run. Returns the build to start on.
     */
    fun onNewLoad(): Path {
        path = startPath
        return path
    }

    /**
     * Record a playback error. Returns true when a plainer build
     * remains below — "rebuild one rung down and retry the
     * current song" — and false only when BARE itself has
     * errored: the fault is real, stay put and keep it visible.
     */
    fun onError(): Boolean = when (path) {
        Path.CUSTOM -> stepDownTo(Path.PLAIN_RETRY)
        Path.PLAIN_RETRY -> stepDownTo(Path.BARE)
        Path.BARE -> {
            path = Path.FAILED
            false
        }
        Path.FAILED -> false
    }

    private fun stepDownTo(next: Path): Boolean {
        path = next
        startPath = next
        return true
    }

    /** Diagnostics label for the path currently in use. */
    val sinkLabel: String
        get() = when (path) {
            Path.CUSTOM -> "custom"
            Path.PLAIN_RETRY -> "plain-retry"
            Path.BARE -> "bare"
            Path.FAILED -> "bare-failed"
        }
}
