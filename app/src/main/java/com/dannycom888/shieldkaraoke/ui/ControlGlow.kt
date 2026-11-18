package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * Outer glow for the player screen's control-bar chips.
 *
 * The user's final word (v1.0.65, superseding the v1.0.64
 * per-chip-colour halo): "on the TV the buttons glow WHITE
 * outward; the one you point at grows bigger and glows
 * PINK." So every control chip wears the same soft white
 * halo at rest, and the highlighted chip — the one holding
 * the remote's focus, or under a touching finger — scales
 * up a notch and its halo turns pink and brighter. On the
 * TV the highlight IS the primary focus indicator (the
 * v1.0.65 focus repair leans on it): bigger + pink reads
 * from across the room, where the white 2dp focus border
 * alone could be missed.
 *
 * Drawn WITHOUT Modifier.blur: blur is a silent no-op
 * below API 31 and the Shield runs API 30 (the brand logo's
 * halo hit exactly that in v1.0.44). The halo is layered
 * enlarged silhouettes instead — the chip's own rounded
 * rect redrawn behind it at growing outsets with falling
 * alpha (see [LAYERS]) — so it renders identically on
 * every API level.
 *
 * Pure arithmetic lives here so unit tests can pin the
 * falloff table and the highlight boost without a device.
 */
object ControlGlow {

    /** The resting halo colour: white for every chip,
     *  whatever its own background (the user's word). */
    val NORMAL_COLOR: Color = Color.White

    /** The highlighted halo colour: a clear pink — high red,
     *  mid green/blue — readable as pink on the navy stage
     *  even after the layers' low alphas soften it. */
    val FOCUS_COLOR: Color = Color(0xFFFF6FA5)

    /** How much a highlighted chip grows: one notch — big
     *  enough to spot from the couch, small enough not to
     *  shove its neighbours in the packed control bar. */
    const val FOCUS_SCALE = 1.10f

    /** (outset dp, alpha) per halo layer, innermost first:
     *  outsets grow while alphas fall, so the halo has a
     *  bright core hugging the chip and a long soft tail —
     *  no single layer's edge is visible as a rim (the
     *  outermost alpha stays at or under 0.05). */
    val LAYERS: List<Pair<Float, Float>> = listOf(
        3f to 0.20f,
        6f to 0.13f,
        10f to 0.08f,
        14f to 0.045f,
    )

    /** How far the halo reaches past the chip: the
     *  outermost layer's outset. */
    val REACH_DP: Float get() = LAYERS.last().first

    /** A highlighted chip's halo runs this much brighter
     *  (the pink switch is the loud part; this is the lift
     *  under it). */
    const val FOCUS_ALPHA_BOOST = 2.0f

    /** No layer ever exceeds this alpha, highlighted or
     *  not — the halo must stay a glow, never a blob. */
    const val MAX_ALPHA = 0.45f

    /** The alpha one layer draws at: its table alpha,
     *  boosted while highlighted, always capped. */
    fun alphaFor(baseAlpha: Float, highlighted: Boolean): Float {
        val boosted = if (highlighted) baseAlpha * FOCUS_ALPHA_BOOST else baseAlpha
        return boosted.coerceAtMost(MAX_ALPHA)
    }
}

/**
 * The text counterpart of the chip halo (v1.0.66): the
 * now-singing line glows pink "the same way the buttons
 * do" (the user's word). A rect silhouette cannot hug
 * glyphs, so the halo is the text's own silhouette: the
 * same line drawn again at small offsets around the
 * original, in [ControlGlow.FOCUS_COLOR] at falling
 * alphas — an inner ring that hugs the strokes and a
 * fainter outer ring. Layered copies, never blur (the
 * same API-30 rule as the chips).
 *
 * Pure data, so the falloff and the symmetry are unit
 * pinned like the chip table.
 */
object TextGlow {

