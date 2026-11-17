package com.dannycom888.shieldkaraoke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * Stage dressing (v1.0.57): the approved mock
 * (stage-mock/stage_mock_v1.png) made real — two soft light
 * beams from the stage's top corners and a mirrored reflection
 * of every figure under its own feet (the star field that
 * completes the mock lives with the bubbles in [Stars]).
 *
 * v1.0.59: the mock's floor BOARD is gone again — the user
 * ruled the band's highlight line and its lighter fill both
 * out ("地板那条白线不要……淡带也不要"), so the stage bottom
 * shows no floor shape at all; only the reflections remain,
 * anchored at the same foot baselines and fading downward.
 * The solo dancer also takes her full pre-.57 size back (see
 * [StageFloor.soloReflectionDepthDp]): the reflection yields
 * room, never the figure.
 *
 * v1.0.60: the two corner fans become follow-spots — both
 * cones converge on the solo dancer's spotlight pool, in the
 * pool's own colour (see [StageBeams] / [PoolTarget]). And
 * the restored solo dancer sat a touch high for the
 * user's eye ("伴舞下移一些"), so the whole solo placement —
 * figure, spotlight pool and reflection as one unit — drops
 * by [StageFloor.SOLO_DROP_FRACTION] of the zone (see
 * [StageFloor.soloDropDp]). A pure translation: her size,
 * the step envelope and the reflection's depth law are all
 * exactly the .59 ones; only where the unit stands changes.
 *
 * Pure numbers live in [StageFloor] / [StageBeams] so unit
 * tests can pin them; the drawing is two small composables.
 */
object StageFloor {
    /** Reflection height per unit of figure height: a figure
     *  stands on the floor line and its mirror reaches this far
     *  below it (the mock's ratio). */
    const val REFLECTION_FRACTION = 0.35f

    /** Alpha of the reflection at the foot baseline (its
     *  strongest point), before the fade to nothing at the
     *  bottom of the reflection band. */
    const val REFLECTION_TOP_ALPHA = 0.55f

    /** How deep the reflection under a figure [figureHeightDp]
     *  tall reaches. */
    fun reflectionDepthDp(figureHeightDp: Float): Float =
        figureHeightDp * REFLECTION_FRACTION

    /**
     * A figure's height at design [fraction] of its zone — the
     * pre-floor law, restored in v1.0.59: the fraction measures
     * the WHOLE zone again (the solo stage's 0.80 / 0.72 at
     * rest, 0.95 / 0.88 stepped forward), and the reflection
     * below the feet takes whatever room is left (see
     * [soloReflectionDepthDp]) instead of taxing the figure.
     */
    fun figureHeightDp(zoneHeightDp: Float, fraction: Float): Float =
        fraction * zoneHeightDp

    /**
     * The solo stage's reflection depth for a figure
     * [figureHeightDp] tall standing in a zone [zoneHeightDp]
     * tall (v1.0.59 — the user: "你向上移放大", move her up and
     * make her bigger): the figure's own 35% while that fits
     * under her feet, compressed to exactly the room the figure
     * leaves when it does not. The REFLECTION yields, never the
     * figure. A pure function of the current figure height, so
     * the invite step — the figure growing toward the zone's
     * top — squeezes the reflection smoothly (no jump, no
     * moving baseline: the wrapper's bottom stays on the zone's
     * bottom edge throughout), and the resting depth the
     * ceremony keeper must match is this same function at the
     * resting height, so the handover lands on the identical
     * figure over the identical reflection.
     */
    fun soloReflectionDepthDp(zoneHeightDp: Float, figureHeightDp: Float): Float =
        minOf(
            reflectionDepthDp(figureHeightDp),
            (zoneHeightDp - figureHeightDp).coerceAtLeast(0f),
        )

    /** How far the solo placement drops, as a fraction of the
     *  zone's height (v1.0.60 — the user: "伴舞下移一些", move
     *  the dancer down a bit): a small, fixed share — enough
     *  to see, small enough to keep her clear of the control
     *  bar's business. Pinned by the tests; taste changes go
     *  through them. */
    const val SOLO_DROP_FRACTION = 0.05f

    /**
     * The solo dancer's downward shift in a zone
     * [zoneHeightDp] tall (v1.0.60): the figure, her spotlight
     * pool and her reflection translate down together by this
     * much — one unit, so the mirror still starts exactly at
     * her feet and the step's growth still pivots on the same
     * (now lower) baseline. The playback dancer and the
     * ceremony keeper both take this drop from their own
     * zone's height; the two zones are the same height over
     * the same bottom edge (the stage box), so the handover
     * still lands on the identical placement. Her reflection's
     * faded tail now ends this far below the zone's bottom
     * edge — unclipped spill, the same mechanism the
     * celebration row's reflections have used since v1.0.59.
     */
    fun soloDropDp(zoneHeightDp: Float): Float =
        zoneHeightDp * SOLO_DROP_FRACTION

