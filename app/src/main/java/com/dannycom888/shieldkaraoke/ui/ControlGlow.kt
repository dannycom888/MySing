package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
