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
 */
object PoseCycle {

    /** Frames advanced per beat (1 = one frame every beat). */
    const val FRAMES_PER_BEAT = 1

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
}
