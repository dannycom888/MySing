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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambient bubble backdrop (v1.0.25): translucent bubbles drift
 * slowly down behind EVERYTHING (all four screens share the one
 * layer the root lays over the navy base). The field is generated
 * from a fixed seed — the same sky on every launch, and the pure
 * model functions are unit-testable without a clock: positions are
 * a function of (bubble, elapsed seconds, stage size) only.
 */
object Bubbles {
    const val SEED = 20261115L
    const val COUNT_FULL = 20
    const val COUNT_COMPACT = 14

    const val MIN_RADIUS_DP = 6f
    const val MAX_RADIUS_DP = 42f
    const val MIN_ALPHA = 0.05f
    const val MAX_ALPHA = 0.15f

    /** Seconds one full top-to-bottom crossing takes; the slow
     *  end of the field is the stately 8–14 s the user asked for,
     *  the quick end only slightly brisker. */
    const val MIN_CYCLE_SEC = 7f
    const val MAX_CYCLE_SEC = 14f

    const val MIN_SWAY_DP = 4f
    const val MAX_SWAY_DP = 14f
    const val MIN_SWAY_PERIOD_SEC = 3f
    const val MAX_SWAY_PERIOD_SEC = 7f

    data class Bubble(
        /** Resting horizontal position, fraction of stage width. */
        val xFrac: Float,
        val radiusDp: Float,
        /** Seconds per full crossing (top margin to bottom margin). */
        val cycleSec: Float,
        /** Starting point in the cycle, 0..1 — staggers the field
         *  so bubbles are spread over the whole height at t=0. */
        val phase: Float,
        val swayAmpDp: Float,
        val swayPeriodSec: Float,
        val alpha: Float,
    )

    /** Deterministic field generation: same seed, same sky. */
    fun generate(count: Int, seed: Long = SEED): List<Bubble> {
        val rnd = Random(seed)
        return List(count) {
            Bubble(
                xFrac = rnd.nextFloat(),
                radiusDp = MIN_RADIUS_DP + rnd.nextFloat() * (MAX_RADIUS_DP - MIN_RADIUS_DP),
                cycleSec = MIN_CYCLE_SEC + rnd.nextFloat() * (MAX_CYCLE_SEC - MIN_CYCLE_SEC),
                phase = rnd.nextFloat(),
                swayAmpDp = MIN_SWAY_DP + rnd.nextFloat() * (MAX_SWAY_DP - MIN_SWAY_DP),
                swayPeriodSec = MIN_SWAY_PERIOD_SEC +
                    rnd.nextFloat() * (MAX_SWAY_PERIOD_SEC - MIN_SWAY_PERIOD_SEC),
                alpha = MIN_ALPHA + rnd.nextFloat() * (MAX_ALPHA - MIN_ALPHA),
            )
        }
    }

    /**
     * Centre height (dp from the stage top) at [timeSec]: the
     * bubble travels from one radius above the top edge to one
     * radius below the bottom edge, then wraps. Pure — same
     * inputs, same position, forever.
     */
    fun yDpAt(b: Bubble, timeSec: Float, stageHeightDp: Float): Float {
        val travel = stageHeightDp + 2f * b.radiusDp
        val frac = (b.phase + timeSec / b.cycleSec) % 1f
        return -b.radiusDp + frac * travel
    }

    /** Centre x (dp from the stage left): resting spot plus a
     *  gentle sinusoidal sway. */
    fun xDpAt(b: Bubble, timeSec: Float, stageWidthDp: Float): Float {
        val sway = sin(2.0 * Math.PI * (timeSec / b.swayPeriodSec + b.phase)).toFloat()
        return b.xFrac * stageWidthDp + sway * b.swayAmpDp
    }
}

private val BUBBLE_TINT = Color(0xFFEAF6FF)

/**
 * The shared bubble layer. Drawn on the composition's own frame
 * clock — no separate timer: the loop suspends on
 * [withFrameMillis], so when the app stops producing frames
 * (backgrounded) the field simply stops, and the clock state is
 * throttled to ~30 fps so the bubbles never compete with the
 * lyric sweep's 33 ms polling for frame budget. A plain Canvas:
 * no focus, no click handling — content laid over it is
 * untouched.
 */
@Composable
fun BubbleBackground(compact: Boolean, modifier: Modifier = Modifier) {
    val bubbles = remember(compact) {
        Bubbles.generate(if (compact) Bubbles.COUNT_COMPACT else Bubbles.COUNT_FULL)
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
        val stageW = size.width / density
        val stageH = size.height / density
        for (b in bubbles) {
            val cx = Bubbles.xDpAt(b, tSec, stageW) * density
            val cy = Bubbles.yDpAt(b, tSec, stageH) * density
            val r = b.radiusDp * density
            if (cy < -r || cy > size.height + r) continue
            // Body: nearly clear middle, a brighter rim near the
            // edge — a soap film catching the light.
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to BUBBLE_TINT.copy(alpha = b.alpha * 0.45f),
                        0.72f to BUBBLE_TINT.copy(alpha = b.alpha * 0.20f),
                        0.92f to BUBBLE_TINT.copy(alpha = b.alpha * 1.35f),
                        1f to BUBBLE_TINT.copy(alpha = b.alpha * 0.10f),
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
            // Highlight: one small bright spot, upper left — the
            // window reflection every real bubble carries.
            val hx = cx - r * 0.38f
            val hy = cy - r * 0.42f
            val hr = r * 0.30f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = (b.alpha * 2.2f).coerceAtMost(0.30f)),
                        1f to Color.White.copy(alpha = 0f),
                    ),
                    center = Offset(hx, hy),
                    radius = hr,
                ),
                radius = hr,
                center = Offset(hx, hy),
            )
        }
    }
}
