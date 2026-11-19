package com.dannycom888.shieldkaraoke.video

import kotlin.math.roundToInt

/**
 * 切片对拍 — beat-sliced playback planning (v1.0.70, the Amy
 * video pilot; the method the user confirmed 2026-11-18,
 * proven first by the offline demo against 《最炫民族风》).
 *
 * A dance clip is not a loop of even length: its movement
 * accents (the packaged per-clip accent lists, measured off
 * the figure's frame-difference energy) fall where the
 * choreography put them. To dance ON a song's beat, the clip
 * is cut at its accents and each inter-accent segment is
 * stretched or squeezed onto a WHOLE number of song beats:
 *
 *  - segment beats = the nearest whole count (at least 1),
 *    then adjusted until the segment's playback speed —
 *    natural duration ÷ (beats × the song's beat period) —
 *    sits inside [MIN_SPEED, MAX_SPEED];
 *  - a segment too short to reach [MIN_SPEED] even on a
 *    single beat is glued to its neighbour first (its accent
 *    is absorbed — a sub-0.7-beat gesture cannot hold a beat
 *    of its own without crawling);
 *  - one beat of lead-in precedes the first accent, exactly
 *    like the demo: playback of a pass starts one song beat
 *    before the first accent is due.
 *
 * Because every segment is a whole number of beats, every
 * accent lands exactly on a beat boundary, and a full pass
 * lasts exactly totalBeats × period — the player re-syncs at
 * each pass start and no drift can accumulate across passes.
 *
 * Pure arithmetic only: the song position → clip position
 * mapping ([Pass.locate]) is a function of the position, so
 * pausing the song freezes the dance mid-segment and seeking
 * lands on the segment that belongs there — the same
 * position-drives-everything law the frame system lives by.
 */
object BeatSliceScheduler {

    /** Slowest a segment may play (the user's 0.7–1.6× guard). */
    const val MIN_SPEED = 0.7

    /** Fastest a segment may play. */
    const val MAX_SPEED = 1.6

    /**
     * One beat-aligned slice of the clip: the clip range
     * [clipStartS, clipEndS) played over [beats] song beats at
     * [speed] (= natural duration ÷ (beats × period), inside
     * the clamp for every segment [plan] emits, except a lone
     * sub-beat clip with nothing to glue to).
     */
    data class Segment(
        val clipStartS: Double,
        val clipEndS: Double,
        val beats: Int,
        val speed: Double,
    ) {
        val naturalS: Double get() = clipEndS - clipStartS

        /** How long this segment occupies in the song. */
        fun songDurationS(beatPeriodS: Double): Double = beats * beatPeriodS

        /** The clip position [songOffsetInSegmentS] seconds
         *  into this segment's song span. */
        fun clipPositionAt(songOffsetInSegmentS: Double): Double =
            clipStartS + songOffsetInSegmentS * speed
    }

    /** Where a song offset inside a pass lands: the segment
     *  index and the clip position to be showing. */
    data class Located(
        val segmentIndex: Int,
        val clipPositionS: Double,
        val segment: Segment,
    )

    /** One full pass through a clip: the ordered segments. */
    data class Pass(val segments: List<Segment>) {
        val totalBeats: Int get() = segments.sumOf { it.beats }

        /** The pass's exact length in the song: whole beats. */
        fun durationS(beatPeriodS: Double): Double = totalBeats * beatPeriodS

        /**
         * Map a song offset within the pass (seconds from the
         * pass's first beat) to the clip position that belongs
         * there. Offsets past the end clamp onto the last
         * segment's end; negative offsets onto its start.
         */
        fun locate(songOffsetS: Double, beatPeriodS: Double): Located? {
            if (segments.isEmpty() || beatPeriodS <= 0.0) return null
            var acc = 0.0
            segments.forEachIndexed { index, segment ->
                val segmentSongS = segment.songDurationS(beatPeriodS)
                if (songOffsetS < acc + segmentSongS || index == segments.lastIndex) {
                    val inSegment = (songOffsetS - acc).coerceIn(0.0, segmentSongS)
                    return Located(index, segment.clipPositionAt(inSegment), segment)
                }
                acc += segmentSongS
            }
            return null
        }
    }

    /**
     * Plan one pass over the clip whose movement accents are
     * [accentsS] (seconds, any order) and whose length is
     * [clipDurationS], against a song beat of [beatPeriodS]
     * seconds. Degenerate inputs (no duration, no beat) plan
     * an empty pass — the caller treats that as "no video".
     */
    fun plan(accentsS: List<Double>, clipDurationS: Double, beatPeriodS: Double): Pass {
        if (clipDurationS <= 0.0 || beatPeriodS <= 0.0) return Pass(emptyList())
        val accents = accentsS
            .filter { it > 0.0 && it < clipDurationS }
            .distinct()
            .sorted()
        // Segment boundaries: one beat of lead-in before the
        // first accent (clamped at the clip's head), every
        // accent, and the clip's tail.
        val bounds = ArrayList<Double>()
        bounds.add(if (accents.isEmpty()) 0.0 else maxOf(0.0, accents.first() - beatPeriodS))
        for (accent in accents) {
            if (accent > bounds.last()) bounds.add(accent)
        }
        if (clipDurationS > bounds.last()) bounds.add(clipDurationS)
        val raw = (0 until bounds.size - 1).map { bounds[it] to bounds[it + 1] }
        val merged = glueShortSegments(raw, MIN_SPEED * beatPeriodS)
        val segments = merged.map { (start, end) ->
            val natural = end - start
            var beats = maxOf(1, (natural / beatPeriodS).roundToInt())
            // Clamp by beat count: more beats slow the segment
            // down, fewer speed it up.
            while (beats > 1 && natural / (beats * beatPeriodS) < MIN_SPEED) beats--
            while (natural / (beats * beatPeriodS) > MAX_SPEED) beats++
            Segment(start, end, beats, natural / (beats * beatPeriodS))
        }
        return Pass(segments)
    }

    /**
     * Glue every segment shorter than [minDurS] into a
     * neighbour: forward into the next segment while one
     * exists (the segment's closing accent is absorbed), or
     * backward into the previous one for a short tail. A lone
     * segment has nothing to glue to and stands as it is.
     */
    private fun glueShortSegments(
        raw: List<Pair<Double, Double>>,
        minDurS: Double,
    ): List<Pair<Double, Double>> {
        if (raw.size <= 1) return raw
        val out = ArrayList<Pair<Double, Double>>()
        var segmentStart = raw.first().first
        for (index in raw.indices) {
            val segmentEnd = raw[index].second
            val isLast = index == raw.lastIndex
            val duration = segmentEnd - segmentStart
            when {
                !isLast && duration < minDurS -> {
                    // Too short to hold a beat: keep the start
                    // and let the next segment absorb this one.
                }
                isLast && duration < minDurS && out.isNotEmpty() -> {
                    val previous = out.removeAt(out.lastIndex)
                    out.add(previous.first to segmentEnd)
                }
                else -> out.add(segmentStart to segmentEnd)
            }
            if (isLast || duration >= minDurS) segmentStart = segmentEnd
        }
        return out
    }
}
