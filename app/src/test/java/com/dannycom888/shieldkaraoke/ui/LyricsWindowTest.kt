package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.25: the lyrics window is computed from the slot's real
 * height — the current line rests at ~40% of the height, two sung
 * lines above, and upcoming lines fill the rest (the old fixed
 * six-line block hung at the top and left the lower stage empty).
 */
class LyricsWindowTest {

    // Compact phone landscape stage, v1.0.25 sizes: current 28sp,
    // side 18sp with 2dp vertical padding each side.
    private val compactCurrent = 28f * LyricsWindow.LINE_HEIGHT_PER_SP
    private val compactSide = 18f * LyricsWindow.LINE_HEIGHT_PER_SP + 4f

    // Full stage: current 42sp, side 26sp with 5dp padding.
    private val fullCurrent = 42f * LyricsWindow.LINE_HEIGHT_PER_SP
    private val fullSide = 26f * LyricsWindow.LINE_HEIGHT_PER_SP + 10f

    @Test
    fun compactStageShowsMoreThanTheOldSixLines() {
        val win = LyricsWindow.compute(300f, compactCurrent, compactSide)
        assertEquals(2, win.above)
        assertTrue("below must fill past the old +3, was ${win.below}", win.below >= 4)
    }

    @Test
    fun currentLineCentreRestsAtFortyPercent() {
        val height = 300f
        val win = LyricsWindow.compute(height, compactCurrent, compactSide)
        val centre = win.topSpacerDp + win.above * compactSide + compactCurrent / 2f
        assertEquals(0.40f * height, centre, 1.0f)
    }

    @Test
    fun windowNeverExceedsTheStage() {
        for (h in listOf(120f, 200f, 300f, 430f, 700f)) {
            val win = LyricsWindow.compute(h, fullCurrent, fullSide)
            val used = win.topSpacerDp + win.above * fullSide +
                fullCurrent + win.below * fullSide
            assertTrue("h=$h used=$used", used <= h + 0.01f)
            assertTrue("spacer must not be negative", win.topSpacerDp >= 0f)
        }
    }

    @Test
    fun tinyStageDegradesToJustTheCurrentLine() {
        val win = LyricsWindow.compute(60f, compactCurrent, compactSide)
        assertEquals(0, win.above)
        assertEquals(0, win.below)
        // The current line (with its small centring spacer) still
        // fits the stage whole — nothing is clipped.
        assertTrue(win.topSpacerDp + compactCurrent <= 60f + 0.01f)
    }

    @Test
    fun tallStageKeepsTwoAboveAndFillsBelow() {
        val win = LyricsWindow.compute(700f, fullCurrent, fullSide)
        assertEquals(LyricsWindow.ABOVE_MAX, win.above)
        assertTrue(win.below > 6)
    }

    @Test
    fun degenerateInputsFallBackToTheClassicWindow() {
        val win = LyricsWindow.compute(0f, compactCurrent, compactSide)
        assertEquals(LyricsWindow.ABOVE_MAX, win.above)
        assertEquals(3, win.below)
    }
}
