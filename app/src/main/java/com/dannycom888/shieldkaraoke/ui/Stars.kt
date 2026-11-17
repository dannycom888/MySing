package com.dannycom888.shieldkaraoke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambient star field (v1.0.57): the third element of the
 * approved stage mock, promoted to the shared ambient layer
 * next to the bubbles (every screen gets the same sky). Fine
 * warm-white points, each twinkling on its own SLOW sine —
 * periods of several seconds, never fully dark, never a flash:
 * the user asked for stars that "慢慢地闪亮" (glow slowly), so
 * [MIN_PERIOD_SEC] is a floor the tests pin against anyone
 * speeding the field up later. Like the bubbles, positions and
 * brightness are pure functions of (star, elapsed seconds) —
 * deterministic from a fixed seed, and driven by wall clock,
 * not playback state: pausing a song does not freeze the sky
 * (the bubbles' long-standing behaviour).
 *
 * v1.0.59, three user rulings: the stars grow and multiply
 * ("星光再大些，多些" — bigger radii, counts up); the field is
 * dealt left-heavy — most stars over the dancer's side of the
 * stage, the lyrics band's sky deliberately sparse, so no star
 * competes with a lyric character; and while a song plays the
 * WHOLE field breathes with the beat (see [breathMultiplier]):
 * one shared gentle swell per beat under the per-star
 * twinkles, riding the playback clock — pause freezes the
 * breath (the position stops), the twinkles keep their wall
 * clock, and an idle stage breathes not at all.
 */
object Stars {
    const val SEED = 20261116L
    const val COUNT_FULL = 108
    const val COUNT_COMPACT = 72

    const val MIN_RADIUS_DP = 0.9f
    const val MAX_RADIUS_DP = 2.6f
    const val MIN_PEAK_ALPHA = 0.18f
    const val MAX_PEAK_ALPHA = 0.50f

    /** Seconds per full twinkle (dim → bright → dim). The
     *  floor is the anti-strobe pin: no star may ever cycle
     *  faster than this. */
    const val MIN_PERIOD_SEC = 3.5f
    const val MAX_PERIOD_SEC = 9f

    /** A star never goes fully dark: at its dimmest it still
     *  holds this fraction of its peak. */
    const val MIN_ALPHA_FRACTION = 0.30f

    // ---- distribution: left-heavy, lyrics-sparse (v1.0.59) --

    /** Where the lyrics band's sky starts, as a fraction of
     *  the screen width: the dancer zone's right edge, taken
     *  from [LyricsBand] so the two can never drift apart. */
    const val LYRICS_ZONE_START = LyricsBand.DANCER_FRACTION

    /** Where the lyrics band's sky ends: the band's right edge
     *  (screen width minus the queue panel's reserve, in round
     *  numbers — the reserve itself is panel territory and
     *  takes the normal right-zone share). */
    const val LYRICS_ZONE_END = 0.80f

    /** Shares of the field dealt to the three zones. Per unit
     *  of width the lyrics zone ends up with barely a fifth of
     *  the dancer side's density — sparse on purpose. */
    const val LEFT_SHARE = 0.55f
    const val LYRICS_SHARE = 0.15f
    const val RIGHT_SHARE = 0.30f

    /** Zone of a star at [xFrac]: 0 = the dancer's side,
     *  1 = the lyrics band's sky, 2 = the right remainder. */
    fun zoneOf(xFrac: Float): Int = when {
        xFrac < LYRICS_ZONE_START -> 0
        xFrac < LYRICS_ZONE_END -> 1
        else -> 2
    }

    /** How many of [count] stars each zone is dealt (left,
     *  lyrics, right); rounding residue goes to the left, the
     *  zone the user asked to favour. */
    fun zoneCounts(count: Int): Triple<Int, Int, Int> {
        val left = (count * LEFT_SHARE).toInt()
        val lyrics = (count * LYRICS_SHARE).toInt()
        return Triple(left, lyrics, count - left - lyrics)
    }

    // ---- the beat breath (v1.0.59) ---------------------------

    /** The whole field's swell at a beat's peak, as a fraction
     *  added to every star's brightness: deliberately SMALL —
     *  a breath, not a pulse. The cap is pinned by the tests;
     *  anyone wanting it bigger must argue with them. */
    const val BREATH_AMPLITUDE = 0.18f

    /**
     * The shared brightness multiplier at playback position
     * [positionMs] in a song of [bpm] beats per minute: 1 +
     * [BREATH_AMPLITUDE] exactly ON each beat, easing back to
     * 1 across the beat (a squared raised cosine — smooth at
     * both ends, so the swell rises gently and settles gently,
     * never a step). Pure: the same position always gives the
     * same multiplier, which is what freezes the breath when
     * the position freezes (pause) with no state anywhere.
     * No song / no tempo → exactly 1 (no breath).
     */
    fun breathMultiplier(positionMs: Long, bpm: Double): Float {
        if (positionMs < 0L || bpm <= 0.0) return 1f
        val cycles = positionMs.toDouble() * bpm / 60_000.0
        val phase = cycles - kotlin.math.floor(cycles)
        val glow = 0.5 + 0.5 * kotlin.math.cos(2.0 * Math.PI * phase)
        return 1f + (BREATH_AMPLITUDE * glow * glow).toFloat()
    }

    data class Star(
        /** Resting position, fractions of the stage size. */
        val xFrac: Float,
        val yFrac: Float,
        val radiusDp: Float,
        val peakAlpha: Float,
        /** Seconds per full twinkle cycle. */
        val periodSec: Float,
        /** Starting point in the cycle, 0..1 — every star keeps
         *  its own phase, so the field never pulses in unison. */
        val phase: Float,
    )

    /** Deterministic field generation: same seed, same sky.
     *  Stars are dealt zone by zone ([zoneCounts]) — the left
     *  of the stage first — and each star's x is sampled inside
     *  its own zone; everything else is sampled as before. */
    fun generate(count: Int, seed: Long = SEED): List<Star> {
        val rnd = Random(seed)
        val (left, lyrics, _) = zoneCounts(count)
        val zones = listOf(
            0f to LYRICS_ZONE_START,
            LYRICS_ZONE_START to LYRICS_ZONE_END,
            LYRICS_ZONE_END to 1f,
        )
        return List(count) { i ->
            val (zx0, zx1) = when {
                i < left -> zones[0]
                i < left + lyrics -> zones[1]
                else -> zones[2]
            }
            Star(
                xFrac = zx0 + rnd.nextFloat() * (zx1 - zx0),
                yFrac = rnd.nextFloat(),
                radiusDp = MIN_RADIUS_DP + rnd.nextFloat() * (MAX_RADIUS_DP - MIN_RADIUS_DP),
                peakAlpha = MIN_PEAK_ALPHA + rnd.nextFloat() * (MAX_PEAK_ALPHA - MIN_PEAK_ALPHA),
                periodSec = MIN_PERIOD_SEC + rnd.nextFloat() * (MAX_PERIOD_SEC - MIN_PERIOD_SEC),
                phase = rnd.nextFloat(),
            )
        }
    }

    /** Brightness at [timeSec]: a sine between
     *  [MIN_ALPHA_FRACTION] × peak and the peak itself. Pure —
     *  same inputs, same glow, forever. */
    fun alphaAt(s: Star, timeSec: Float): Float {
        val wave = 0.5f + 0.5f *
            sin(2.0 * Math.PI * (timeSec / s.periodSec + s.phase)).toFloat()
        return s.peakAlpha * (MIN_ALPHA_FRACTION + (1f - MIN_ALPHA_FRACTION) * wave)
    }
}

private val STAR_TINT = Color(0xFFFFF6D8)

/**
 * The shared star layer, drawn beside the bubbles on the
 * composition's own frame clock (same ~30 fps throttle — a
 * several-second sine needs no more, and the lyric sweep keeps
 * its frame budget). A plain Canvas: no focus, no clicks.
 *
 * v1.0.59: [beatPositionMs] / [beatBpm] carry the playing
 * song's clock when there is one (the root passes null on
 * every other screen and whenever the stage is idle): the
 * whole field's brightness is multiplied by
 * [Stars.breathMultiplier], so the sky swells once per beat
 * under the individual twinkles. A paused song freezes the
 * position, which freezes the breath — the twinkles, on their
 * wall clock, glow on.
 */
@Composable
fun StarBackground(
    compact: Boolean,
    modifier: Modifier = Modifier,
    beatPositionMs: Long? = null,
    beatBpm: Double = 0.0,
) {
    val stars = remember(compact) {
        Stars.generate(if (compact) Stars.COUNT_COMPACT else Stars.COUNT_FULL)
    }
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var lastPush = 0L
        while (true) {
            val now = withFrameMillis { it }
            if (now - lastPush >= 33L) {
                lastPush = now
                frameMs = now
            }
        }
    }
    val breath = if (beatPositionMs != null) {
        Stars.breathMultiplier(beatPositionMs, beatBpm)
    } else {
        1f
    }
    Canvas(modifier = modifier) {
        val tSec = frameMs / 1000f
        for (s in stars) {
            val cx = s.xFrac * size.width
            val cy = s.yFrac * size.height
            val r = s.radiusDp * density
            val a = Stars.alphaAt(s, tSec) * breath
            // Halo first (a wide, faint pool), then the point
            // itself — a star is a glow, not a disc.
            drawCircle(
                color = STAR_TINT.copy(alpha = a * 0.25f),
                radius = r * 2.2f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = STAR_TINT.copy(alpha = a),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}