    /**
     * The solo pool's centre inside the dancer's zone, in dp
     * from the zone's top-left (v1.0.60) — where the stage
     * beams aim. Composed from the placement law itself: the
     * wrapper is bottom-anchored in the zone and translated
     * down by [dropDp] (the v1.0.60 drop), shifted left by
     * [overflowDp] when the stepped-forward figure outgrows
     * the zone (the v1.0.28 guard's applied shift, ≥ 0), and
     * inside the wrapper the pool rides at
     * [SpotlightGeom.poolCentreInFigureBoxDp] under a figure
     * [figureHeightDp] tall with [depthDp] of reflection
     * reserved below it. The wrapper's centre column never
     * moves horizontally (the figure box is centred in the
     * wrapper, the pool centred in the box), so x is simply
     * the zone's middle minus the guard's shift. Both beam
     * writers (the playback dancer and the ceremony keeper)
     * feed this function their own live numbers, so the aim
     * follows the placement — step, drop and all — instead of
     * a written-down coordinate.
     */
    fun poolCentreInZoneDp(
        zoneWidthDp: Float,
        zoneHeightDp: Float,
        figureHeightDp: Float,
        depthDp: Float,
        dropDp: Float,
        overflowDp: Float,
    ): Offset {
        val inBox = SpotlightGeom.poolCentreInFigureBoxDp(figureHeightDp)
        val wrapperTop = zoneHeightDp - (figureHeightDp + depthDp) + dropDp
        return Offset(zoneWidthDp / 2f - overflowDp, wrapperTop + inBox.y)
    }

    /**
     * The reflection flip: the figure stack (height H) is drawn
     * once more in a same-height node laid out just below the
     * figure, under a graphicsLayer with this Y scale and a
     * transform origin at [reflectionOriginFraction] of its
     * height. The pair is chosen so the stack's local [0, H]
     * maps onto [depth, 0] of that node — the feet (local H)
     * land exactly on the node's top edge (the foot baseline)
     * and the head reaches [depth] below it: a true mirror,
     * squashed to the reflection band, with the figure's own
     * pose / bounce / frame carried along because the very same
     * stack is drawn.
     */
    fun reflectionScaleY(figureHeightDp: Float, depthDp: Float): Float =
        -depthDp / figureHeightDp

    /** See [reflectionScaleY]: origin fraction of the stack
     *  height the flip pivots on. */
    fun reflectionOriginFraction(figureHeightDp: Float, depthDp: Float): Float =
        depthDp / (figureHeightDp + depthDp)

    /** Where the reflection's fade reaches zero, as a fraction
     *  of the stack node's height: the flipped figure occupies
     *  only the node's top [depthDp] (see [reflectionScaleY]),
     *  so the fade gradient — drawn over the whole node —
     *  bottoms out at this stop. */
    fun reflectionFadeStop(figureHeightDp: Float, depthDp: Float): Float =
        depthDp / figureHeightDp
}

/**
 * Where the beams aim (v1.0.60): the solo dancer's spotlight
 * pool — its centre and its horizontal radius, in root
 * (screen) pixels. Written by the two layouts that stand at
 * the solo placement (the playback dancer and the ceremony
 * keeper, both feeding [StageFloor.poolCentreInZoneDp] their
 * live geometry), read by [StageBeamBackdrop]. It persists
 * when neither is on stage (the celebration row, the
 * ceremony's group phases): the beams keep aiming at the
 * solo placement's pool, which is also where the keeper
 * stands when she is out. Null only until the first solo
 * placement has ever been laid out.
 */
data class PoolTarget(val centrePx: Offset, val radiusPx: Float)

object StageBeams {
    /** Where each lamp hangs: the source segment's centre, as
     *  a fraction of the stage width — the left lamp over the
     *  stage's left shoulder, the right one mirrored. The
     *  CONES are not mirrors of each other any more (v1.0.60):
     *  both converge on the one pool, which stands left of
     *  centre, so the right cone crosses the stage. */
    const val SOURCE_CENTRE_FRAC = 0.10f

    /** Half-width of the cone at the lamp, as a fraction of
     *  the stage width: a follow-spot leaves its lamp narrow
     *  and lands wide. */
    const val SOURCE_HALF_FRAC = 0.028f

    /** The cone's half-width where it lands, as a fraction of
     *  the pool's horizontal radius: the light disc sits a
     *  little inside the pool's own edge, so beam and pool
     *  read as one pool of light, brightest where they meet. */
    const val TARGET_RADIUS_RATIO = 0.85f

