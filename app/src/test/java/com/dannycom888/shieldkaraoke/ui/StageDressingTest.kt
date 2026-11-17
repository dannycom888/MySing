package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.57 pins for the stage dressing the user approved from
 * the mock (stage_mock_v1.png): light beams, the floor band,
 * and a mirrored reflection under every figure. The geometry
 * is pure ([StageFloor] / [StageBeams]); the layer order lives
 * in Compose calls, so — like DancerImageStructureTest — it is
 * pinned on the MainActivity source.
 */
class StageDressingTest {

    // ---- StageFloor -------------------------------------------

    @Test
    fun reflectionIsThirtyFivePercentOfTheFigure() {
        assertEquals(0.35f, StageFloor.REFLECTION_FRACTION, 0.0001f)
        assertEquals(70f, StageFloor.reflectionDepthDp(200f), 0.001f)
        assertEquals(0f, StageFloor.reflectionDepthDp(0f), 0.001f)
    }

    @Test
    fun restingFigurePlusReflectionHonoursTheDesignFraction() {
        // The 0.80 fraction, solved: h = 0.8 × 400 / 1.28 = 250,
        // reflection 87.5 — the figure is the fraction OF THE
        // SPACE ABOVE ITS OWN FLOOR: h == 0.8 × (400 − 87.5),
        // and figure + reflection together stay inside the zone.
        val h = StageFloor.restingHeightDp(400f, 0.80f)
        assertEquals(250f, h, 0.01f)
        val depth = StageFloor.reflectionDepthDp(h)
        assertEquals(h, 0.80f * (400f - depth), 0.05f)
        assertTrue(h + depth <= 400f)
        // Compact fraction, same law.
        val hc = StageFloor.restingHeightDp(400f, 0.72f)
        assertEquals(hc, 0.72f * (400f - StageFloor.reflectionDepthDp(hc)), 0.05f)
    }

    @Test
    fun floorLineNeverMovesWhileTheFigureGrows() {
        // At the rest fraction, heightAtFractionDp over the
        // resting figure's own floor depth returns the resting
        // height itself — the step envelope and the floor agree.
        for (zone in listOf(300f, 440f, 900f)) {
            for (rest in listOf(0.72f, 0.80f)) {
                val depth = StageFloor.reflectionDepthDp(
                    StageFloor.restingHeightDp(zone, rest),
                )
                assertEquals(
                    StageFloor.restingHeightDp(zone, rest),
                    StageFloor.heightAtFractionDp(zone, rest, depth),
                    0.05f,
                )
            }
        }
    }

    @Test
    fun stepForwardStillFitsAndKeepsItsGrowthRatio() {
        for ((rest, step) in listOf(0.80f to 0.95f, 0.72f to 0.88f)) {
            val zone = 440f
            val depth = StageFloor.reflectionDepthDp(
                StageFloor.restingHeightDp(zone, rest),
            )
            val base = StageFloor.heightAtFractionDp(zone, rest, depth)
            val big = StageFloor.heightAtFractionDp(zone, step, depth)
            assertTrue(
                "stepped figure + its floor must fit the zone: $big + $depth <= $zone",
                big + depth <= zone + 0.05f,
            )
            assertEquals(
                "the step's growth ratio is the pre-floor one",
                step / rest,
                big / base,
                0.001f,
            )
        }
    }

    @Test
    fun reflectionFlipMapsFeetToTheBaselineAndHeadToTheDepth() {
        val h = 200f
        val d = StageFloor.reflectionDepthDp(h)
        val s = StageFloor.reflectionScaleY(h, d)
        val oy = StageFloor.reflectionOriginFraction(h, d) * h
        fun visual(y: Float) = oy + (y - oy) * s
        assertEquals("feet (local H) land on the baseline", 0f, visual(h), 0.01f)
        assertEquals("head (local 0) lands at the reflection depth", d, visual(0f), 0.01f)
        assertTrue("the flip is a squash, never an enlargement", s < 0f && -s < 1f)
        assertEquals(-StageFloor.REFLECTION_FRACTION, s, 0.0001f)
    }

    @Test
    fun reflectionFadeBottomsOutAtTheBandEdge() {
        assertEquals(
            StageFloor.REFLECTION_FRACTION,
            StageFloor.reflectionFadeStop(200f, StageFloor.reflectionDepthDp(200f)),
            0.0001f,
        )
        assertTrue(
            "the reflection never rivals the figure itself",
            StageFloor.REFLECTION_TOP_ALPHA in 0.3f..0.7f,
        )
    }

