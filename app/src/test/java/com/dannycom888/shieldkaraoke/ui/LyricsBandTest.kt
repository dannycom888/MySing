package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.28: the lyrics live in a fixed safe band — dancer zone,
 * gap, band, gap, reserve band (queue panel + margin). The band's
 * edges are a pure function of the screen/stage widths, so the
 * panel showing or hiding (and the dancer showing or hiding) can
 * never move them, and the panel/QR can never reach the lyrics.
 */
class LyricsBandTest {

    // Full stage (Shield / TV density): screen 960dp wide, the
    // player column pads 24dp each side -> stage 912dp.
    private val tvBand = LyricsBand.compute(stageWidthDp = 912f, screenWidthDp = 960f)

    // Compact phone landscape: screen 800dp wide, padding 12dp
    // each side -> stage 776dp.
    private val compactBand = LyricsBand.compute(stageWidthDp = 776f, screenWidthDp = 800f)

    @Test
    fun tvBandGeometryMatchesHandComputedValues() {
        // dancer 0.36*960 = 345.6; left = dancer + 12 gap;
        // reserve = 0.19*960 + 12 = 194.4; width = what remains.
        assertEquals(345.6f, tvBand.dancerWidthDp, 0.01f)
        assertEquals(357.6f, tvBand.leftDp, 0.01f)
        assertEquals(194.4f, tvBand.reserveWidthDp, 0.01f)
        assertEquals(360.0f, tvBand.widthDp, 0.01f)
        assertEquals(717.6f, tvBand.rightDp, 0.01f)
    }

    @Test
    fun compactBandGeometryMatchesHandComputedValues() {
        assertEquals(288f, compactBand.dancerWidthDp, 0.01f)
        assertEquals(300f, compactBand.leftDp, 0.01f)
        assertEquals(164f, compactBand.reserveWidthDp, 0.01f)
        assertEquals(312f, compactBand.widthDp, 0.01f)
    }

    @Test
    fun bandEdgesAreConstantWhetherOrNotThePanelShows() {
        // The compute signature takes no visibility input at all —
        // that IS the guarantee; this pins it: the same stage in
        // the panel-shown and panel-hidden states (two calls, as
        // the UI makes them) yields byte-identical edges.
        val panelShown = LyricsBand.compute(912f, 960f)
        val panelHidden = LyricsBand.compute(912f, 960f)
        assertEquals(panelShown, panelHidden)
        assertEquals(panelShown.leftDp, panelHidden.leftDp, 0f)
        assertEquals(panelShown.widthDp, panelHidden.widthDp, 0f)
    }

    @Test
    fun panelLeftEdgeSitsExactlyOneGapRightOfTheBand() {
        // The panel is PANEL_FRACTION of the screen wide, aligned
        // to the stage's right edge; the band's right edge must
        // stop GAP_DP short of it — in both size classes.
        for ((stage, screen, band) in listOf(
            Triple(912f, 960f, tvBand),
            Triple(776f, 800f, compactBand),
        )) {
            val panelLeft = stage - LyricsBand.PANEL_FRACTION * screen
            assertEquals(
                "stage=$stage",
                LyricsBand.GAP_DP, panelLeft - band.rightDp, 0.01f,
            )
            // And the three zones plus the two gaps tile the stage.
            val tiled = band.dancerWidthDp + LyricsBand.GAP_DP +
                band.widthDp + band.reserveWidthDp
            assertEquals(stage, tiled, 0.01f)
        }
    }

    @Test
    fun bandStaysPositiveAndInsideTheStage() {
        for ((stage, screen) in listOf(912f to 960f, 776f to 800f, 500f to 540f)) {
            val band = LyricsBand.compute(stage, screen)
            assertTrue("stage=$stage width=${band.widthDp}", band.widthDp > 0f)
            assertTrue(band.leftDp >= 0f)
            assertTrue(band.rightDp <= stage)
        }
    }

    @Test
    fun degenerateWidthsClampToZeroInsteadOfGoingNegative() {
        val band = LyricsBand.compute(stageWidthDp = 100f, screenWidthDp = 960f)
        assertEquals(0f, band.widthDp, 0f)
    }
}

/**
 * v1.0.28 long-line policy: fit whole into the band by shrinking,
 * never below [LyricFit.MIN_SCALE] of the base size; past the
 * floor the band's own clip is the fallback (inside the band).
 */
class LyricFitTest {

    @Test
    fun lineThatFitsKeepsFullSize() {
        assertEquals(1f, LyricFit.scaleFor(300f, 360f), 0f)
        assertEquals(1f, LyricFit.scaleFor(360f, 360f), 0f)
        assertEquals(1f, LyricFit.scaleFor(1f, 360f), 0f)
    }

    @Test
    fun moderatelyLongLineShrinksExactlyToFit() {
        // 450 into 360 -> 0.8, above the floor.
        assertEquals(0.8f, LyricFit.scaleFor(450f, 360f), 0.001f)
        // 400 into 312 (compact band) -> ~0.78.
        assertEquals(312f / 400f, LyricFit.scaleFor(400f, 312f), 0.001f)
    }

    @Test
    fun veryLongLineStopsAtTheReadableFloor() {
        assertEquals(LyricFit.MIN_SCALE, LyricFit.scaleFor(720f, 360f), 0f)
        assertEquals(LyricFit.MIN_SCALE, LyricFit.scaleFor(10000f, 360f), 0f)
    }

    @Test
    fun degenerateInputsMeanNoScaling() {
        assertEquals(1f, LyricFit.scaleFor(0f, 360f), 0f)
        assertEquals(1f, LyricFit.scaleFor(-5f, 360f), 0f)
        assertEquals(1f, LyricFit.scaleFor(300f, 0f), 0f)
    }
}
