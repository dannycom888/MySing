package com.dannycom888.shieldkaraoke.lyrics

/**
 * Per-character fill progress for the karaoke sweep.
 *
 * Where [WordSweep] only counts whole sung characters (so a
 * character pops from base colour to gold in one step), this drives
 * the smooth fill: every character is gold-filled from its left
 * edge to its right edge over its own singing window, so the gold
 * boundary travels continuously across the line.
 *
 * A character's window is [start, next character's start) — the
 * same derivation the whole-character sweep always used: inside a
 * multi-character fragment the fragment's span up to the next
 * fragment's start is divided evenly among its characters, and the
 * line-final character is bounded by the trailing end-marker
 * fragment when one is present.
 */
object SweepProgress {

    /**
     * How much of one character is gold-filled at [nowMs]: 0 before
     * the character starts, 1 once it ends, linear in between.
     *
     * A degenerate window ([endMs] <= [startMs]) — the trailing
     * fragment that has no next start to bound it — fills whole the
     * moment it starts, exactly how the whole-character sweep
     * always treated it.
     */
    fun progress(nowMs: Long, startMs: Long, endMs: Long): Float {
        if (endMs <= startMs) return if (nowMs >= startMs) 1f else 0f
        if (nowMs <= startMs) return 0f
        if (nowMs >= endMs) return 1f
        return (nowMs - startMs).toFloat() / (endMs - startMs).toFloat()
    }

    /** One character's singing window within a word-timed line. */
    data class CharWindow(val startMs: Long, val endMs: Long)

    /**
     * Expands a line's word fragments into one timing window per
     * character of the concatenated line text (in the same units
     * [LyricLine.text] is indexed by). Adjacent windows are
     * contiguous: a character's end is the next character's start.
     * An empty-text fragment (the enhanced line's end marker)
     * contributes no characters but bounds the fragment before it.
     * A fragment with no usable next start gives each of its
     * characters a degenerate window at the fragment's own start,
     * so the whole fragment lights at once — the pre-v1.0.2
     * behaviour for a trailing fragment.
     */
    fun charWindows(words: List<WordTiming>): List<CharWindow> {
        val result = ArrayList<CharWindow>()
        for (index in words.indices) {
            val segment = words[index]
            val length = segment.text.length
            if (length == 0) continue
            val nextStart = words.getOrNull(index + 1)?.startMs
            if (nextStart == null || nextStart <= segment.startMs) {
                repeat(length) { result += CharWindow(segment.startMs, segment.startMs) }
            } else {
                val span = nextStart - segment.startMs
                for (j in 0 until length) {
                    val charStart = segment.startMs + span * j / length
                    val charEnd = if (j == length - 1) nextStart
                        else segment.startMs + span * (j + 1) / length
                    result += CharWindow(charStart, charEnd)
                }
            }
        }
        return result
    }
}
