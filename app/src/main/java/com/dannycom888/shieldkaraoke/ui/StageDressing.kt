package com.dannycom888.shieldkaraoke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

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

object StageBeams {
    /** Beam outline as fractions of the stage size, read off
     *  the approved mock: a narrow mouth at the top corner
     *  fanning out as it falls to the floor. The right beam is
     *  the left one's mirror. */
    const val TOP_LEFT_FRAC = 0.07f
    const val TOP_RIGHT_FRAC = 0.19f
    const val BOTTOM_LEFT_FRAC = 0.21f
    const val BOTTOM_RIGHT_FRAC = 0.44f
    const val BOTTOM_Y_FRAC = 0.86f

    /** Alpha at the beam's bright core — deliberately low: the
     *  beams are backdrop, and lyric legibility is pinned
     *  against this ceiling by the tests. */
    const val PEAK_ALPHA = 0.16f

    /** By the floor the beam has mostly melted into the stage
     *  background (the mock's beams never reach the floor at
     *  full strength). */
    const val MELT_AT_BOTTOM = 0.55f

    /** Warm white, the spotlight pool's family. */
    const val COLOR_ARGB = 0xFFFFF3D0L

    /**
     * The four corners of one beam (top-left, top-right,
     * bottom-right, bottom-left) for a stage of
     * [widthPx] × [heightPx]; [right] mirrors the beam to the
     * other top corner.
     */
    fun polygon(widthPx: Float, heightPx: Float, right: Boolean): List<Offset> {
        fun x(frac: Float) = (if (right) 1f - frac else frac) * widthPx
        val bottomY = BOTTOM_Y_FRAC * heightPx
        return listOf(
            Offset(x(TOP_LEFT_FRAC), 0f),
            Offset(x(TOP_RIGHT_FRAC), 0f),
            Offset(x(BOTTOM_RIGHT_FRAC), bottomY),
            Offset(x(BOTTOM_LEFT_FRAC), bottomY),
        )
    }
}

/** The stage background the beams melt into — MainActivity's
 *  navy base, restated here because the melt overlay must match
 *  it exactly. */
private val STAGE_BG = Color(0xFF0D1B3E)

/**
 * The two light beams (v1.0.57): drawn ONCE, statically, as the
 * player stage's bottom layer (the root composes this under the
 * bubble field, and every screen element draws over both).
 * Each beam is a fanned polygon filled with a gradient across
 * its width (clear edges, warm core) and then melted downward
 * with a background-colour gradient clipped to the same path —
 * soft on every side without any blur (the Shield is API 30;
 * no RenderEffect). No state, no clock: a stage light rig does
 * not flicker, and stillness cannot fight the lyric sweep's
 * frame budget.
 */
@Composable
fun StageBeamBackdrop(modifier: Modifier = Modifier) {
    val beamColor = Color(StageBeams.COLOR_ARGB)
    Canvas(modifier = modifier) {
        for (right in listOf(false, true)) {
            val pts = StageBeams.polygon(size.width, size.height, right)
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                lineTo(pts[1].x, pts[1].y)
                lineTo(pts[2].x, pts[2].y)
                lineTo(pts[3].x, pts[3].y)
                close()
            }
            val minX = pts.minOf { it.x }
            val maxX = pts.maxOf { it.x }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to beamColor.copy(alpha = 0f),
                        0.5f to beamColor.copy(alpha = StageBeams.PEAK_ALPHA),
                        1f to beamColor.copy(alpha = 0f),
                    ),
                    startX = minX,
                    endX = maxX,
                ),
            )
            clipPath(path) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to STAGE_BG.copy(alpha = 0f),
                            1f to STAGE_BG.copy(alpha = StageBeams.MELT_AT_BOTTOM),
                        ),
                        startY = 0f,
                        endY = size.height * StageBeams.BOTTOM_Y_FRAC,
                    ),
                )
            }
        }
    }
}

