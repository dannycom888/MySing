package com.dannycom888.shieldkaraoke.state

/**
 * The karaoke sweep colour, chosen on the TV admin page (v1.0.26:
 * 「扫光颜色」). The sweep is the coloured fill that travels
 * through each character of the current lyric line as it is sung,
 * over the line's unchanged cyan glow base — only the fill colour
 * changes, never the glow.
 *
 * The three hues started as the ones MyLRC Auto settled on in
 * its v1.0.36: gold (the colour the sweep has had since the
 * gradient landed in v1.0.2, and the default), a green and a
 * light pink. The orange and red MyLRC tried in between were
 * rejected by the user as harsh （太刺眼） and are deliberately
 * NOT offered here. The green diverged from MyLRC in v1.0.38:
 * the user found the pale #A9E5A1 too light on the TV and picked
 * the deeper #34C759 off a swatch card — MySing only; MyLRC
 * keeps its own palette.
 *
 * This enum only names the choice and carries the exact ARGB
 * value; the Compose rendering converts [argb] to a Color where
 * it draws. Pure Kotlin, so the mapping, the stored-name parsing
 * and the fallback are unit-testable without a UI.
 */
enum class SweepColor(val argb: Long) {
    GOLD(0xFFFFD54FL),
    LIGHT_GREEN(0xFF34C759L),
    LIGHT_PINK(0xFFFFB7C5L),
    ;

    companion object {
        /** Gold — the sweep's colour since v1.0.2; choosing
         *  nothing changes nothing. */
        val DEFAULT: SweepColor = GOLD

        /**
         * The choice a stored prefs value names. Only the three
         * current names map to themselves; null (never stored)
         * and any unrecognised value fall back to [DEFAULT], so
         * a corrupt or future value can never break the sweep.
         */
        fun fromStoredName(name: String?): SweepColor =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
