package com.dannycom888.shieldkaraoke.ui

import kotlin.math.floor

/**
 * Which dance-pose frame is on screen, as a pure function of the
 * playback position: one new frame every beat, cycling through
 * the frames in order (0, 1, 2, … N-1, 0, …). Derived only from
 * position + BPM — the same beat phase [DanceMotion] uses — so
 * pausing freezes the current frame, seeking lands on the frame
 * that belongs to that beat, and the frame stepping stays locked
 * to the whole-body bounce/sway layered on top.
 *
 * The rate was one frame every half beat up to v1.0.5; the user
 * found that too frantic, so v1.0.6 halves it to one per beat.
 * Only the frame stepping slows — the bounce/sway phase is
 * untouched.
 *
 * v1.0.10: the user found the one-frame-per-beat stepping too
 * stiff — a hard cut every beat reads as a slideshow. The rate
 * stays exactly as set in v1.0.6; instead [blend] cross-fades
 * each new frame in over the first [FADE_FRACTION] of its beat.
 * The robot is exempt at the call site (its stepped, mechanical
 * hard cuts are its character), and the applause row never
 * blends (all six clap in lockstep).
 *
 * v1.0.17: the *order* the frames are visited in is no longer
 * the sheet order — the user confirmed the poses themselves
 * jumped ("姿势乱跳，前后接不上"), which no fade can fix. The
 * [frameIndex] / [blend] overloads taking an `order` list map
 * the beat step through [DancerPoseOrder]'s re-sequenced tour:
 * beat step k shows frame `order[k mod N]`. The step timing,
 * the fade, and the wrap behaviour are exactly the ones above;
 * only which frame a step lands on changes.
 */
object PoseCycle {

    /** Frames advanced per beat (1 = one frame every beat). */
    const val FRAMES_PER_BEAT = 1

    /**
     * Portion of each beat spent cross-fading from the previous
     * frame into the current one (v1.0.10). The frame *rate* is
     * unchanged — still one frame per beat — but instead of a hard
     * cut at the beat boundary, the first ~35% of the beat blends
     * the two frames, which takes the slideshow edge off the dance.
     */
    const val FADE_FRACTION = 0.35

    // ---- v1.0.36: the fade window widens a little as songs get
    // faster. The user's "抖动得很厉害" complaint is mostly the
    // motion amplitudes (see DanceMotion.tempoFactor), but at
    // 140+ BPM a 35%-of-beat fade is only ~150 ms — pose changes
    // still land as snaps. [fadeFraction] keeps the normal-speed
    // 0.35 up to [FADE_TAPER_START_BPM], then eases the window
    // open to [FADE_FRACTION_FAST] at [FADE_TAPER_FULL_BPM] so
    // fast songs melt between poses instead of snapping. The
    // robot never blends at all (call-site exemption, unchanged).

    /** Fade window at the fastest tempos. */
    const val FADE_FRACTION_FAST = 0.50

    /** At or below this tempo the fade window is [FADE_FRACTION]. */
    const val FADE_TAPER_START_BPM = 120.0

    /** At this tempo the fade window reaches [FADE_FRACTION_FAST]. */
    const val FADE_TAPER_FULL_BPM = 150.0

    /**
     * The cross-fade window, as a fraction of one beat, at [bpm]:
     * [FADE_FRACTION] at or below 120 BPM, a smoothstep ease up
     * to [FADE_FRACTION_FAST] at 150 BPM, flat beyond. Pure
     * function of the tempo.
     */
    fun fadeFraction(bpm: Double): Double {
        if (bpm <= FADE_TAPER_START_BPM) return FADE_FRACTION.toDouble()
        val t = ((bpm - FADE_TAPER_START_BPM) / (FADE_TAPER_FULL_BPM - FADE_TAPER_START_BPM))
            .coerceIn(0.0, 1.0)
        val s = t * t * (3.0 - 2.0 * t)
        return FADE_FRACTION + (FADE_FRACTION_FAST - FADE_FRACTION) * s
    }

    /**
     * The two frames to draw for a beat, and how far the cross-fade
     * between them has run: draw [previousIndex] at alpha
     * `1 - currentAlpha` under [currentIndex] at [currentAlpha].
     * `currentAlpha == 1` means the fade is over and only the
     * current frame shows. Pure function of position, like
     * everything else here — pausing freezes the fade mid-blend.
     */
    data class Blend(
        val previousIndex: Int,
        val currentIndex: Int,
        val currentAlpha: Float,
    )

    fun frameIndex(
        positionMs: Long,
        bpm: Double,
        frameCount: Int,
        phaseOffsetBeats: Double = 0.0,
    ): Int {
        if (frameCount <= 1) return 0
        val safeBpm = bpm.coerceIn(40.0, 220.0)
        val beats = positionMs / (60_000.0 / safeBpm) + phaseOffsetBeats
        val step = floor(beats * FRAMES_PER_BEAT).toLong()
        return (((step % frameCount) + frameCount) % frameCount).toInt()
    }

    fun blend(
        positionMs: Long,
        bpm: Double,
        frameCount: Int,
        phaseOffsetBeats: Double = 0.0,
    ): Blend {
        if (frameCount <= 1) return Blend(0, 0, 1f)
        val safeBpm = bpm.coerceIn(40.0, 220.0)
        val beats = positionMs / (60_000.0 / safeBpm) + phaseOffsetBeats
        val stepped = beats * FRAMES_PER_BEAT
        val step = floor(stepped).toLong()
        // Phase inside the current frame's beat, always in [0, 1)
        // (floor keeps it there for negative positions too).
        val phase = stepped - step
        val current = (((step % frameCount) + frameCount) % frameCount).toInt()
        val previous = (((step - 1) % frameCount + frameCount) % frameCount).toInt()
        // v1.0.36: the fade window widens with tempo (see
        // fadeFraction); at normal speeds this is exactly the
        // v1.0.10 FADE_FRACTION division.
        val alpha = (phase / fadeFraction(safeBpm)).coerceIn(0.0, 1.0).toFloat()
        return Blend(previous, current, alpha)
    }

    // --- v1.0.17: the same stepping, visited in dance order ---

    /**
     * The frame on screen at [positionMs] when the cycle visits
     * frames in [order] (see [DancerPoseOrder]): the beat step is
     * computed exactly as in the sheet-order [frameIndex], then
     * mapped through the order — step k shows `order[k mod N]`.
     */
    fun frameIndex(
        positionMs: Long,
        bpm: Double,
        order: List<Int>,
        phaseOffsetBeats: Double = 0.0,
    ): Int {
        if (order.isEmpty()) return 0
        val step = frameIndex(positionMs, bpm, order.size, phaseOffsetBeats)
        return order[step]
    }

    /**
     * The cross-fade pair for the ordered cycle: the sheet-order
     * [blend] for the same position, with both indices mapped
     * through [order] — the previous frame is the order's previous
     * entry, so the fade melts between dance neighbours, and the
     * wrap fades from the tour's last frame back into its first.
     */
    fun blend(
        positionMs: Long,
        bpm: Double,
        order: List<Int>,
        phaseOffsetBeats: Double = 0.0,
    ): Blend {
        if (order.isEmpty()) return Blend(0, 0, 1f)
        val b = blend(positionMs, bpm, order.size, phaseOffsetBeats)
        return Blend(order[b.previousIndex], order[b.currentIndex], b.currentAlpha)
    }
}
