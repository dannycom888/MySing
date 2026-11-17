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

    /** The celebration row (ceremony + end-of-song applause)
     *  aims for this fraction of the screen height — as tall as
     *  the width budget below allows. v1.0.45 set 0.46; v1.0.48
     *  raised the aim to 0.58 (the user: "六人再放大"), with the
     *  panel-avoidance cap below deciding what actually fits.
     *  v1.0.50 keeps the aim for the five-figure row. */
    const val ROW_TARGET_FRACTION = 0.58f

    /**
     * Centre-to-centre distance of neighbouring figures in the
     * celebration row, per unit of figure height. v1.0.50: the
     * row is FIVE figures now — Amy holding Hannah as one
     * combined figure, then Xiuying, the sumo, Nezha and the
     * robot (the user: "汉娜能不能让Amy抱着") — and the pitch
     * is re-derived from the MEASURED figure widths of the art
     * the row actually draws (alpha > 8 bounding boxes, units
     * of the 600 px canvas height; each figure's reach is taken
     * per simultaneous state — standing frames, same invite
     * frame index, same bow frame, same clap frame — and the
     * worst state binds; re-measured for v1.0.50 with the bow
     * frames included, no bow state binds any pair).
     * Neighbouring pairs, centre distance at which their edges
     * just touch:
     *
     *  - combo → Xiuying: 0.553 (invite frame 3: Hannah's side
     *    of the combo against Xiuying's sleeve sweeping out)
     *  - Xiuying → sumo: 0.521 (the standing frames: her spread
     *    hanfu sleeve against his flank)
     *  - sumo → Nezha: 0.382 (invite frames)
     *  - Nezha → robot: 0.333 (standing frames)
     *
     * At a pitch of 0.52 the edge gaps are −0.033 / −0.001 /
     * +0.138 / +0.187: only the widest pair's bounding boxes
     * overlap (a translucent sleeve tip over the combo's edge,
     * the slight wide-arm overlap the user explicitly allows),
     * the second pair just kisses in the standing frames, and
     * the rest keep clear air. The combo's figure is centred in
     * its 468-wide canvas (bbox x ∈ [120, 346] standing), so a
     * uniform pitch stays honest.
     */
    const val ROW_PITCH_FRACTION = 0.52f

    /** How many figures stand in the row (v1.0.50: five — Amy
     *  holds Hannah, so the pair takes one slot). */
    const val ROW_COUNT = 5

    /**
     * Total span of the row per unit of figure height: one full
     * frame plus four pitches — 0.78 + 4 × 0.52 = 2.86.
     */
    const val ROW_SPAN_PER_HEIGHT =
        FRAME_ASPECT + (ROW_COUNT - 1) * ROW_PITCH_FRACTION

    /**
     * Height of one figure in the row: the target fraction of
     * the screen height, capped so the whole row fits in the
     * width LEFT of the queue panel's reserve band
     * ([reserveWidthDp], from [LyricsBand] — the band the panel
     * and QR live in). v1.0.48 (the user, from a photo of his
     * phone: the row ran UNDER the 已点歌单 panel and hid half
     * the robot): the row may never reach into that band, so the
     * cap is (screen width − reserve) / span, with no extra
     * factor — [rowLeftDp] centres the row in exactly that
     * available width, which makes "row right edge ≤ reserve
     * band's left edge" hold by construction. Effective heights
     * for the v1.0.50 five-figure span (2.86): 16:9 TV
     * 1920×1080 → 539.6 dp ≈ 0.50 of the height (width-bound,
     * up from v1.0.48's 0.449 — one slot fewer buys real size);
     * the user's phone ≈869×411 dp → 238.4 dp = the 0.58 target
     * itself (the width cap would allow 241.9); 4:3 1024×768 →
     * 285.8 ≈ 0.37 (width-bound).
     */
    fun rowHeightDp(screenWDp: Float, screenHDp: Float, reserveWidthDp: Float): Float {
        val byHeight = screenHDp * ROW_TARGET_FRACTION
        val avail = (screenWDp - reserveWidthDp).coerceAtLeast(0f)
        val byWidth = avail / ROW_SPAN_PER_HEIGHT
        return minOf(byHeight, byWidth).coerceAtLeast(0f)
    }

    /**
     * Where the row starts, in dp from the screen's left edge:
     * centred in the available width left of the reserve band,
     * so its left margin equals its right margin and its right
     * edge lands exactly on (never past) the band's left edge
     * when the width cap binds.
     */
    fun rowLeftDp(screenWDp: Float, heightDp: Float, reserveWidthDp: Float): Float {
        val avail = (screenWDp - reserveWidthDp).coerceAtLeast(0f)
        val span = heightDp * ROW_SPAN_PER_HEIGHT
        return ((avail - span) / 2f).coerceAtLeast(0f)
    }

    /**
     * How far the robot （瓦力， the last slot of the row) shifts
     * LEFT inside his slot, per unit of figure height. v1.0.48
     * measured 0.038 against Amy's solo still (her visual left
     * margin 0.2358 vs his visual right margin 0.1975 — her
     * narrow canvas sat deeper in the fixed slot).
     *
     * v1.0.50 re-check, against the NEW first slot (the Amy +
     * Hannah combo's standing frame, a full 468×600 canvas,
     * figure x ∈ [120, 346]): the combo's visual left margin
     * inside the row is 0.39 − 0.190 = 0.2000 × height; the
     * robot's visual right margin on his standing frame is
     * 2.86 − (4 × 0.52 + 0.39 + 0.193) = 0.1975 × height. The
     * difference is 0.0025 × height — about a dp at any real
     * row height, invisible. The five-figure composition is
     * symmetric on its own, so the personal shift is RETIRED
     * (kept at 0, mechanism in place should future art unbalance
     * the ends again).
     */
    const val ROW_ROBOT_LEFT_SHIFT_FRACTION = 0f

    /** The robot's left shift in the row, in dp. */
    fun rowRobotShiftDp(dancerHeightDp: Float): Float =
        dancerHeightDp * ROW_ROBOT_LEFT_SHIFT_FRACTION

    /** Rendered width of a dancer shown [dancerHeightDp] tall. */
    fun dancerWidthDp(dancerHeightDp: Float): Float = dancerHeightDp * FRAME_ASPECT

    /** Full pool width for a dancer shown [dancerHeightDp] tall. */
    fun poolWidthDp(dancerHeightDp: Float): Float =
        dancerWidthDp(dancerHeightDp) * 2f * RADIUS_PER_DANCER_WIDTH

    /** Full pool height (the flattened ellipse's vertical span). */
    fun poolHeightDp(dancerHeightDp: Float): Float = poolWidthDp(dancerHeightDp) * FLATTEN

    /** The pool's warm white-gold, as a raw ARGB value
     *  (v1.0.60): the ONE colour the pool and the stage beams
     *  share — the beams converge on the pool, and the user
     *  ruled beam and pool must read as the same lamp, so both
     *  take the colour from here and nowhere else. */
    const val POOL_COLOR_ARGB = 0xFFFFF6D8L

    /** Where the feet sit inside the figure box, as a fraction
     *  of its height: the cutout baseline is ~2% above the
     *  canvas's bottom edge, and the pool's centre rides a
     *  little below that line (see [poolCentreInFigureBoxDp]).
     *  Named in v1.0.60 so the drawn pool and the beams' aim
     *  point share the one number. */
    const val FEET_BASELINE_FRACTION = 0.98f

    /**
     * The pool's centre inside the figure box of a dancer
     * [figureHeightDp] tall, in dp from the box's top-left —
     * the exact point [spotlightPool] draws around (its cx /
     * cy, restated as a pure function). v1.0.60: the stage
     * beams converge on this point, so it is computed once,
     * here, and both the pool's own draw and the beams' target
     * derive from it — the aim cannot drift from the light.
     * Note the centre sits BELOW the box's bottom edge (the
     * feet baseline plus the centre drop): the pool is a floor
     * ellipse, drawn unclipped behind the figure.
     */
    fun poolCentreInFigureBoxDp(figureHeightDp: Float): Offset {
        val w = dancerWidthDp(figureHeightDp)
        val ry = w * RADIUS_PER_DANCER_WIDTH * FLATTEN
        return Offset(
            w / 2f,
            figureHeightDp * FEET_BASELINE_FRACTION + ry * CENTRE_DROP_PER_RY,
        )
    }

    /** The pool's horizontal radius for a dancer
     *  [figureHeightDp] tall, in dp — the radius the beams'
     *  landing disc is sized against. */
    fun poolRadiusXDp(figureHeightDp: Float): Float =
        dancerWidthDp(figureHeightDp) * RADIUS_PER_DANCER_WIDTH
}

private val POOL_CENTRE = Color(SpotlightGeom.POOL_COLOR_ARGB)
private val POOL_MID = Color(SpotlightGeom.POOL_COLOR_ARGB)
private val POOL_EDGE = Color(SpotlightGeom.POOL_COLOR_ARGB).copy(alpha = 0f)

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
    // line so the brightest light pools around the feet. The
    // fraction is SpotlightGeom.FEET_BASELINE_FRACTION — the
    // same number the beams' aim point is computed from
    // (SpotlightGeom.poolCentreInFigureBoxDp), so the drawn
    // centre and the aimed-at centre are one point.
    val cy = size.height * SpotlightGeom.FEET_BASELINE_FRACTION +
        ry * SpotlightGeom.CENTRE_DROP_PER_RY
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
