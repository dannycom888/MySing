package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Outer glow for the player control bar's chips (v1.0.64 —
 * the user: "按钮做成外发光", make the buttons glow). The
 * control bar has no strip behind it — the chips float
 * directly on the stage (pinned in ControlGlowTest) — so
 * each lit chip carries its own halo, like a stage-lit
 * button: the colour is the chip's OWN background colour
 * (the gold selected chips glow warm gold, the dark-blue
 * chips glow blue), passed in by the chip itself.
 *
 * NO BLUR, deliberately, and the reason is written here so
 * nobody "simplifies" it later: the blur modifier silently
 * no-ops below API 31 and the Shield is API 30 — the brand
 * logo's halo hit exactly that in v1.0.44 and had to be
 * rebuilt as a silhouette underlay. This glow is the same
 * family of trick: the chip's rounded-rect silhouette
 * drawn several times behind it, each pass a little larger
 * and a little fainter (see [LAYERS]) — the stacked soft
 * edges read as one halo. Pure numbers live in
 * [ControlGlow] so the tests can pin them.
 *
 * Focus: with no strip behind the bar, a focused chip is
 * found by its white focus frame (the chip's own border,
 * unchanged) PLUS this halo stepped up one notch — every
 * pass's alpha scaled by [ControlGlow.FOCUS_ALPHA_BOOST],
 * capped by [ControlGlow.MAX_ALPHA] so it reads as a
 * stronger glow, never a glare.
 */
object ControlGlow {
    /** Halo passes as (outset dp, alpha), innermost first:
     *  outsets strictly grow, alphas strictly fall, and the
     *  outermost pass is a whisper (≤ 0.05) so the halo
     *  fades out instead of ending on a visible rim. */
    val LAYERS: List<Pair<Float, Float>> = listOf(
        3f to 0.20f,
        6f to 0.13f,
        10f to 0.08f,
        14f to 0.045f,
    )

    /** The focused halo's boost: one clear notch up. */
    const val FOCUS_ALPHA_BOOST = 2.0f

    /** No halo pass may exceed this alpha, focused or not. */
    const val MAX_ALPHA = 0.45f

    /** The halo's full reach beyond the chip, in dp. */
    val REACH_DP: Float get() = LAYERS.last().first

    /** One pass's alpha: the table value, boosted and
     *  capped when the chip holds the remote's focus. */
    fun alphaFor(baseAlpha: Float, focused: Boolean): Float =
        if (focused) {
            (baseAlpha * FOCUS_ALPHA_BOOST).coerceAtMost(MAX_ALPHA)
        } else {
            baseAlpha
        }
}

/**
 * Draws the [ControlGlow] halo behind the chip this is
 * applied to: [color] is the chip's own background colour,
 * [cornerDp] its corner radius (each pass's corner grows
 * with its outset, so the halo hugs the silhouette).
 * Applied ahead of the chip's border and background in its
 * modifier chain — the chip's own face then covers the
 * halo's inner part and only the skirt outside the
 * silhouette shows. Nothing clips it: the halo spills onto
 * the stage around the chip, which is the point.
 */
fun Modifier.controlGlow(
    color: Color,
    focused: Boolean,
    cornerDp: Float = 16f,
): Modifier = drawBehind {
    val corner = cornerDp.dp.toPx()
    for ((outsetDp, alpha) in ControlGlow.LAYERS) {
        val o = outsetDp.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = ControlGlow.alphaFor(alpha, focused)),
            topLeft = Offset(-o, -o),
            size = Size(size.width + 2f * o, size.height + 2f * o),
            cornerRadius = CornerRadius(corner + o, corner + o),
        )
    }
}
