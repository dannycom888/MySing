package com.dannycom888.shieldkaraoke.ui

/**
 * The outro applause (v1.0.51, the user's call 2026-11-16):
 * when an ordered song's singing is done but its outro (the
 * instrumental tail after the last lyric line) still has
 * substance, the five figures come out and clap along with
 * the music for the rest of the song — "唱完3秒，如果后奏没
 * 结束，就让全部人出来鼓掌".
 *
 * The trigger is a pure function of the PLAYBACK POSITION —
 * never of a wall clock — so pausing the song freezes the
 * countdown with it: the three seconds are three seconds of
 * music past the last sung line, not three seconds of real
 * time. The "last lyric end" is [DancerActivity.lastLyricEndMs]
 * — the same boundary the dancer's outro hold / still phases
 * already use (the enhanced-LRC end marker when present, the
 * last line's start otherwise).
 *
 * Only the timing lives here; the stage renders the same
 * five-figure clap row as the end-of-song celebration, and
 * when the song truly ends the existing flow (ceremony with
 * a next song, applause send-off without one) takes over the
 * identical row — same place, same figures, no re-entry.
 * There is deliberately NO applause sound in this phase:
 * the applause recording belongs to the end-of-song flow and
 * would fight the outro music.
 */
object OutroClap {

    /** How long after the last sung line ends the row comes
     *  out: three seconds of playback. */
    const val DELAY_MS = 3_000L

    /**
     * The playback position at which the row comes out, or
     * null when this song gets no outro applause:
     *
     * - no LRC (or no sung line): [lastLyricEndMs] is null;
     * - unknown duration ([durationMs] <= 0): the tail cannot
     *   be measured, so nothing is promised;
     * - an outro of [DELAY_MS] or less: the +3 s moment must
     *   land STRICTLY before the song's end — at exactly 3 s
     *   of outro the song is already over at that moment and
     *   the end-of-song flow owns the stage.
     */
    fun triggerMs(lastLyricEndMs: Long?, durationMs: Long): Long? {
        val end = lastLyricEndMs ?: return null
        if (durationMs <= 0L) return null
        val trigger = end + DELAY_MS
        return if (trigger < durationMs) trigger else null
    }

    /** True while the outro applause should be on stage:
     *  at/past the trigger and still inside the song. */
    fun isActive(positionMs: Long, lastLyricEndMs: Long?, durationMs: Long): Boolean {
        val trigger = triggerMs(lastLyricEndMs, durationMs) ?: return false
        return positionMs >= trigger && positionMs < durationMs
    }
}
