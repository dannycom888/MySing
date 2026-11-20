package com.dannycom888.shieldkaraoke.audio

/**
 * Decides whether the red "播放错误" banner is on screen.
 *
 * History: v1.0.3's rule was "an error never auto-hides" — right
 * for a real fault, wrong for the self-healed case, where the
 * banner sat over an entire healthy song. v1.0.15 softened it:
 * show until the plain retry was confirmed progressing, then
 * clear. On the user's phone that still left a visible FLASH at
 * every startup: the custom build fails first-try, the banner
 * pops up, the retry heals within a fraction of a second and
 * the banner clears (user report, 2026-11-19: "一开机有一行红字
 * 一闪而过" — and after v1.0.72, "红字一闪而过的毛病还在").
 * A flash the user cannot even read is pure noise, so v1.0.74
 * inverts the default: an error the fallback heals is NEVER
 * displayed, at any poll.
 *
 * Policy (pure, unit-tested):
 * - no recorded error → never show.
 * - FAILED path → always show: every rung died, BARE included —
 *   a real fault the user must see, sticky as it has always been.
 * - CUSTOM path → never show: the ladder has not run yet, the
 *   error's fate is undecided.
 * - PLAIN_RETRY / BARE (the recovery rungs) → hidden while the
 *   retry settles. Two exits:
 *   - progression confirmed (READY + isPlaying + the position
 *     advanced between two observations): the error is healed,
 *     the clearance latches for the rest of the song — a later
 *     user pause or brief stall must not resurrect the banner;
 *   - [SETTLE_OBSERVATIONS] polls pass with no progression
 *     confirmed: the retry had its chance and the error is
 *     still unresolved, so the banner shows (and a late heal
 *     still clears it via the same latch). The diagnostics
 *     small print keeps the sink label either way, so a healed
 *     degradation stays on record.
 */
class ErrorDisplayPolicy {

    private var recovered = false
    private var lastPositionMs: Long? = null
    private var unsettledPolls = 0

    /** Forget any latched recovery (new song load / new error). */
    fun reset() {
        recovered = false
        lastPositionMs = null
        unsettledPolls = 0
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
            FallbackStateMachine.Path.CUSTOM -> false
            FallbackStateMachine.Path.PLAIN_RETRY,
            FallbackStateMachine.Path.BARE -> {
                if (recovered) return false
                val prev = lastPositionMs
                if (ready && playing && prev != null && positionMs > prev) {
                    recovered = true
                    return false
                }
                lastPositionMs = positionMs
                unsettledPolls++
                unsettledPolls > SETTLE_OBSERVATIONS
            }
        }
    }

    companion object {
        /**
         * How many polls a recovery rung gets to confirm
         * progression before its error counts as unresolved and
         * the banner shows. The only caller polls every ~33 ms
         * (the player screen's position loop), so this is roughly
         * three seconds — far beyond a local file's retry, which
         * heals in a few polls, and short enough that a genuinely
         * stuck retry still surfaces while the song is young.
         */
        const val SETTLE_OBSERVATIONS = 90
    }
}
