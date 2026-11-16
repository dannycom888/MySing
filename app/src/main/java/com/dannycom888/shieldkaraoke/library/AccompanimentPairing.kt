package com.dannycom888.shieldkaraoke.library

/**
 * The accompaniment-file naming rule (v1.0.47): the separate
 * source-separation app writes its result beside the original as
 * "原名（伴奏）.mp3" — same folder, same stem, one suffix. MySing
 * recognises that suffix at scan time and pairs the file with its
 * original instead of listing it as a song of its own.
 *
 * Both bracket shapes count: the full-width （伴奏） the
 * separation app writes, and the half-width (伴奏) a file might
 * pick up after a trip through a PC. The suffix is matched on the
 * stem (file name minus extension), exactly, brackets included —
 * a title that merely ends in the word 伴奏 without brackets is
 * an ordinary song title and is never mistaken for a pairing.
 *
 * Pure string logic, split out of the scanner so the rule is
 * unit-testable on its own.
 */
object AccompanimentPairing {

    private val SUFFIXES = listOf("（伴奏）", "(伴奏)")

    /**
     * The original's stem when [stem] names an accompaniment
     * file, or null when it does not. Only ONE suffix is
     * stripped: "X（伴奏）（伴奏）" pairs against a song whose
     * stem is "X（伴奏）" — which is itself an accompaniment
     * name, so in practice it finds no partner and is ignored.
     * A bare "（伴奏）" has an empty base and is not a pairing.
     */
    fun baseStemOf(stem: String): String? {
        for (suffix in SUFFIXES) {
            if (stem.endsWith(suffix)) {
                val base = stem.dropLast(suffix.length)
                return base.ifEmpty { null }
            }
        }
        return null
    }

    fun isAccompanimentStem(stem: String): Boolean = baseStemOf(stem) != null
}
