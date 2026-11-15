package com.dannycom888.shieldkaraoke.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Beat-driven dance poses for the little dancers on the player
 * screen. Everything is a pure function of the *playback position*
 * (never a wall clock), so pausing freezes the dancers and seeking
 * lands them back on the beat. Bounce is a fraction of the dancer's
 * height; the UI turns it into pixels.
 */
object DanceMotion {

    data class Pose(
        /** Upward bounce, 0..1 of the max bounce height. */
        val bounceFraction: Float,
        /** Side-to-side sway, degrees. */
        val rotationDeg: Float,
        /** Landing pulse scale, ~1.0. */
        val scale: Float,
        /** Downward sink (baby falls), 0..1. */
        val sinkFraction: Float = 0f,
    )

    enum class Profile { MODERN, ANCIENT, BABY, ROBOT }

    /** Clap interval for the applause celebration, ms. */
    const val CLAP_INTERVAL_MS = 300L

    fun pose(profile: Profile, positionMs: Long, bpm: Double, phaseOffsetBeats: Double = 0.0): Pose {
        val safeBpm = bpm.coerceIn(40.0, 220.0)
        val beats = positionMs / (60_000.0 / safeBpm) + phaseOffsetBeats
        return when (profile) {
            Profile.ROBOT -> robotPose(beats, safeBpm)
            Profile.BABY -> BabyFall.pose(beats, safeBpm)
            Profile.MODERN -> humanPose(beats, safeBpm, bounceHeavy = true)
            Profile.ANCIENT -> humanPose(beats, safeBpm, bounceHeavy = false)
        }
    }

    private fun humanPose(beats: Double, bpm: Double, bounceHeavy: Boolean): Pose {
        // v1.0.5: the pose FRAMES do the dancing now (see PoseCycle),
        // so the whole-body transform is only a gentle assist —
        // amplitudes roughly halved versus v1.0.3.
        val slow = if (bpm < 80.0) 0.7f else 1.0f // slow songs: bounce one notch down
        val bounceAmp = (if (bounceHeavy) 0.55f else 0.28f) * slow
        val swayAmp = if (bounceHeavy) 2.0f else 3.5f
        val bounce = abs(sin(PI * beats)) * bounceAmp
        val rotation = sin(2 * PI * beats) * swayAmp
        val frac = beats - floor(beats)
        val pulse = exp(-frac * 5.0) * (if (bounceHeavy) 0.03 else 0.02)
        return Pose(bounce.toFloat(), rotation.toFloat(), (1.0 + pulse).toFloat())
    }

    /** The robot dances in mechanical steps: the pose is quantised
     *  to quarter-beat steps from lookup tables — no sine smoothing.
     *  Bounce reduced in v1.0.5 like the humans'; the stepped
     *  rotations stay (the frame stepping is itself stepped). */
    private fun robotPose(beats: Double, bpm: Double): Pose {
        val slow = if (bpm < 80.0) 0.7f else 1.0f
        val step = (floor(beats * 4).toInt() % 4 + 4) % 4
        val rotations = floatArrayOf(-5f, -5f, 5f, 5f)
        val bounces = floatArrayOf(0f, 0.55f, 0.2f, 0f)
        val scales = floatArrayOf(1f, 1.04f, 1.04f, 1f)
        return Pose(bounces[step] * slow, rotations[step], scales[step])
    }

    /**
     * Applause celebration pose: all dancers clap in sync to a fixed
     * 300 ms clap beat (driven by elapsed wall time because the
     * player sits at ENDED). The robot claps in stepped motion too.
     * The baby never falls while clapping.
     */
    fun clapPose(profile: Profile, elapsedMs: Long): Pose {
        val phase = elapsedMs.coerceAtLeast(0) / CLAP_INTERVAL_MS.toDouble()
        if (profile == Profile.ROBOT) {
            val step = (floor(phase * 2).toInt() % 2 + 2) % 2
            return Pose(
                bounceFraction = if (step == 0) 0.30f else 0.0f,
                rotationDeg = if (step == 0) -4f else 4f,
                scale = 1.02f,
            )
        }
        val bounce = abs(sin(PI * phase)) * 0.35f
        val rotation = sin(2 * PI * phase) * 4.0f
        return Pose(bounce.toFloat(), rotation.toFloat(), 1.0f)
    }
}

