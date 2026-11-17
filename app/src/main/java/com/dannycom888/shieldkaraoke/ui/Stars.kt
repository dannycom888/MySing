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
 */
object Stars {
    const val SEED = 20261116L
    const val COUNT_FULL = 64
    const val COUNT_COMPACT = 44

    const val MIN_RADIUS_DP = 0.7f
    const val MAX_RADIUS_DP = 1.7f
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

    /** Deterministic field generation: same seed, same sky. */
    fun generate(count: Int, seed: Long = SEED): List<Star> {
        val rnd = Random(seed)
        return List(count) {
            Star(
                xFrac = rnd.nextFloat(),
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
 */
@Composable
fun StarBackground(compact: Boolean, modifier: Modifier = Modifier) {
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
    Canvas(modifier = modifier) {
        val tSec = frameMs / 1000f
        for (s in stars) {
            val cx = s.xFrac * size.width
            val cy = s.yFrac * size.height
            val r = s.radiusDp * density
            val a = Stars.alphaAt(s, tSec)
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
