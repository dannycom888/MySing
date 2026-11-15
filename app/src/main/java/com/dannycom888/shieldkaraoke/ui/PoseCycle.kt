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
        val alpha = (phase / FADE_FRACTION).coerceIn(0.0, 1.0).toFloat()
        return Blend(previous, current, alpha)
    }
}
