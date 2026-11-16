package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * Stage spotlight pool geometry (v1.0.25): every dancer stands in a
 * flattened ellipse of warm light on the stage floor — horizontal
 * radius [RADIUS_PER_DANCER_WIDTH]× the dancer's own rendered
 * width, squashed vertically to [FLATTEN] of that radius, warm
 * white-gold in the same family as the lyric sweep gold. Pure
 * numbers live here so unit tests can pin them; the drawing is a
 * single [spotlightPool] modifier.
 */
object SpotlightGeom {
    /** Dancer frame canvases are 468×600, so a dancer rendered
     *  height-first is this fraction as wide as it is tall. */
    const val FRAME_ASPECT = 468f / 600f

    /** Horizontal pool radius per unit of dancer width. */
    const val RADIUS_PER_DANCER_WIDTH = 1.2f

    /** Vertical radius as a fraction of the horizontal radius. */
    const val FLATTEN = 0.28f

    /** How far the pool's centre sits below the foot baseline, as a
     *  fraction of the pool's vertical radius: most of the light
     *  lies on the floor around the feet, a soft spill reaches a
     *  little way toward the audience. */
    const val CENTRE_DROP_PER_RY = 0.35f

    /** Alpha at the pool's centre (before the caller's own fade). */
    const val CENTRE_ALPHA = 0.50f

    /** v1.0.45: the six-dancer row (ceremony + end-of-song
     *  celebration) aims for this fraction of the screen height —
     *  as tall as the width budget below allows. */
    const val SIX_ROW_TARGET_FRACTION = 0.46f

    /** v1.0.45: the row of six may span at most this fraction of
     *  the screen width (frames + the gaps between them). */
    const val SIX_ROW_MAX_WIDTH_FRACTION = 0.98f

    /**
     * Centre-to-centre distance of neighbouring dancers in the
     * row of six, per unit of dancer height (v1.0.45, second
     * note — the user: "六人可以靠近一些，现在分得太开"). Each
     * frame is [FRAME_ASPECT] (0.78) wide but the figure inside
     * is centred with transparent margin on both sides, so frames
     * placed edge to edge read as people standing far apart. At a
     * pitch of 0.60 the transparent margins overlap and the
     * figures stand close, group-photo style; wide-arm poses may
     * overlap slightly, which is intended.
     */
    const val SIX_ROW_PITCH_FRACTION = 0.60f

    /** How many dancers stand in the row. */
    const val SIX_ROW_COUNT = 6

    /**
     * Total span of the row of six per unit of dancer height: one
     * full frame plus five pitches — 0.78 + 5 × 0.60 = 3.78.
     */
    const val SIX_ROW_SPAN_PER_HEIGHT =
        FRAME_ASPECT + (SIX_ROW_COUNT - 1) * SIX_ROW_PITCH_FRACTION

    /**
     * Height of one dancer in the row of six (v1.0.45 — the user:
     * "6人再放大"): the target fraction of the screen height,
     * capped so the whole overlapped row (span 3.78 × height, see
     * [SIX_ROW_SPAN_PER_HEIGHT]) fits in
     * [SIX_ROW_MAX_WIDTH_FRACTION] of the screen width. With the
     * tightened pitch the 0.46 target itself binds on both a 16:9
     * TV (span ≈ 97.8% of the width there) and the user's wide
     * phone; the width cap only binds on narrower screens.
     */
    fun sixRowHeightDp(screenWDp: Float, screenHDp: Float): Float {
        val byHeight = screenHDp * SIX_ROW_TARGET_FRACTION
        val byWidth = screenWDp * SIX_ROW_MAX_WIDTH_FRACTION / SIX_ROW_SPAN_PER_HEIGHT
        return minOf(byHeight, byWidth).coerceAtLeast(0f)
    }

    /** Rendered width of a dancer shown [dancerHeightDp] tall. */
    fun dancerWidthDp(dancerHeightDp: Float): Float = dancerHeightDp * FRAME_ASPECT

    /** Full pool width for a dancer shown [dancerHeightDp] tall. */
    fun poolWidthDp(dancerHeightDp: Float): Float =
        dancerWidthDp(dancerHeightDp) * 2f * RADIUS_PER_DANCER_WIDTH

    /** Full pool height (the flattened ellipse's vertical span). */
    fun poolHeightDp(dancerHeightDp: Float): Float = poolWidthDp(dancerHeightDp) * FLATTEN
}

private val POOL_CENTRE = Color(0xFFFFF6D8)
private val POOL_MID = Color(0xFFFFF6D8)
private val POOL_EDGE = Color(0xFFFFF6D8).copy(alpha = 0f)

/**
 * Draws the spotlight pool behind the content of the node this is
 * applied to. The node is expected to wrap exactly one dancer
 * image (its box IS the dancer's rendered rect: width = dancer
 * width, bottom edge = foot baseline), so the pool follows the
 * dancer's position and size automatically. Drawing is not
 * clipped to the node — the ellipse is wider than the dancer by
 * design and spills softly onto the stage floor around her.
 * [alphaScale] lets a caller fade the pool together with its
 * dancer (the ceremony's exit melt). Static by design (v1.0.25):
 * a stage light does not flicker, and stillness cannot fight the
 * lyric sweep's frame budget.
 */
fun Modifier.spotlightPool(alphaScale: Float = 1f): Modifier = drawBehind {
    val rx = size.width * SpotlightGeom.RADIUS_PER_DANCER_WIDTH
    val ry = rx * SpotlightGeom.FLATTEN
    val cx = size.width / 2f
    // Feet stand ~2% above the frame canvas's bottom edge (the
    // cutout baseline); the pool centre sits a little below that
    // line so the brightest light pools around the feet.
    val cy = size.height * 0.98f + ry * SpotlightGeom.CENTRE_DROP_PER_RY
    val a = SpotlightGeom.CENTRE_ALPHA * alphaScale.coerceIn(0f, 1f)
    withTransform({ scale(1f, ry / rx, Offset(cx, cy)) }) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to POOL_CENTRE.copy(alpha = a),
                    0.55f to POOL_MID.copy(alpha = a * 0.42f),
                    1f to POOL_EDGE,
                ),
                center = Offset(cx, cy),
                radius = rx,
            ),
            radius = rx,
            center = Offset(cx, cy),
        )
    }
}
