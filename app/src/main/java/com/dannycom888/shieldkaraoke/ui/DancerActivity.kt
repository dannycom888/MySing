package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.lyrics.LyricLine

/**
 * Whether the little dancer is still dancing at a playback position.
 *
 * The user's rule: "歌都唱完了，剩下音乐，别跳了" — once the last
 * lyric line has been sung and only the outro music remains, the
 * dancer stops: it stands in its neutral first frame with no frame
 * cycling and no bounce/sway (the baby does not fall, the robot
 * halts too). The intro is untouched — dancing starts with the song.
 *
 * Pure functions of the parsed lyrics + position, so the rule is
 * unit-testable without a screen.
 */
object DancerActivity {

    /** Frame shown while standing still (each dancer's neutral pose). */
    const val STILL_FRAME = 0

    /**
     * When the singing ends in [lines]: the last line's end time.
     *
     * An enhanced (word-timed) line carries its end explicitly — the
     * trailing empty fragment is the line's end marker, and its start
     * is when the line stops being sung (see [com.dannycom888
     * .shieldkaraoke.lyrics.WordTiming]). A plain line has no end
     * marker, so its own start stands in as the fallback: the dancer
     * stops once the last line has begun and its start has passed.
     * No lines at all → null (no "sung out" moment exists).
     */
    fun lastLyricEndMs(lines: List<LyricLine>): Long? {
        val last = lines.lastOrNull() ?: return null
        val marker = last.words.lastOrNull()
        return if (marker != null && marker.text.isEmpty() && marker.startMs >= last.timeMs) {
            marker.startMs
        } else {
            last.timeMs
        }
    }

    /**
     * Dancing at [positionMs]? Past the last lyric's end → still.
     * A null [lastLyricEndMs] (the song has no same-name LRC) means
     * there is no singing to finish, so the dancer dances the whole
     * song rather than stopping by mistake.
     */
    fun isDancing(positionMs: Long, lastLyricEndMs: Long?): Boolean =
        lastLyricEndMs == null || positionMs <= lastLyricEndMs
}