    /** Alpha at the cone's landing end — deliberately low: the
     *  beams are backdrop, and lyric legibility is pinned
     *  against this ceiling by the tests. The convergence
     *  brightening comes from the two cones overlapping at the
     *  pool and from the pool itself, not from hotter beams. */
    const val PEAK_ALPHA = 0.16f

    /** Alpha at the lamp end: the beam strengthens as it
     *  converges (the user: brighter near the pool, fading
     *  toward the source). */
    const val SOURCE_ALPHA = 0.05f

    /** The bright core cone's width, as a fraction of the
     *  outer cone's: each beam is drawn twice — a wide faint
     *  wash and a narrower core over it — which softens the
     *  cone's edges without any blur (the Shield is API 30;
     *  no RenderEffect). */
    const val CORE_WIDTH_RATIO = 0.55f

    /** The outer wash's share of the cone's gradient alphas. */
    const val WASH_ALPHA_RATIO = 0.45f

    /** The pool's own colour (v1.0.60): beam and pool are the
     *  same lamp, so the beam colour IS
     *  [SpotlightGeom.POOL_COLOR_ARGB], taken from the one
     *  constant — never restated here. */
    const val COLOR_ARGB = SpotlightGeom.POOL_COLOR_ARGB

    /** The lamp point of one cone on a stage [stageWidthPx]
     *  wide; [right] hangs the other lamp. */
    fun sourceCentre(stageWidthPx: Float, right: Boolean): Offset =
        Offset(
            (if (right) 1f - SOURCE_CENTRE_FRAC else SOURCE_CENTRE_FRAC) * stageWidthPx,
            0f,
        )

    /**
     * The four corners of one follow-spot cone (lamp-left,
     * lamp-right, landing-right, landing-left): from the
     * side's lamp segment at the stage's top edge, converging
     * on [targetCentre] — the pool's centre — and landing
     * [targetHalfWidthPx] wide around it. [widthRatio] scales
     * both ends for the core pass (see [CORE_WIDTH_RATIO]).
     * Both sides are fed the SAME target, so the two cones'
     * landing segments coincide exactly: the alignment is by
     * construction, and the tests pin it.
     */
    fun cone(
        stageWidthPx: Float,
        targetCentre: Offset,
        targetHalfWidthPx: Float,
        right: Boolean,
        widthRatio: Float = 1f,
    ): List<Offset> {
        val src = sourceCentre(stageWidthPx, right)
        val shw = SOURCE_HALF_FRAC * stageWidthPx * widthRatio
        val thw = targetHalfWidthPx * widthRatio
        return listOf(
            Offset(src.x - shw, 0f),
            Offset(src.x + shw, 0f),
            Offset(targetCentre.x + thw, targetCentre.y),
            Offset(targetCentre.x - thw, targetCentre.y),
        )
    }
}

/**
 * The two follow-spot beams (v1.0.57 as static fans from the
 * top corners; v1.0.60 re-aimed): drawn as the player stage's
 * bottom layer (the root composes this under the bubble
 * field, and every screen element draws over both). Each beam
 * is now a cone from its top lamp CONVERGING on the solo
 * dancer's spotlight pool ([target] — the pool's centre and
 * radius in screen pixels, kept current by the solo
 * placement's own layout): two stage follow-spots crossing
 * onto one pool of light, in the pool's own colour. The fill
 * is a linear gradient along the cone's axis — faint at the
 * lamp, strongest at the landing — drawn as a wide wash plus
 * a narrower core for soft edges without blur. Until the
 * first solo placement has been laid out there is no target
 * and the backdrop draws nothing.
 */
@Composable
fun StageBeamBackdrop(target: PoolTarget?, modifier: Modifier = Modifier) {
    val beamColor = Color(StageBeams.COLOR_ARGB)
    Canvas(modifier = modifier) {
        val t = target ?: return@Canvas
        val landingHalf = t.radiusPx * StageBeams.TARGET_RADIUS_RATIO
        for (right in listOf(false, true)) {
            val src = StageBeams.sourceCentre(size.width, right)
            for ((ratio, alphaScale) in listOf(
                1f to StageBeams.WASH_ALPHA_RATIO,
                StageBeams.CORE_WIDTH_RATIO to 1f,
            )) {
                val pts = StageBeams.cone(size.width, t.centrePx, landingHalf, right, ratio)
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to beamColor.copy(
                                alpha = StageBeams.SOURCE_ALPHA * alphaScale,
                            ),
                            1f to beamColor.copy(
                                alpha = StageBeams.PEAK_ALPHA * alphaScale,
                            ),
                        ),
                        start = src,
                        end = t.centrePx,
                    ),
                )
            }
        }
    }
}

