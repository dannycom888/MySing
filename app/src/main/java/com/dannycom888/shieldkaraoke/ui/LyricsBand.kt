package com.dannycom888.shieldkaraoke.ui

/**
 * The lyrics' fixed safe band on the player stage (v1.0.28).
 *
 * Until now the lyrics column took whatever width the stage row
 * had left after the dancer zone and the queue panel floated OVER
 * its right edge (v1.0.24's overlay) — a long line's tail slid
 * under the panel, and the top-right QR card lived in the same
 * corner. The user's ruling: move the lyrics left and never let
 * anything cover them.
 *
 * So the stage row is now three fixed zones, computed here and
 * nowhere else:
 *
 *   [ dancer zone ][gap][   lyrics band   ][gap][ reserve band ]
 *
 * The reserve band on the right is exactly as wide as the queue
 * panel plus a margin, and it is reserved ALWAYS — whether the
 * panel is on screen or not, whether the dancer is dancing or
 * hidden. The band's edges are a pure function of the screen and
 * stage widths; nothing that comes and goes (panel, dancer,
 * celebration) can move them, so the lyrics can never jump and
 * the panel/QR can never reach them: both live inside the
 * reserve band by construction.
 *
 * All widths are dp floats; the fractions are of the SCREEN
 * width, matching how the dancer zone and the panel have always
 * been sized (0.36 / 0.19 of screenW).
 */
object LyricsBand {
    /** Dancer zone width, as a fraction of the screen width
     *  (unchanged since the dancer moved left of the lyrics). */
    const val DANCER_FRACTION = 0.36f

    /** Queue panel width, as a fraction of the screen width
     *  (v1.0.24's floating overlay width — the reserve band is
     *  built around the panel's real footprint). */
    const val PANEL_FRACTION = 0.19f

    /** Breathing room on both sides of the band: between the
     *  dancer zone and the first lyric character, and between
     *  the last character and the reserve band. */
    const val GAP_DP = 12f

    /**
     * The band, in stage coordinates (dp from the stage's left
     * edge). [leftDp] is where the lyrics column starts;
     * [widthDp] is its fixed width. The reserve band occupies
     * [stageWidthDp] − [reserveWidthDp] .. [stageWidthDp].
     */
    data class Band(
        val dancerWidthDp: Float,
        val leftDp: Float,
        val widthDp: Float,
        val reserveWidthDp: Float,
    ) {
        /** Stage-relative x of the band's right edge. */
        val rightDp: Float get() = leftDp + widthDp
    }

    /** The reserve band's width on its own (v1.0.48): the row of
     *  six sizes and places itself against this same number, so
     *  the panel's territory is computed here and nowhere else. */
    fun reserveWidthDp(screenWidthDp: Float): Float =
        PANEL_FRACTION * screenWidthDp + GAP_DP

    fun compute(stageWidthDp: Float, screenWidthDp: Float): Band {
        val dancer = DANCER_FRACTION * screenWidthDp
        val reserve = reserveWidthDp(screenWidthDp)
        val left = dancer + GAP_DP
        val width = (stageWidthDp - left - reserve).coerceAtLeast(0f)
        return Band(
            dancerWidthDp = dancer,
            leftDp = left,
            widthDp = width,
            reserveWidthDp = reserve,
        )
    }
}

/**
 * Fit-to-width for a single lyric line inside the band (v1.0.28).
 *
 * A line renders at its full size whenever it fits the band. A
 * longer line shrinks just enough to fit whole — the alternative
 * (Compose's default single-line clip) silently ate the tail,
 * which is exactly the "lyrics covered" complaint when the clip
 * happened under the queue panel. Shrinking stops at
 * [MIN_SCALE] of the base size: below that the line would be
 * unreadable on a TV, so the floor size is kept and the band's
 * own clip takes the overflow — inside the band, never under
 * anything else.
 */
object LyricFit {
    /** Smallest readable fraction of a line's base font size. */
    const val MIN_SCALE = 0.6f

    /**
     * Scale factor in ([MIN_SCALE], 1] for a line whose natural
     * width at base size is [naturalWidthPx] inside a band
     * [bandWidthPx] wide. Degenerate inputs (nothing measured
     * yet, zero-width band) return 1 — no scaling, no crash.
     */
    fun scaleFor(naturalWidthPx: Float, bandWidthPx: Float): Float {
        if (naturalWidthPx <= 0f || bandWidthPx <= 0f) return 1f
        if (naturalWidthPx <= bandWidthPx) return 1f
        return (bandWidthPx / naturalWidthPx).coerceIn(MIN_SCALE, 1f)
    }
}
