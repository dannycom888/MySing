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

    /** The six-dancer row (ceremony + end-of-song celebration)
     *  aims for this fraction of the screen height — as tall as
     *  the width budget below allows. v1.0.45 set 0.46; v1.0.48
     *  raises the aim to 0.58 (the user: "六人再放大"), with the
     *  panel-avoidance cap below deciding what actually fits. */
    const val SIX_ROW_TARGET_FRACTION = 0.58f

    /**
     * Centre-to-centre distance of neighbouring dancers in the
     * row of six, per unit of dancer height. v1.0.45 used 0.60 —
     * still far apart on screen, because each figure is much
     * narrower than its 0.78-wide frame. v1.0.48 sets the pitch
     * from the MEASURED figure widths (alpha > 8 bounding boxes
     * of the still + invite cutouts the row actually draws, in
     * units of the canvas height): Amy 0.308/0.362, Xiuying
     * 0.595/0.317, sumo 0.453/0.485, Hannah 0.300/0.245, baby
     * 0.283/0.202, robot 0.383/0.355. The widest neighbouring
     * pair (Xiuying's spread hanfu sleeves against the sumo,
     * clapping frame) needs a half-sum of 0.524 for its edges
     * just to touch; every other pair needs ≤ 0.452. At a pitch
     * of 0.48 every pair keeps a positive edge gap (smallest
     * +0.028, Amy–Xiuying clapping) except that one widest pair,
     * whose bounding boxes overlap by 0.044 — sleeve tips over
     * the sumo's flank, the slight wide-arm overlap the user
     * explicitly allows ("手臂张开的姿势轻微交叠可以"). All
     * figures sit centred in their canvases (≤ 0.01 off), so a
     * uniform pitch is honest.
     */
    const val SIX_ROW_PITCH_FRACTION = 0.48f

    /** How many dancers stand in the row. */
    const val SIX_ROW_COUNT = 6

    /**
     * Total span of the row of six per unit of dancer height: one
     * full frame plus five pitches — 0.78 + 5 × 0.48 = 3.18.
     */
    const val SIX_ROW_SPAN_PER_HEIGHT =
        FRAME_ASPECT + (SIX_ROW_COUNT - 1) * SIX_ROW_PITCH_FRACTION

    /**
     * Height of one dancer in the row of six: the target fraction
     * of the screen height, capped so the whole row fits in the
     * width LEFT of the queue panel's reserve band
     * ([reserveWidthDp], from [LyricsBand] — the band the panel
     * and QR live in). v1.0.48 (the user, from a photo of his
     * phone: the row ran UNDER the 已点歌单 panel and hid half
     * the robot): the row may never reach into that band, so the
     * cap is (screen width − reserve) / span, with no extra
     * factor — [sixRowLeftDp] centres the row in exactly that
     * available width, which makes "row right edge ≤ reserve
     * band's left edge" hold by construction. Effective heights:
     * 16:9 TV 1920×1080 → 485 dp ≈ 0.45 of the height (width-
     * bound; the figures stand far closer than v1.0.45's 0.46
     * row and nobody is under the panel); the user's phone
     * ≈869×411 dp → 218 dp ≈ 0.53 (also width-bound, up from
     * 0.46); the 0.58 target itself only binds on ultrawide
     * screens.
     */
    fun sixRowHeightDp(screenWDp: Float, screenHDp: Float, reserveWidthDp: Float): Float {
        val byHeight = screenHDp * SIX_ROW_TARGET_FRACTION
        val avail = (screenWDp - reserveWidthDp).coerceAtLeast(0f)
        val byWidth = avail / SIX_ROW_SPAN_PER_HEIGHT
        return minOf(byHeight, byWidth).coerceAtLeast(0f)
    }

    /**
     * Where the row of six starts, in dp from the screen's left
     * edge: centred in the available width left of the reserve
     * band, so its left margin equals its right margin and its
     * right edge lands exactly on (never past) the band's left
     * edge when the width cap binds.
     */
    fun sixRowLeftDp(screenWDp: Float, heightDp: Float, reserveWidthDp: Float): Float {
        val avail = (screenWDp - reserveWidthDp).coerceAtLeast(0f)
        val span = heightDp * SIX_ROW_SPAN_PER_HEIGHT
        return ((avail - span) / 2f).coerceAtLeast(0f)
    }

    /**
     * v1.0.48: how far the robot （瓦力， the last slot of the row
     * of six) shifts LEFT inside his slot, per unit of dancer
     * height — the user, on v1.0.47: "鼓掌时瓦力太靠边了，再靠近
     * 时，向左移". Measured 2026-11-16 on the shipped still
     * cutouts the row actually draws (alpha > 8 bounding box;
     * every canvas is 600 px tall):
     *
     *  - Amy, slot 0 (dancer_modern_woman.png, 199×600, figure
     *    x ∈ [7, 192]): her image is only 0.332 × height wide, so
     *    inside the fixed 0.78 slot it sits (0.78 − 0.332)/2 deep,
     *    and her VISUAL left margin inside the row works out to
     *    (0.78 − 199/600)/2 + 7/600 = 0.2358 × height.
     *  - The robot, slot 5 (dancer_robot.png, 243×600, figure
     *    x ∈ [7, 237]): his wider 0.405-aspect image sits only
     *    (0.78 − 0.405)/2 deep, so his VISUAL right margin is
     *    3.78 − 5×0.60 − [(0.78 − 243/600)/2 + 243/600 − 6/600]
     *    = 0.1975 × height.
     *
     * Both figures are centred in their own canvases (Amy 7 px /
     * 7 px, the robot 7 px / 6 px) — the on-screen asymmetry is
     * the different canvas aspects inside the fixed-width slot,
     * not the art and not the span: shrinking the span would lift
     * both margins by the same amount and could never equalise
     * them. The v1.0.48 pitch tightening and panel-avoidance
     * centring leave the difference untouched too (the end slots'
     * visual margins do not depend on the pitch, and centring
     * lifts both sides equally — re-verified against the 0.48
     * pitch: still 0.2358 vs 0.1975), so this offset stays as the
     * third, smallest part of that fix. Shifting the robot left
     * by the measured difference (0.2358 − 0.1975 = 0.0383,
     * robust at 0.035–0.038 across alpha thresholds 8–128) makes
     * his visual right margin equal Amy's visual left margin on
     * every screen, because the difference is a pure function of
     * the art and the slot geometry, not of the screen size.
     * Only the row's robot slot moves; his solo / invite
     * placement is untouched.
     */
    const val SIX_ROW_ROBOT_LEFT_SHIFT_FRACTION = 0.038f

    /** The robot's left shift in the row of six, in dp. */
    fun sixRowRobotShiftDp(dancerHeightDp: Float): Float =
        dancerHeightDp * SIX_ROW_ROBOT_LEFT_SHIFT_FRACTION

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
