package com.dannycom888.shieldkaraoke.ui

/**
 * The outro applause (v1.0.51, the user's call 2026-11-16;
 * timing aligned in v1.0.53): when an ordered song's singing
 * is done but its outro (the instrumental tail after the last
 * lyric line) still has substance, the five figures come out
 * and clap along with the music for the rest of the song —
 * "唱完3秒，如果后奏没结束，就让全部人出来鼓掌".
 *
 * The tail runs in three stages, in sequence:
 *
 *  1. HOLD — the solo dancer freezes in its last dance pose
 *     for [DancerActivity.OUTRO_HOLD_MS] (2 s), the v1.0.22
 *     outro hold, unchanged;
 *  2. BREATHE — the dancer puts its hands down and stands
 *     breathing (the STILL phase) for [BREATH_MS] (3 s) —
 *     the user's "歌唱完3秒喘气，全部人才出来": the three
 *     seconds are THIS breathing stretch;
 *  3. ROW — if the music is still running when the breathing
 *     completes, the five figures come out clapping.
 *
 * So the trigger lands at lastLyricEnd + 5 s (2 s hold + 3 s
 * breathing). In v1.0.51 it was +3 s, because the trigger was
 * counted straight from the lyric end before the breathing
 * stretch had a defined length of its own; v1.0.53 counts the
 * hold and the breathing separately and the trigger moves to
 * +5 s. The solo dancer's own phases (hold, then breathing)
 * are [DancerActivity.phase]'s business and are NOT changed
 * by this — only the moment the row joins.
 *
 * The trigger is a pure function of the PLAYBACK POSITION —
 * never of a wall clock — so pausing the song freezes the
 * countdown with it: the five seconds are five seconds of
 * music past the last sung line, not five seconds of real
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

    /** Stage 2 of the tail: how long the solo dancer stands
     *  breathing after the 2 s outro hold before the row comes
     *  out — three seconds of playback (v1.0.53). */
    const val BREATH_MS = 3_000L

    /**
     * The offset from the last sung line's end at which the row
     * comes out: the outro hold ([DancerActivity.OUTRO_HOLD_MS],
     * 2 s) plus the breathing stretch ([BREATH_MS], 3 s) = 5 s.
     * (v1.0.51: 3 s flat from the lyric end; v1.0.53 aligned it
     * to hold + breathing.)
     */
    val DELAY_MS: Long = DancerActivity.OUTRO_HOLD_MS + BREATH_MS

    /**
     * The playback position at which the row comes out, or
     * null when this song gets no outro applause:
     *
     * - no LRC (or no sung line): [lastLyricEndMs] is null;
     * - unknown duration ([durationMs] <= 0): the tail cannot
     *   be measured, so nothing is promised;
     * - an outro of [DELAY_MS] or less: the +5 s moment must
     *   land STRICTLY before the song's end — at exactly 5 s
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