    /** (dx dp, dy dp, alpha) per silhouette pass: an
     *  8-direction inner ring at 1.2 dp, then the four
     *  cardinal directions again at 2.4 dp with less than
     *  half the alpha, so the bloom has a bright core and
     *  a soft tail with no visible rim. Offsets come in
     *  mirrored pairs — the halo grows evenly on every
     *  side of the glyphs. */
    val LAYERS: List<Triple<Float, Float, Float>> = listOf(
        // Inner ring (radius 1.2 dp, alpha 0.30).
        Triple(1.2f, 0f, 0.30f),
        Triple(0.85f, 0.85f, 0.30f),
        Triple(0f, 1.2f, 0.30f),
        Triple(-0.85f, 0.85f, 0.30f),
        Triple(-1.2f, 0f, 0.30f),
        Triple(-0.85f, -0.85f, 0.30f),
        Triple(0f, -1.2f, 0.30f),
        Triple(0.85f, -0.85f, 0.30f),
        // Outer ring (radius 2.4 dp, alpha 0.13).
        Triple(2.4f, 0f, 0.13f),
        Triple(0f, 2.4f, 0.13f),
        Triple(-2.4f, 0f, 0.13f),
        Triple(0f, -2.4f, 0.13f),
    )

    /** How far the bloom reaches past the glyphs. */
    val REACH_DP: Float get() = LAYERS.maxOf { maxOf(kotlin.math.abs(it.first), kotlin.math.abs(it.second)) }
}

/**
 * The halo itself: the chip's rounded-rect silhouette
 * drawn behind the chip once per [ControlGlow.LAYERS]
 * entry, in [color] — white at rest, [ControlGlow]'s pink
 * while highlighted (the caller picks; the scale-up that
 * goes with the highlight is also the caller's, a
 * graphicsLayer on the same branch).
 *
 * [cornerDp] matches the chip's own corner radius so the
 * halo hugs the chip's shape instead of rounding past it.
 */
fun Modifier.controlGlow(
    color: Color,
    highlighted: Boolean,
    cornerDp: Float = 16f,
): Modifier = drawBehind {
    val corner = CornerRadius(cornerDp.dp.toPx(), cornerDp.dp.toPx())
    // Outermost first, so the brighter inner layers land on
    // top of the faint tail.
    for ((outsetDp, alpha) in ControlGlow.LAYERS.asReversed()) {
        val outset = outsetDp.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + outset * 2f, size.height + outset * 2f),
            cornerRadius = CornerRadius(corner.x + outset, corner.y + outset),
            alpha = ControlGlow.alphaFor(alpha, highlighted),
        )
    }
}

/**
 * The per-corner form of [controlGlow] (v1.0.67), for the
 * language pill's halves: a half is rounded on the pill's
 * outside and square where the two halves meet, so its
 * halo needs a different radius per corner. Same layer
 * table, same white-at-rest / pink-when-highlighted rule —
 * only the silhouette differs. Each corner's radius grows
 * by the layer's outset, exactly like the single-radius
 * form (a square corner's offset contour is an arc of the
 * outset's own radius, so a 0 base grows to the outset —
 * the geometry of an outward offset, not a fudge).
 *
 * drawRoundRect takes one radius for all four corners,
 * so the silhouette goes through a [Path] built on a
 * per-corner [RoundRect] instead.
 */
fun Modifier.controlGlow(
    color: Color,
    highlighted: Boolean,
    topStartDp: Float,
    topEndDp: Float,
    bottomEndDp: Float,
    bottomStartDp: Float,
): Modifier = drawBehind {
    for ((outsetDp, alpha) in ControlGlow.LAYERS.asReversed()) {
        val outset = outsetDp.dp.toPx()
        fun corner(baseDp: Float) =
            CornerRadius((baseDp + outsetDp).dp.toPx(), (baseDp + outsetDp).dp.toPx())
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = -outset,
                    top = -outset,
                    right = size.width + outset,
                    bottom = size.height + outset,
                    topLeftCornerRadius = corner(topStartDp),
                    topRightCornerRadius = corner(topEndDp),
                    bottomRightCornerRadius = corner(bottomEndDp),
                    bottomLeftCornerRadius = corner(bottomStartDp),
                ),
            )
        }
        drawPath(
            path = path,
            color = color,
            alpha = ControlGlow.alphaFor(alpha, highlighted),
        )
    }
}