/**
 * The baby dancer: normally an unsteady toddler wobble (bigger sway,
 * slightly faster bounce), and every so often he falls over — tips
 * sideways in ~0.3 s, lies on the floor for one beat, then pops back
 * up in ~0.4 s with an overshoot bounce. The fall interval is a
 * deterministic pseudo-random 12–20 beats derived from the cycle
 * index, so it never ticks like a metronome. Pure function of the
 * beat count, fully unit tested.
 */
object BabyFall {

    enum class Phase { DANCING, FALLING, DOWN, RISING }

    data class State(val phase: Phase, val progress: Float)

    /** Beats of plain dancing before fall number [cycle] starts. */
    fun intervalForCycle(cycle: Int): Int {
        val h = (cycle.toLong() + 1) * 2654435761L
        return 12 + ((h ushr 8) % 9).toInt() // 12..20 beats
    }

    private fun fallBeats(bpm: Double) = 0.3 * bpm / 60.0 // ~0.3 s
    private fun riseBeats(bpm: Double) = 0.4 * bpm / 60.0 // ~0.4 s
    private const val DOWN_BEATS = 1.0

    fun cycleLengthBeats(cycle: Int, bpm: Double): Double =
        intervalForCycle(cycle) + fallBeats(bpm) + DOWN_BEATS + riseBeats(bpm)

    /** Where in the fall cycle [beats] (absolute beat count) sits. */
    fun stateAt(beats: Double, bpm: Double): State {
        if (beats < 0) return State(Phase.DANCING, 0f)
        var start = 0.0
        var cycle = 0
        while (true) {
            val dance = intervalForCycle(cycle).toDouble()
            val fall = fallBeats(bpm)
            val rise = riseBeats(bpm)
            val offset = beats - start
            when {
                offset < dance -> return State(Phase.DANCING, 0f)
                offset < dance + fall ->
                    return State(Phase.FALLING, ((offset - dance) / fall).toFloat())
                offset < dance + fall + DOWN_BEATS ->
                    return State(Phase.DOWN, ((offset - dance - fall) / DOWN_BEATS).toFloat())
                offset < dance + fall + DOWN_BEATS + rise ->
                    return State(Phase.RISING, ((offset - dance - fall - DOWN_BEATS) / rise).toFloat())
            }
            start += dance + fall + DOWN_BEATS + rise
            cycle++
        }
    }

    fun pose(beats: Double, bpm: Double): DanceMotion.Pose {
        val state = stateAt(beats, bpm)
        return when (state.phase) {
            Phase.DANCING -> {
                // Unsteady toddler: faster, bigger sway + a slow extra
                // wobble. Amplitudes trimmed in v1.0.5 (the pose
                // frames dance too) but still the wobbliest dancer.
                val wobble = beats * 1.5
                val bounce = abs(sin(PI * wobble)) * 0.35f
                val rotation = sin(2 * PI * wobble) * 5.0 + sin(PI * beats) * 1.8
                DanceMotion.Pose(bounce.toFloat(), rotation.toFloat(), 1.0f)
            }
            Phase.FALLING -> {
                val t = state.progress.toDouble()
                DanceMotion.Pose(
                    bounceFraction = 0f,
                    rotationDeg = (82.0 * t * t).toFloat(), // accelerating tip-over
                    scale = 1.0f,
                    sinkFraction = (0.38 * t).toFloat(),
                )
            }
            Phase.DOWN -> DanceMotion.Pose(
                bounceFraction = 0f,
                rotationDeg = 84f,
                scale = 1.0f,
                sinkFraction = 0.38f,
            )
            Phase.RISING -> {
                val t = state.progress.toDouble()
                DanceMotion.Pose(
                    bounceFraction = 0f,
                    // Quadratic settle + a growing counter-swing: near
                    // the end he rocks a few degrees past upright.
                    rotationDeg = (84.0 * (1 - t) * (1 - t) - 14.0 * sin(PI * t) * t).toFloat(),
                    scale = (1.0 + sin(PI * t) * 0.09).toFloat(), // pop back up with a bounce
                    sinkFraction = (0.38 * (1 - t)).toFloat(),
                )
            }
        }
    }
}