    // ---- StageBeams -------------------------------------------

    @Test
    fun beamsFanOutFromTheTopCornersAndReachTheFloor() {
        val w = 1600f
        val h = 900f
        for (right in listOf(false, true)) {
            val pts = StageBeams.polygon(w, h, right)
            assertEquals(4, pts.size)
            assertTrue("both beams start at the very top", pts[0].y == 0f && pts[1].y == 0f)
            assertTrue(
                "the beam reaches the floor band",
                pts[2].y >= 0.8f * h && pts[3].y == pts[2].y,
            )
            val topWidth = kotlin.math.abs(pts[1].x - pts[0].x)
            val bottomWidth = kotlin.math.abs(pts[2].x - pts[3].x)
            assertTrue("the beam fans out as it falls", bottomWidth > topWidth)
            for (p in pts) {
                assertTrue("inside the stage horizontally", p.x in 0f..w)
            }
        }
        // The left beam slants toward the centre as it falls.
        val left = StageBeams.polygon(w, h, right = false)
        assertTrue(left[3].x > left[0].x && left[2].x > left[1].x)
    }

    @Test
    fun rightBeamMirrorsTheLeft() {
        val w = 1600f
        val h = 900f
        val left = StageBeams.polygon(w, h, right = false)
        val right = StageBeams.polygon(w, h, right = true)
        for (i in left.indices) {
            assertEquals(w - left[i].x, right[i].x, 0.01f)
            assertEquals(left[i].y, right[i].y, 0.01f)
        }
    }

    @Test
    fun beamsStayBackdropQuiet() {
        // The legibility ceiling: a beam core brighter than
        // this would start washing the lyrics it sits under.
        assertTrue(StageBeams.PEAK_ALPHA > 0f && StageBeams.PEAK_ALPHA <= 0.20f)
        assertTrue(StageBeams.MELT_AT_BOTTOM in 0.3f..0.8f)
    }

    // ---- layer order (source pins) ----------------------------

    private fun mainActivitySource(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("MainActivity.kt not found; tried ${candidates.map { it.absolutePath }}")
        return file.readText()
    }

    @Test
    fun rootLayersRunBeamsThenBubblesThenStars() {
        val src = mainActivitySource()
        val beams = src.indexOf("StageBeamBackdrop(")
        val bubbles = src.indexOf("BubbleBackground(")
        val stars = src.indexOf("StarBackground(")
        assertTrue("beams are composed in the root", beams >= 0)
        assertTrue(
            "beams sit UNDER the bubble field (the stage's bottom layer)",
            beams < bubbles,
        )
        assertTrue(
            "stars share the bubbles' ambient layer, composed right after them",
            bubbles < stars,
        )
        assertTrue(
            "beams belong to the player stage only",
            src.substring((beams - 200).coerceAtLeast(0), beams)
                .contains("Screen.PLAYER"),
        )
    }

    @Test
    fun floorBandIsDrawnUnderTheStageBranches() {
        val src = mainActivitySource()
        val band = src.indexOf("StageFloorBand(")
        assertTrue("the stage draws its floor band", band >= 0)
        assertTrue(
            "the band precedes the ceremony branch (it is the stage's first child)",
            band < src.indexOf("CeremonyStage("),
        )
        assertTrue(
            "the ceremony stands on the row's floor",
            src.contains("ceremony != null -> rowFloorDepth"),
        )
        assertTrue(
            "the celebration / outro row stands on the row's floor",
            src.contains("showCelebration || outroClapping -> rowFloorDepth"),
        )
    }

    @Test
    fun everyStageFigureStandsOnTheSharedFloor() {
        val src = mainActivitySource()
        assertTrue(
            "the solo dancer's reflection fills the stage's solo floor exactly",
            src.contains("reflectionDepthDp = soloFloorDepthDp.dp"),
        )
        assertTrue(
            "the ceremony keeper's reflection fills the ceremony's row floor",
            src.contains("reflectionDepthDp = rowFloorDepth.dp"),
        )
        // The celebration row grows its own floor strip.
        val rowStart = src.indexOf("private fun CelebrationRow(")
        assertTrue(rowStart >= 0)
        val rowEnd = src.indexOf("\n    }\n", rowStart)
        val rowBody = src.substring(rowStart, rowEnd)
        assertTrue(
            rowBody.contains("StageFloor.reflectionDepthDp") &&
                rowBody.contains("heightDp + floorDepth"),
        )
    }
}
