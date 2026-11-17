package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.geometry.Offset
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
 *
 * v1.0.60 amendments: the solo placement drops a fixed 5%
 * of the zone (the user: "伴舞下移一些") — a translation pin:
 * the amount, and that exactly the solo dancer and the
 * ceremony keeper take it, so the handover cannot step. And
 * the beams become follow-spots: the .57 fan pins are
 * replaced by convergence pins — both cones land exactly on
 * the pool centre the placement law computes, in the pool's
 * own colour, taken from the one shared constant.
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
    fun soloDancerStandsALittleLower() {
        // v1.0.60: the user sent the restored solo dancer down
        // a touch ("伴舞下移一些"). The drop is a fixed 5% of
        // the zone — inside the "a little" she asked for, and
        // a pure translation: it resizes nothing.
        assertEquals(0.05f, StageFloor.SOLO_DROP_FRACTION, 0.0001f)
        assertTrue(StageFloor.SOLO_DROP_FRACTION in 0.04f..0.06f)
        assertEquals(20f, StageFloor.soloDropDp(400f), 0.01f)
        assertEquals(0f, StageFloor.soloDropDp(0f), 0.001f)
        // A twentieth of the zone can never swallow the figure
        // it translates (her smallest design height is 0.72).
        assertTrue(
            StageFloor.soloDropDp(400f) <
                StageFloor.figureHeightDp(400f, 0.72f),
        )
    }

    @Test
    fun soloDropAppliesToSoloDancerAndKeeperAlike() {
        // Source pin: the drop is taken at exactly four call
        // sites — each of the two placements (the playback
        // dancer's DancerImage, the ceremony keeper's) takes
        // it twice: once as the visual offset itself, and once
        // inside its beam-aim evaluation
        // (StageFloor.poolCentreInZoneDp's drop argument).
        // All four read it off the placement's own zone height,
        // and the two zones are the same height over the same
        // bottom edge, so the keeper dissolves into the
        // identical lowered placement when the song starts:
        // no handover step, and the beams aim at the lowered
        // pool, not the pre-.60 one.
        val src = mainActivitySource()
        assertEquals(
            4,
            Regex("""StageFloor\.soloDropDp\(""").findAll(src).count(),
        )
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

    // ---- StageBeams (v1.0.60: follow-spots) ------------------

    @Test
    fun poolCentreInTheFigureBoxMatchesTheDrawnPool() {
        // The aim point IS the drawn pool's centre — the same
        // numbers spotlightPool draws around, restated as a
        // pure function. Figure 200 tall → box 156 wide, pool
        // ry = 156 × 1.2 × 0.28 = 52.416, centre =
        // (78, 200 × 0.98 + 52.416 × 0.35 = 214.3456).
        assertEquals(0.98f, SpotlightGeom.FEET_BASELINE_FRACTION, 0.0001f)
        val c = SpotlightGeom.poolCentreInFigureBoxDp(200f)
        assertEquals(78f, c.x, 0.01f)
        assertEquals(214.3456f, c.y, 0.01f)
        // The centre sits BELOW the figure box's bottom edge:
        // the pool is a floor ellipse under the feet.
        assertTrue("pool centre below the feet: ${c.y}", c.y > 200f)
        // The draw site uses the shared fraction, not a
        // restated literal — one number, two readers.
        assertTrue(
            "spotlightPool draws around the shared baseline fraction",
            spotlightSource().contains("SpotlightGeom.FEET_BASELINE_FRACTION"),
        )
    }

    @Test
    fun beamsAimWhereTheSoloPlacementPutsThePool() {
        // A resting solo placement worked by hand: zone
        // 360 × 400, figure 320 (0.80 of the zone), yielded
        // reflection depth 80, the v1.0.60 drop 20, no guard
        // shift. Wrapper top = 400 − (320 + 80) + 20 = 20;
        // the pool centre inside the figure box is
        // (124.8, 342.953), so in the zone it is
        // (180, 362.953) — the point the beams must hit.
        val centre = StageFloor.poolCentreInZoneDp(
            zoneWidthDp = 360f, zoneHeightDp = 400f,
            figureHeightDp = 320f, depthDp = 80f,
            dropDp = 20f, overflowDp = 0f,
        )
        assertEquals(180f, centre.x, 0.01f)
        assertEquals(362.953f, centre.y, 0.01f)
        // The guard's leftward shift moves the aim with the
        // figure; removing the drop raises it by exactly the
        // drop — the aim follows the placement, always.
        val shifted = StageFloor.poolCentreInZoneDp(360f, 400f, 320f, 80f, 20f, 10f)
        assertEquals(170f, shifted.x, 0.01f)
        assertEquals(centre.y, shifted.y, 0.01f)
        val undropped = StageFloor.poolCentreInZoneDp(360f, 400f, 320f, 80f, 0f, 0f)
        assertEquals(centre.y - 20f, undropped.y, 0.01f)
        // Source pin: exactly two writers evaluate the law in
        // MainActivity — the solo dancer's layout and the
        // ceremony keeper's — and the root feeds their state
        // to the backdrop.
        val src = mainActivitySource()
        assertEquals(
            2,
            Regex("""StageFloor\.poolCentreInZoneDp\(""").findAll(src).count(),
        )
        assertTrue(
            "the backdrop is fed the solo pool target",
            src.contains("StageBeamBackdrop(soloPoolTarget,"),
        )
    }

    @Test
    fun bothBeamsConvergeExactlyOnThePoolCentre() {
        // Whatever centre they are fed, both cones' landing
        // segments are centred exactly on it and coincide with
        // each other — the two follow-spots land on ONE pool.
        // (The .57 mirror pin is retired with the fans: with
        // an off-centre pool the cones are not mirrors, they
        // are two lamps on one target.)
        val w = 1600f
        val target = Offset(300f, 900f)
        val half = 100f
        val left = StageBeams.cone(w, target, half, right = false)
        val right = StageBeams.cone(w, target, half, right = true)
        assertEquals(4, left.size)
        assertEquals(4, right.size)
        for (cone in listOf(left, right)) {
            assertEquals(target.x, (cone[2].x + cone[3].x) / 2f, 0.01f)
            assertEquals(target.y, (cone[2].y + cone[3].y) / 2f, 0.01f)
        }
        assertEquals(left[2], right[2])
        assertEquals(left[3], right[3])
        // Lamps: at the very top, each on its own side.
        for (cone in listOf(left, right)) {
            assertTrue(cone[0].y == 0f && cone[1].y == 0f)
        }
        assertTrue((left[0].x + left[1].x) / 2f < w / 2f)
        assertTrue((right[0].x + right[1].x) / 2f > w / 2f)
        // A follow-spot cone widens from lamp to landing.
        for (cone in listOf(left, right)) {
            val lampWidth = kotlin.math.abs(cone[1].x - cone[0].x)
            val landWidth = kotlin.math.abs(cone[2].x - cone[3].x)
            assertTrue("cone widens: $lampWidth → $landWidth", landWidth > lampWidth)
        }
        // The core pass is the same cone, narrower at both
        // ends, still centred on the pool.
        val core = StageBeams.cone(
            w, target, half, right = false,
            widthRatio = StageBeams.CORE_WIDTH_RATIO,
        )
        assertTrue(StageBeams.CORE_WIDTH_RATIO in 0.3f..0.8f)
        assertEquals(target.x, (core[2].x + core[3].x) / 2f, 0.01f)
        assertTrue(
            kotlin.math.abs(core[2].x - core[3].x) <
                kotlin.math.abs(left[2].x - left[3].x),
        )
    }

    @Test
    fun beamAndPoolAreTheSameLampColour() {
        // One colour constant for the pool and the beams that
        // converge on it — the warm white-gold the pool has
        // always been (#FFF6D8), never restated per element.
        assertEquals(0xFFFFF6D8L, SpotlightGeom.POOL_COLOR_ARGB)
        assertEquals(SpotlightGeom.POOL_COLOR_ARGB, StageBeams.COLOR_ARGB)
    }

    @Test
    fun beamsStayBackdropQuiet() {
        // The legibility ceiling: a beam core brighter than
        // this would start washing the lyrics it sits under.
        assertTrue(StageBeams.PEAK_ALPHA > 0f && StageBeams.PEAK_ALPHA <= 0.20f)
        // The cone strengthens toward the pool, never the
        // other way: faintest at the lamp.
        assertTrue(
            StageBeams.SOURCE_ALPHA > 0f &&
                StageBeams.SOURCE_ALPHA < StageBeams.PEAK_ALPHA,
        )
        // The landing disc stays inside the pool's own edge.
        assertTrue(StageBeams.TARGET_RADIUS_RATIO in 0.5f..1f)
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
        // yielding-depth formula — that is what makes the
        // handover land on the identical figure over the
        // identical reflection. Three call sites in
        // MainActivity: the playback dancer's depth, the
        // keeper's DancerImage depth, and the keeper's beam-aim
        // publisher (v1.0.60), which reads the same resting
        // depth to aim the beams at her pool.
        assertEquals(
            3,
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

    private fun spotlightSource(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/Spotlight.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/Spotlight.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("Spotlight.kt not found; tried ${candidates.map { it.absolutePath }}")
        return file.readText()
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
