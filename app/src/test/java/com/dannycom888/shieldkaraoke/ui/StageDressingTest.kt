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
 *
 * v1.0.59 amendments (the user's rulings on the .57 stage):
 * the floor band is GONE (line and fill), the solo figure
 * takes her full design fraction back and the reflection
 * yields room instead, and the five-figure row stands on the
 * stage's bottom edge again with its reflections drawn below
 * the feet. The .57 pins that encoded the taxed figure and
 * the lifted row are replaced by the ones below.
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
    fun figureHeightIsTheFullDesignFractionAgain() {
        // v1.0.59: the fraction measures the whole zone again —
        // 0.80 of 400 is the full 320 the pre-.57 stage drew
        // (the .57 floor tax had solved it down to 250).
        assertEquals(320f, StageFloor.figureHeightDp(400f, 0.80f), 0.01f)
        assertEquals(288f, StageFloor.figureHeightDp(400f, 0.72f), 0.01f)
        assertEquals(380f, StageFloor.figureHeightDp(400f, 0.95f), 0.01f)
    }

    @Test
    fun soloReflectionYieldsRoomNeverTheFigure() {
        // Rest, full size: figure 320 in a 400 zone — her full
        // 35% mirror (112) does not fit, so the REFLECTION is
        // compressed to exactly the room left (80) and figure +
        // reflection fill the zone to the edge.
        val depth = StageFloor.soloReflectionDepthDp(400f, 320f)
        assertEquals(80f, depth, 0.01f)
        assertEquals(400f, 320f + depth, 0.01f)
        // Compact rest: the full 35% fits (288 × 0.35 = 100.8 ≤
        // the 112 left), so nothing is compressed away.
        assertEquals(
            100.8f,
            StageFloor.soloReflectionDepthDp(400f, 288f),
            0.01f,
        )
        // Stepped fully forward the figure nearly fills the
        // zone and the mirror is squeezed to the last 20.
        assertEquals(20f, StageFloor.soloReflectionDepthDp(400f, 380f), 0.01f)
        // Degenerate heights: no room, no reflection — never a
        // negative depth pushing the figure around.
        assertEquals(0f, StageFloor.soloReflectionDepthDp(400f, 400f), 0.001f)
        assertEquals(0f, StageFloor.soloReflectionDepthDp(400f, 460f), 0.001f)
        // Across the whole step envelope, figure + reflection
        // stay inside the zone.
        for (zone in listOf(257f, 440f, 900f)) {
            var f = 0.60f
            while (f <= 0.951f) {
                val h = StageFloor.figureHeightDp(zone, f)
                val d = StageFloor.soloReflectionDepthDp(zone, h)
                assertTrue("figure + reflection must fit: $h + $d <= $zone", h + d <= zone + 0.05f)
                assertTrue("depth never negative", d >= 0f)
                f += 0.05f
            }
        }
    }

    @Test
    fun stepForwardKeepsItsPreFloorGrowthRatio() {
        // The figure's own growth across the intro step is once
        // more exactly the design ratio — nothing is skimmed
        // off either end of the envelope for the reflection.
        for ((rest, step) in listOf(0.80f to 0.95f, 0.72f to 0.88f)) {
            val base = StageFloor.figureHeightDp(440f, rest)
            val big = StageFloor.figureHeightDp(440f, step)
            assertEquals(step / rest, big / base, 0.001f)
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
    fun noFloorShapeIsDrawnAnyMore() {
        // v1.0.59: the user ruled the floor band's highlight
        // line AND its lighter fill out — no composable, no
        // constants, no call site may come back.
        val src = mainActivitySource()
        assertTrue(
            "the stage must not draw a floor band",
            !src.contains("StageFloorBand"),
        )
        val dressing = stageDressingSource()
        assertTrue(
            "the band composable and its colours are retired",
            !dressing.contains("StageFloorBand") &&
                !dressing.contains("BAND_TOP_ARGB") &&
                !dressing.contains("LINE_ARGB"),
        )
    }

    @Test
    fun figuresKeepTheirReflectionsOnTheNewGeometry() {
        val src = mainActivitySource()
        assertTrue(
            "the solo dancer's reflection is the depth her height leaves room for",
            src.contains("reflectionDepthDp = soloFloorDepthDp.dp"),
        )
        // The solo stage and the ceremony keeper share the one
        // yielding-depth formula (two call sites in MainActivity:
        // the playback dancer's, and the keeper's) — that is
        // what makes the handover land on the identical figure
        // over the identical reflection.
        assertEquals(
            2,
            Regex("""StageFloor\.soloReflectionDepthDp\(""").findAll(src).count(),
        )
        assertTrue(
            "the keeper takes the full design fraction like the solo dancer",
            src.contains("StageFloor.figureHeightDp("),
        )
        // The row's four DancerImage call sites (the celebration
        // / outro row and the ceremony's three row branches)
        // let their reflections overflow below the feet.
        assertEquals(
            4,
            Regex("""reflectionOverflow = true""").findAll(src).count(),
        )
        // The row container is the pre-.57 box again: figure
        // height only, no grown floor strip inside it.
        val rowStart = src.indexOf("private fun CelebrationRow(")
        assertTrue(rowStart >= 0)
        val rowEnd = src.indexOf("\n    }\n", rowStart)
        val rowBody = src.substring(rowStart, rowEnd)
        assertTrue(
            "the row container is back to figure height",
            rowBody.contains(".height(heightDp)"),
        )
        assertTrue(
            "no floor depth grows inside the row any more",
            !rowBody.contains("floorDepth"),
        )
    }

    private fun stageDressingSource(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/StageDressing.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/StageDressing.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("StageDressing.kt not found; tried ${candidates.map { it.absolutePath }}")
        return file.readText()
    }
}
