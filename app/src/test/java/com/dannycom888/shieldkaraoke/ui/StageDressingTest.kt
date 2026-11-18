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
 *
 * v1.0.61 amendments (the user's photo of the .60 stage:
 * "感觉人在半空跳舞", and "聚光灯交集处再淡一些"): the cones
 * gain a fade tail — the peak still lands exactly on the
 * pool centre, but the light is fully gone within one pool
 * radius past it; the intersection is dimmed (beam peak
 * 0.16 → 0.10, the solo pools scaled to an effective 0.42
 * centre while the shared pool centre stays 0.50 for the
 * row); the solo reflection's top alpha gets a floor (0.75,
 * solo + keeper only); and the contact shadow's layer order
 * (pool → shadow → figure) is pinned in
 * DancerImageStructureTest.
 *
 * v1.0.63 amendments (the user, on the .61 stage: "灯光照到
 * 光圈范围就淡化，不照下去了，地上就一光圈"): the fade tail
 * is retired — measured, its parallelogram's far corner
 * swung below the pool, outside the ellipse, at near-peak
 * alpha, so no tail length could keep beam light off the
 * ground past the pool. The cone is the plain cone again,
 * ending at its landing segment on the pool centre, and the
 * gradient ends the light instead: peak where the axis
 * crosses INTO the pool ellipse, zero AT the centre. The
 * guarantee — outside the pool, on the ground, the beam
 * alpha is identically zero — is pinned by a sampled proof
 * over the same primitives the backdrop draws with.
 *
 * v1.0.64 amendments (the user, on the .63 stage: "聚光灯
 * 不够柔和", then, verbatim, "聚光到光圈边缘就消失"): the
 * zero point moves from the pool CENTRE to the pool EDGE —
 * the axial profile peaks at 70% of the run to the entry
 * and rolls off to exactly zero AT the entry, and a
 * pool-edge mask (DstIn, in the pool's own ellipse space)
 * makes "inside the pool, no beam light at all" exact, so
 * the ground's only light is the pool's own gradient and
 * the seam at the rim has no step. The wash/core two-pass
 * draw is retired for a sixteen-shell lateral feather (no
 * step edges), and the sampled proof widens: inside the
 * ellipse zero, outside on the ground zero, boundary
 * crossings continuous, side profiles monotone to zero.
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
        // v1.0.64: the feather shells are this same cone at
        // decreasing width ratios — the innermost still
        // centred on the pool, the outermost the cone
        // itself — and their alpha shares telescope to the
        // whole lateral profile (they sum to 1).
        val shells = StageBeams.lateralShells()
        assertEquals(StageBeams.FEATHER_SHELLS, shells.size)
        assertEquals(1f, shells.last().first, 0.0001f)
        var ratioSum = 0f
        for ((ratio, share) in shells) {
            assertTrue("shell ratio in (0, 1]: $ratio", ratio > 0f && ratio <= 1f)
            assertTrue("shell share never negative: $share", share >= 0f)
            ratioSum += share
        }
        assertEquals("shell shares telescope to 1", 1f, ratioSum, 0.001f)
        val inner = StageBeams.cone(
            w, target, half, right = false,
            widthRatio = shells.first().first,
        )
        assertEquals(target.x, (inner[2].x + inner[3].x) / 2f, 0.01f)
        assertTrue(
            kotlin.math.abs(inner[2].x - inner[3].x) <
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
        // v1.0.61: the ceiling itself came down — at the old
        // 0.16 the two cones' overlap on the pool was a
        // glaring white blob (the user: "聚光灯交集处再淡一些"),
        // so the peak is now 0.10 and the pin's ceiling is
        // 0.12: the intersection may only read a little
        // brighter than the beam itself.
        assertEquals(0.10f, StageBeams.PEAK_ALPHA, 0.0001f)
        assertTrue(StageBeams.PEAK_ALPHA > 0f && StageBeams.PEAK_ALPHA <= 0.12f)
        // The cone strengthens toward the pool, never the
        // other way: faintest at the lamp.
        assertTrue(
            StageBeams.SOURCE_ALPHA > 0f &&
                StageBeams.SOURCE_ALPHA < StageBeams.PEAK_ALPHA,
        )
        // The landing disc stays inside the pool's own edge.
        assertTrue(StageBeams.TARGET_RADIUS_RATIO in 0.5f..1f)
        // v1.0.64: the drawn gradient's stops honour the same
        // ceiling — the brightest stop IS the peak (the peak
        // point is one of the stops), no stop exceeds it, the
        // lamp end is the source alpha and both ends of the
        // run (the pool entry, the centre) sit at zero.
        for (entry in listOf(0.55f, 0.8f, 0.9379f, 0.99f)) {
            val stops = StageBeams.gradientStops(entry)
            assertEquals(0f, stops.first().first, 0.0001f)
            assertEquals(
                StageBeams.SOURCE_ALPHA, stops.first().second, 0.0001f,
            )
            assertEquals(1f, stops.last().first, 0.0001f)
            assertEquals(0f, stops.last().second, 0.0001f)
            assertEquals(
                "the peak is a stop, at the ceiling",
                StageBeams.PEAK_ALPHA,
                stops.maxOf { it.second },
                0.0001f,
            )
            assertTrue(stops.all { it.second <= StageBeams.PEAK_ALPHA + 0.0001f })
            assertEquals(
                "zero at the pool entry",
                0f,
                StageBeams.sampleStops(stops, entry),
                0.0001f,
            )
        }
    }

    @Test
    fun beamLightRollsOffToZeroAtThePoolBoundary() {
        // v1.0.64, the user's sentence as pins: "聚光到光圈
        // 边缘就消失" — the light is gone AT the pool's edge.
        // The machinery that put the peak at the entry (the
        // .63 stop law) and the two-pass wash/core draw are
        // retired: none of their names may come back into
        // the dressing source.
        val dressing = stageDressingSource()
        for (retired in listOf(
            "FADE_PER_RADIUS", "coneWithFade", "fadeEnd", "fadeLengthPx",
            "WASH_ALPHA_RATIO", "CORE_WIDTH_RATIO", "peakStop",
        )) {
            assertTrue("retired: $retired", !dressing.contains(retired))
        }
        // Source pins: the backdrop builds each beam from
        // the plain cone (one call, inside the shell loop),
        // ends it through the one entry law (one call),
        // samples its gradient from the one profile (one
        // call), feathers it from the one shell table (one
        // call), and seals the pool with the one mask stop
        // table (one call) under DstIn in its own offscreen
        // layer.
        assertEquals(
            1,
            Regex("""StageBeams\.cone\(""").findAll(dressing).count(),
        )
        assertEquals(
            1,
            Regex("""StageBeams\.entryStop\(""").findAll(dressing).count(),
        )
        assertEquals(
            1,
            Regex("""StageBeams\.gradientStops\(""").findAll(dressing).count(),
        )
        assertEquals(
            1,
            Regex("""StageBeams\.lateralShells\(""").findAll(dressing).count(),
        )
        assertEquals(
            1,
            Regex("""StageBeams\.poolEdgeMaskStops\(""").findAll(dressing).count(),
        )
        assertTrue("the seam is a DstIn mask", dressing.contains("BlendMode.DstIn"))
        assertTrue(
            "confined to the backdrop's own layer",
            dressing.contains("CompositingStrategy.Offscreen"),
        )
        // The boundary distance along a direction: the full
        // radius horizontally, the squashed radius vertically,
        // always between the two — and the boundary point it
        // names is ON the pool ellipse, for every direction.
        assertEquals(
            200f,
            StageBeams.poolBoundaryDistancePx(Offset(1f, 0f), 200f),
            0.01f,
        )
        assertEquals(
            200f * SpotlightGeom.FLATTEN,
            StageBeams.poolBoundaryDistancePx(Offset(0f, 1f), 200f),
            0.01f,
        )
        var ang = 0f
        while (ang < 6.29f) {
            val d = Offset(kotlin.math.cos(ang), kotlin.math.sin(ang))
            val dist = StageBeams.poolBoundaryDistancePx(d, 200f)
            assertTrue(
                "boundary distance between the squashed and full radius: $dist",
                dist >= 200f * SpotlightGeom.FLATTEN - 0.01f && dist <= 200f + 0.01f,
            )
            val bx = d.x * dist / 200f
            val by = d.y * dist / (200f * SpotlightGeom.FLATTEN)
            assertEquals("boundary point on the ellipse", 1f, bx * bx + by * by, 0.002f)
            ang += 0.1f
        }
        // Per lamp, per geometry: the entry stop sits exactly
        // where the axis crosses INTO the pool — the entry
        // point is on the ellipse, entryStop × axis == axis −
        // boundary distance — and the .64 law holds there:
        // the beam is GONE at the entry (the .63 pin had its
        // peak here), gone at the centre, peaked earlier.
        for ((w, centre, r) in beamGeometries()) {
            for (right in listOf(false, true)) {
                val src = StageBeams.sourceCentre(w, right)
                val dir = StageBeams.axisDirection(src, centre)
                val dist = StageBeams.poolBoundaryDistancePx(dir, r)
                val entry = Offset(centre.x - dir.x * dist, centre.y - dir.y * dist)
                val ex = (entry.x - centre.x) / r
                val ey = (entry.y - centre.y) / (r * SpotlightGeom.FLATTEN)
                assertEquals("entry point on the pool ellipse", 1f, ex * ex + ey * ey, 0.002f)
                val axis = (centre - src).getDistance()
                val stop = StageBeams.entryStop(src, centre, r)
                assertTrue("entry stop is a fraction: $stop", stop in 0f..1f)
                assertEquals(axis - dist, stop * axis, 0.05f)
                assertEquals(
                    "the beam is gone at the pool's edge",
                    0f,
                    beamAlphaAt(entry, w, centre, r),
                    0.0001f,
                )
                assertEquals(
                    "and at the pool's centre",
                    0f,
                    beamAlphaAt(centre, w, centre, r),
                    0.0001f,
                )
                // The peak stands at PEAK_ENTRY_FRACTION of
                // the run to the entry: the profile itself
                // reaches the full peak there, and the lamp's
                // composited light comes within a hair of it
                // (the feather shells stack source-over — a
                // few percent of stacking discount).
                val peakF = stop * StageBeams.PEAK_ENTRY_FRACTION
                val peakPoint = Offset(
                    src.x + (centre.x - src.x) * peakF,
                    src.y + (centre.y - src.y) * peakF,
                )
                assertEquals(
                    "the profile peaks before the entry",
                    StageBeams.PEAK_ALPHA,
                    StageBeams.sampleStops(StageBeams.gradientStops(stop), peakF),
                    0.001f,
                )
                val peakLamp = beamLampAlpha(peakPoint, w, centre, r, right)
                assertTrue(
                    "the lamp's light at its peak point: $peakLamp",
                    peakLamp in 0.09f..StageBeams.PEAK_ALPHA,
                )
                // The roll-off from the peak to the entry is
                // monotone — a smooth landing, no shelf.
                var prev = Float.MAX_VALUE
                var f = peakF
                while (f <= stop + 0.0001f) {
                    val p = Offset(
                        src.x + (centre.x - src.x) * f,
                        src.y + (centre.y - src.y) * f,
                    )
                    val a = beamLampAlpha(p, w, centre, r, right)
                    assertTrue(
                        "roll-off monotone at f=$f: $a <= $prev",
                        a <= prev + 0.0005f,
                    )
                    prev = a
                    f += (stop - peakF) / 16f
                }
                // Mid-flight the beam is lit, between source
                // and peak: it still reads as a follow-spot.
                val mid = Offset(
                    src.x + (centre.x - src.x) * 0.5f,
                    src.y + (centre.y - src.y) * 0.5f,
                )
                val midAlpha = beamLampAlpha(mid, w, centre, r, right)
                assertTrue(
                    "mid-flight alpha between source and peak: $midAlpha",
                    midAlpha > StageBeams.SOURCE_ALPHA && midAlpha < peakLamp,
                )
                // Boundary continuity on the axis: stepping
                // toward the entry in shrinking strides, the
                // lamp's own light (through the mask) dies
                // smoothly — and the COMBINED light a breath
                // before the rim is already a whisper, so
                // beam and pool meet with no step.
                var prevSeam = Float.MAX_VALUE
                for (backPx in listOf(48f, 24f, 12f, 6f, 3f, 1f, 0f)) {
                    val p = Offset(
                        entry.x - dir.x * backPx,
                        entry.y - dir.y * backPx,
                    )
                    val nx = (p.x - centre.x) / r
                    val ny = (p.y - centre.y) / (r * SpotlightGeom.FLATTEN)
                    val rho = kotlin.math.sqrt(nx * nx + ny * ny)
                    val seam = beamLampAlpha(p, w, centre, r, right) *
                        StageBeams.poolEdgeFactorAt(rho)
                    assertTrue(
                        "the seam dies smoothly at ${backPx}px out: $seam",
                        seam <= prevSeam + 0.001f,
                    )
                    prevSeam = seam
                    val combined = beamAlphaAt(p, w, centre, r)
                    if (backPx == 3f) {
                        assertTrue(
                            "combined light 3px before the rim is soft: $combined",
                            combined <= 0.02f,
                        )
                    }
                    if (backPx == 1f) {
                        assertTrue(
                            "combined light 1px before the rim is a whisper: $combined",
                            combined <= 0.01f,
                        )
                    }
                    if (backPx == 0f) {
                        assertEquals(
                            "combined light at the rim is zero",
                            0f, combined, 0.0001f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun beamMeetsThePoolWithoutAStepAtAnyAngle() {
        // Across the rim at sampled angles: inside is
        // exactly zero (the mask is shut), and just outside
        // the light is already a whisper — no brightness
        // step at the seam, in any direction.
        for ((w, centre, r) in beamGeometries()) {
            val ry = r * SpotlightGeom.FLATTEN
            var ang = 0.0
            while (ang < 6.29) {
                val dx = kotlin.math.cos(ang).toFloat()
                val dy = kotlin.math.sin(ang).toFloat()
                fun at(rho: Float) = beamAlphaAt(
                    Offset(centre.x + dx * rho * r, centre.y + dy * rho * ry),
                    w, centre, r,
                )
                assertEquals("inside the rim is dark at $ang", 0f, at(0.9f), 0f)
                assertEquals("just inside the rim is dark at $ang", 0f, at(0.99f), 0f)
                assertTrue(
                    "just outside the rim is a whisper at $ang: ${at(1.01f)}",
                    at(1.01f) <= 0.01f,
                )
                assertTrue(
                    "a breath outside the rim stays soft at $ang: ${at(1.05f)}",
                    at(1.05f) <= 0.02f,
                )
                ang += 0.35
            }
        }
    }

    @Test
    fun beamSidesFeatherToZeroWithoutSteps() {
        // The lateral profile (the ideal curve): full on the
        // axis, zero at and beyond the side edge, monotone
        // in between; the shells' staircase tracks it with a
        // residue under a hundredth at the very edge.
        var prev = Float.MAX_VALUE
        var s = 0f
        while (s <= 1.001f) {
            val v = StageBeams.lateralProfile(s)
            assertTrue("lateral profile monotone at $s", v <= prev + 0.0005f)
            prev = v
            s += 0.02f
        }
        assertEquals(1f, StageBeams.lateralProfile(0f), 0.0001f)
        assertEquals(0f, StageBeams.lateralProfile(1f), 0.0001f)
        assertEquals(0f, StageBeams.lateralProfile(1.5f), 0.0001f)
        assertTrue(
            "edge residue is a whisper",
            StageBeams.lateralProfile(
                (StageBeams.FEATHER_SHELLS - 1).toFloat() / StageBeams.FEATHER_SHELLS,
            ) <= 0.01f,
        )
        // Cross-sections of the drawn beam (the full model)
        // along the perpendicular at two depths of the run,
        // both sides: from the axis outward the light never
        // grows, neighbouring samples never jump by more
        // than a hundredth of alpha (no step edge), and past
        // the cone's side it is zero and stays zero. The
        // sampling is fine enough to isolate single shell
        // boundaries — one boundary's jump is bounded by the
        // axial alpha times the largest shell share
        // (≈ 0.10 × 0.098), which is what "no step" means
        // for a staircase feather.
        for ((w, centre, r) in beamGeometries()) {
            for (right in listOf(false, true)) {
                val src = StageBeams.sourceCentre(w, right)
                val dir = StageBeams.axisDirection(src, centre)
                val perp = Offset(-dir.y, dir.x)
                val entry = StageBeams.entryStop(src, centre, r)
                for (f0 in listOf(entry * 0.5f, entry * 0.85f)) {
                    val axisPt = Offset(
                        src.x + (centre.x - src.x) * f0,
                        src.y + (centre.y - src.y) * f0,
                    )
                    val tMax = r * StageBeams.TARGET_RADIUS_RATIO * 2f
                    for (sign in listOf(1f, -1f)) {
                        var prevA = Float.MAX_VALUE
                        var prevStep: Float? = null
                        var sawZero = false
                        var t = 0f
                        while (t <= tMax) {
                            val p = Offset(
                                axisPt.x + perp.x * t * sign,
                                axisPt.y + perp.y * t * sign,
                            )
                            val a = beamLampAlpha(p, w, centre, r, right)
                            assertTrue(
                                "feather monotone at t=$t f=$f0 sign=$sign: $a",
                                a <= prevA + 0.001f,
                            )
                            val ps = prevStep
                            if (ps != null) {
                                assertTrue(
                                    "no step across the beam at t=$t f=$f0: |$a - $ps|",
                                    kotlin.math.abs(a - ps) <= 0.012f,
                                )
                            }
                            if (sawZero) {
                                assertEquals(
                                    "stays zero past the side edge",
                                    0f, a, 0f,
                                )
                            }
                            if (a == 0f) sawZero = true
                            prevA = a
                            prevStep = a
                            t += tMax / 240f
                        }
                        assertTrue(
                            "the feather reaches zero within the sampled width",
                            sawZero,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun poolEdgeMaskIsShutInsideAndOpensSmoothly() {
        val band = StageBeams.POOL_EDGE_FADE_BAND
        assertEquals(0f, StageBeams.poolEdgeFactorAt(0f), 0.0001f)
        assertEquals(0f, StageBeams.poolEdgeFactorAt(0.5f), 0.0001f)
        assertEquals(0f, StageBeams.poolEdgeFactorAt(1f), 0.0001f)
        assertEquals(1f, StageBeams.poolEdgeFactorAt(1f + band), 0.0001f)
        assertEquals(1f, StageBeams.poolEdgeFactorAt(2.5f), 0.0001f)
        // The drawn stops track the ideal curve at the
        // sampled points and open monotonically — no shelf.
        assertEquals(
            StageBeams.poolEdgeFactor(1f + band / 2f),
            StageBeams.poolEdgeFactorAt(1f + band / 2f),
            0.02f,
        )
        var prev = 0f
        var rho = 1f
        while (rho <= 1f + band + 0.001f) {
            val v = StageBeams.poolEdgeFactorAt(rho)
            assertTrue("mask opens monotonically at rho=$rho", v >= prev - 0.0005f)
            prev = v
            rho += band / 20f
        }
        // The stops themselves: flat zero out to the rim's
        // own fraction of the mask radius, fully open at
        // the mask's edge, nothing out of range.
        val stops = StageBeams.poolEdgeMaskStops()
        assertEquals(0f, stops.first().second, 0.0001f)
        assertEquals(1f, stops.last().first, 0.0001f)
        assertEquals(1f, stops.last().second, 0.0001f)
        assertEquals(
            0f,
            StageBeams.sampleStops(stops, 1f / (1f + band)),
            0.0001f,
        )
        assertTrue(stops.all { it.second in 0f..1f })
    }

    @Test
    fun noBeamLightInsideThePoolOrOnTheGroundOutsideIt() {
        // v1.0.64 widens the .63 sampled proof ("地上就一
        // 光圈" plus the user's new "聚光到光圈边缘就消失"):
        // over a dense grid around the pool, both lamps and
        // all feather shells combined exactly as the
        // backdrop composites them, with the pool-edge mask
        // over the sum —
        //  - INSIDE the pool ellipse the beam alpha is
        //    identically zero: the ground's only light is
        //    the pool's own gradient;
        //  - outside the ellipse and at-or-below the centre
        //    line (the ground) it is zero too (.63's law);
        //  - below the pool's bottom edge, zero everywhere.
        for ((w, centre, r) in beamGeometries() + listOf(
            Triple(1920f, Offset(420f, 880f), 260f),
            Triple(1600f, Offset(700f, 700f), 137.5f),
            Triple(1600f, Offset(300f, 900f), 40f),
        )) {
            val ry = r * SpotlightGeom.FLATTEN
            val step = r / 25f
            var x = centre.x - 2.4f * r
            while (x <= centre.x + 2.4f * r) {
                var y = centre.y - 0.8f * r
                while (y <= centre.y + 2.4f * r) {
                    val p = Offset(x, y)
                    val nx = (x - centre.x) / r
                    val ny = (y - centre.y) / ry
                    val rho = kotlin.math.sqrt(nx * nx + ny * ny)
                    val a = beamAlphaAt(p, w, centre, r)
                    if (rho <= 0.998f) {
                        assertEquals(
                            "beam light inside the pool at $p " +
                                "(stage $w, centre $centre, r $r)",
                            0f, a, 0f,
                        )
                    }
                    if (rho > 1.002f && y >= centre.y) {
                        assertEquals(
                            "beam light on the ground outside the pool at $p " +
                                "(stage $w, centre $centre, r $r)",
                            0f, a, 0f,
                        )
                    }
                    if (y > centre.y + ry + 1f) {
                        assertEquals(
                            "beam light below the pool's bottom edge at $p " +
                                "(stage $w, centre $centre, r $r)",
                            0f, a, 0f,
                        )
                    }
                    y += step
                }
                x += step
            }
        }
    }

    /** Stage geometries (width, pool centre, pool radius)
     *  the beam pins are proven over: the TV stage with the
     *  pool left-of-centre and centred, and the user's phone. */
    private fun beamGeometries(): List<Triple<Float, Offset, Float>> = listOf(
        Triple(1600f, Offset(300f, 900f), 200f),
        Triple(1920f, Offset(960f, 950f), 300f),
        Triple(869f, Offset(190f, 330f), 110f),
    )

    /** Point-in-convex-polygon (the cone is convex; boundary
     *  counts as inside), by consistent cross-product sign. */
    private fun pointInConvex(poly: List<Offset>, p: Offset): Boolean {
        var sign = 0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val s = when {
                cross > 0f -> 1
                cross < 0f -> -1
                else -> 0
            }
            if (s != 0) {
                if (sign == 0) sign = s else if (s != sign) return false
            }
        }
        return true
    }

    /**
     * The alpha ONE lamp's feathered beam paints at [p],
     * modelled on the very primitives the backdrop draws
     * with: inside the plain [StageBeams.cone] polygon, the
     * axial gradient ([StageBeams.gradientStops] read
     * through [StageBeams.sampleStops]) stacked through the
     * [StageBeams.lateralShells] — each shell the cone at
     * its width ratio, carrying its share of the axial
     * alpha, composited source-over; outside the outer
     * cone, exactly zero.
     */
    private fun beamLampAlpha(
        p: Offset,
        stageWidth: Float,
        centre: Offset,
        radius: Float,
        right: Boolean,
    ): Float {
        val landingHalf = radius * StageBeams.TARGET_RADIUS_RATIO
        val outer = StageBeams.cone(stageWidth, centre, landingHalf, right, 1f)
        if (!pointInConvex(outer, p)) return 0f
        val src = StageBeams.sourceCentre(stageWidth, right)
        val ax = centre.x - src.x
        val ay = centre.y - src.y
        val len2 = ax * ax + ay * ay
        if (len2 <= 0f) return 0f
        val f = ((p.x - src.x) * ax + (p.y - src.y) * ay) / len2
        val stops = StageBeams.gradientStops(
            StageBeams.entryStop(src, centre, radius),
        )
        val axial = StageBeams.sampleStops(stops, f)
        if (axial <= 0f) return 0f
        var remaining = 1f
        for ((ratio, share) in StageBeams.lateralShells()) {
            val poly = StageBeams.cone(stageWidth, centre, landingHalf, right, ratio)
            if (pointInConvex(poly, p)) {
                remaining *= 1f - axial * share
            }
        }
        return 1f - remaining
    }

    /** The combined beam alpha at [p]: both lamps
     *  composited source-over exactly as the Canvas stacks
     *  them, then the pool-edge mask over the sum (the
     *  backdrop's DstIn) — zero inside the pool, zero
     *  outside the cones, the seam at the rim continuous. */
    private fun beamAlphaAt(
        p: Offset,
        stageWidth: Float,
        centre: Offset,
        radius: Float,
    ): Float {
        var remaining = 1f
        for (right in listOf(false, true)) {
            remaining *= 1f - beamLampAlpha(p, stageWidth, centre, radius, right)
        }
        val nx = (p.x - centre.x) / radius
        val ny = (p.y - centre.y) / (radius * SpotlightGeom.FLATTEN)
        val rho = kotlin.math.sqrt(nx * nx + ny * ny)
        return (1f - remaining) * StageBeams.poolEdgeFactorAt(rho)
    }

    @Test
    fun soloPoolsAreDimmedAtTheIntersectionOnly() {
        // v1.0.61: the shared pool centre does NOT move (the
        // row's pools keep it); the solo placement's pools —
        // where the two beams land — scale down to an
        // effective 0.42.
        assertEquals(0.50f, SpotlightGeom.CENTRE_ALPHA, 0.0001f)
        assertEquals(0.84f, SpotlightGeom.SOLO_POOL_ALPHA_SCALE, 0.0001f)
        assertTrue(SpotlightGeom.SOLO_POOL_ALPHA_SCALE in 0.8f..0.9f)
        assertEquals(
            0.42f,
            SpotlightGeom.CENTRE_ALPHA * SpotlightGeom.SOLO_POOL_ALPHA_SCALE,
            0.001f,
        )
        // Exactly the two solo-placement call sites (the
        // playback dancer, the ceremony keeper) take the
        // dimming, the contact shadow and the stronger
        // reflection top — the row takes none of them.
        val src = mainActivitySource()
        assertEquals(
            2,
            Regex("""spotlightAlphaScale\s*=\s*SpotlightGeom\.SOLO_POOL_ALPHA_SCALE""")
                .findAll(src).count(),
        )
        assertEquals(
            2,
            Regex("""contactShadow = true""").findAll(src).count(),
        )
        assertEquals(
            2,
            Regex("""reflectionTopAlpha\s*=\s*StageFloor\.SOLO_REFLECTION_TOP_ALPHA""")
                .findAll(src).count(),
        )
    }

    @Test
    fun soloReflectionStartsStrongerOverThePool() {
        // v1.0.61: the solo reflection's top — the stretch
        // that lies over the pool — has a floor: at the shared
        // 0.55 the light washed the mirror out and the feet
        // looked cut from it. The fade's shape is untouched
        // (the shared value and the fade stop law stand), only
        // the solo top is hotter.
        assertEquals(0.75f, StageFloor.SOLO_REFLECTION_TOP_ALPHA, 0.0001f)
        assertTrue(
            "the solo reflection top never drops below the readability floor",
            StageFloor.SOLO_REFLECTION_TOP_ALPHA >= 0.7f,
        )
        assertTrue(
            StageFloor.SOLO_REFLECTION_TOP_ALPHA > StageFloor.REFLECTION_TOP_ALPHA,
        )
        assertEquals(0.55f, StageFloor.REFLECTION_TOP_ALPHA, 0.0001f)
        // The default every other figure keeps is the shared
        // value, declared on the parameter itself.
        assertTrue(
            mainActivitySource().contains(
                "reflectionTopAlpha: Float = StageFloor.REFLECTION_TOP_ALPHA",
            ),
        )
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
