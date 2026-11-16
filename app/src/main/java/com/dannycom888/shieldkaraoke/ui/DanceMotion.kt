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

    enum class Profile { MODERN, ANCIENT, SUMO, BABY, ROBOT }

    /** Clap interval for the applause celebration, ms. */
    const val CLAP_INTERVAL_MS = 300L

    // ---- v1.0.22: the beat accent --------------------------------
    //
    // The user loves the dancing but asked for it to sit ON the
    // beat harder ("跳舞跟着拍子"). The frame stepping and the
    // bounce already derive from the beat grid; what was missing
    // is a felt downbeat. Each profile now adds a short downward
    // pulse — a dip that lands exactly on the beat and rebounds
    // within the beat — as the pose's sinkFraction. The pulse is
    // computed from the SAME `beats` value every other part of
    // the pose uses (position ÷ the song's measured BPM), never
    // from a second clock, so it cannot drift off the grid, and
    // pausing freezes it like everything else. Amplitudes follow
    // each profile's existing口径: modern hits hardest, ancient
    // stays soft, the sumo's accent runs at half frequency (one
    // per two beats) and heavy, the robot's is a hard step with
    // no decay curve, the baby's is a small extra wobble-dip.
    // Slow songs (< 80 BPM) scale the accent by the same 0.7
    // factor the bounce already uses.

    /** Envelope decay of the beat accent within one beat. */
    const val ACCENT_DECAY = 7.0

    /** Peak accent dip, modern profile (bounce-heavy). */
    const val ACCENT_MODERN = 0.12

    /** Peak accent dip, ancient profile (sway-heavy, soft). */
    const val ACCENT_ANCIENT = 0.06

    /** Peak accent dip, sumo — lands every second beat. */
    const val ACCENT_SUMO = 0.08

    /** Peak accent dip, baby (dancing phase only, never mid-fall). */
    const val ACCENT_BABY = 0.05

    /** Accent dip, robot — a hard step, held for one quarter beat. */
    const val ACCENT_ROBOT = 0.10f

    /**
     * The accent envelope at [beats]: [strength] exactly on the
     * beat, decaying exponentially across the beat that follows.
     * Pure function of the beat count, like everything here.
     */
    fun beatAccent(beats: Double, strength: Double, decay: Double = ACCENT_DECAY): Double {
        val frac = beats - floor(beats)
        return strength * exp(-frac * decay)
    }

    // ---- v1.0.36: the tempo taper --------------------------------
    //
    // The user (2026-11-15): on fast songs the dancer "抖动得很
    // 厉害" — shakes hard. The frame art is not the cause (the
    // five humans' adjacent-frame foot baselines measure within
    // a pixel of each other); the motion layer is. Bounce + sway
    // + the beat-accent dip stack on EVERY beat at full amplitude
    // regardless of tempo — pleasant at 90 BPM, but at 142 BPM
    // the whole stack pumps 2.4 times a second and reads as
    // shaking. So above [TEMPO_TAPER_START_BPM] the human
    // profiles' bounce, sway and accent (and the small scale
    // pulse riding with them) are scaled down by [tempoFactor]:
    // 1.0 up to 110 BPM, easing to ~0.40 at 140 BPM and to a
    // 0.35 floor at 160 BPM. The factor multiplies the existing
    // slow-song 0.7 gentling; the two never overlap (the 0.7
    // applies below 80 BPM, the taper above 110), so nothing is
    // double-discounted. The robot is exempt — his discrete
    // steps are his character — and the baby's fall state
    // machine is exempt; only his dancing-phase wobble and
    // accent are tapered, like the other humans'.

    /** Tempo taper starts here: at or below, factor is 1.0. */
    const val TEMPO_TAPER_START_BPM = 110.0

    /** By this tempo the factor has eased to [TEMPO_FACTOR_AT_140]. */
    const val TEMPO_TAPER_FULL_BPM = 140.0

    /** The factor at [TEMPO_TAPER_FULL_BPM]. */
    const val TEMPO_FACTOR_AT_140 = 0.40

    /** From this tempo on, the factor sits at [TEMPO_FACTOR_FLOOR]. */
    const val TEMPO_FLOOR_BPM = 160.0

    /** The lowest the tempo factor ever goes. */
    const val TEMPO_FACTOR_FLOOR = 0.35

    /**
     * How much of the human motion amplitude survives at [bpm]:
     * 1.0 at or below 110 BPM, a smoothstep ease down to 0.40 at
     * 140 BPM, then a gentler ease to the 0.35 floor at 160 BPM,
     * flat beyond. Pure function of the tempo, like everything
     * here — the same song at the same tempo always dances the
     * same.
     */
    fun tempoFactor(bpm: Double): Double {
        if (bpm <= TEMPO_TAPER_START_BPM) return 1.0
        if (bpm <= TEMPO_TAPER_FULL_BPM) {
            val t = (bpm - TEMPO_TAPER_START_BPM) / (TEMPO_TAPER_FULL_BPM - TEMPO_TAPER_START_BPM)
            val s = t * t * (3.0 - 2.0 * t)
            return 1.0 - (1.0 - TEMPO_FACTOR_AT_140) * s
        }
        val t = ((bpm - TEMPO_TAPER_FULL_BPM) / (TEMPO_FLOOR_BPM - TEMPO_TAPER_FULL_BPM))
            .coerceIn(0.0, 1.0)
        val s = t * t * (3.0 - 2.0 * t)
        return TEMPO_FACTOR_AT_140 - (TEMPO_FACTOR_AT_140 - TEMPO_FACTOR_FLOOR) * s
    }

    fun pose(profile: Profile, positionMs: Long, bpm: Double, phaseOffsetBeats: Double = 0.0): Pose {
        val safeBpm = bpm.coerceIn(40.0, 220.0)
        val beats = positionMs / (60_000.0 / safeBpm) + phaseOffsetBeats
        return when (profile) {
            Profile.ROBOT -> robotPose(beats, safeBpm)
            Profile.BABY -> BabyFall.pose(beats, safeBpm)
            Profile.MODERN -> humanPose(beats, safeBpm, bounceHeavy = true)
            Profile.ANCIENT -> humanPose(beats, safeBpm, bounceHeavy = false)
            Profile.SUMO -> sumoPose(beats, safeBpm)
        }
    }

    /**
     * The sumo (v1.0.12): heavy and grounded. The weight shift is
     * the dance — a slow side-to-side rock, one full sway cycle per
     * two beats at ±4.5°, while the bounce stays low and blunt
     * (flattened sine: he sinks rather than springs) with a small
     * knee-bend sink landing on each beat. No falls, no jumps.
     */
    private fun sumoPose(beats: Double, bpm: Double): Pose {
        val slow = if (bpm < 80.0) 0.7f else 1.0f
        // v1.0.36: the tempo taper (see tempoFactor) — his rock,
        // bounce, knee sink and accent all calm down as the song
        // speeds up, exactly like the other humans'.
        val tempo = tempoFactor(bpm).toFloat()
        // v1.0.41: sway amplitude 4.5° → 2.0° (see the sway note
        // at humanPose) — the rock is still his dance, it just no
        // longer throws his mass side to side.
        val rotation = sin(PI * beats) * 2.0 * slow * tempo
        val bounce = Math.pow(abs(sin(PI * beats)), 0.6) * 0.30 * slow * tempo
        val frac = beats - floor(beats)
        // Knee-bend sink on every beat, plus the v1.0.22 accent at
        // half frequency: the downbeat of each two-beat sway cycle
        // lands heavier than the off beat.
        val sink = exp(-frac * 6.0) * 0.10 * slow * tempo +
            beatAccent(beats / 2.0, ACCENT_SUMO, decay = 5.0) * slow * tempo
        val pulse = exp(-frac * 5.0) * 0.015 * tempo
        return Pose(
            bounceFraction = bounce.toFloat(),
            rotationDeg = rotation.toFloat(),
            scale = (1.0 + pulse).toFloat(),
            sinkFraction = sink.toFloat(),
        )
    }

    private fun humanPose(beats: Double, bpm: Double, bounceHeavy: Boolean): Pose {
        // v1.0.5: the pose FRAMES do the dancing now (see PoseCycle),
        // so the whole-body transform is only a gentle assist —
        // amplitudes roughly halved versus v1.0.3.
        val slow = if (bpm < 80.0) 0.7f else 1.0f // slow songs: bounce one notch down
        // v1.0.36: the tempo taper (see tempoFactor) multiplies
        // the slow-song gentling — the two never overlap (0.7
        // below 80 BPM, the taper above 110), and at or below
        // 110 BPM the factor is exactly 1.0, so normal-speed
        // songs dance bit-for-bit as before.
        val tempo = tempoFactor(bpm).toFloat()
        val bounceAmp = (if (bounceHeavy) 0.55f else 0.28f) * slow * tempo
        // v1.0.41 sway reduction: the user (2026-11-16) — the
        // dancers "flash" left-right and hurt the eyes. The frame
        // art half of that is fixed by the pelvis-anchor alignment
        // of the frames themselves (see
        // dancers/align_frames_v1041.py); this is the runtime
        // half: the whole-body sway stacked ±2.0°/±3.5° of
        // rotation on top of every frame change. All human sway
        // amplitudes are scaled by ~0.45 — modern 2.0° → 0.9°,
        // ancient 3.5° → 1.6°, the sumo's rock 4.5° → 2.0° (in
        // sumoPose), the baby's dancing wobble likewise (in
        // BabyFall). Bounce and the beat accent are NOT sway and
        // keep their amplitudes. The reduction multiplies with
        // the v1.0.36 tempo taper exactly as before (the taper
        // still applies on top, unchanged); the robot is exempt —
        // his stepped ±5° is his character — and the baby's fall
        // phases are exempt, as they were from the taper.
        val swayAmp = (if (bounceHeavy) 0.9f else 1.6f) * tempo
        val bounce = abs(sin(PI * beats)) * bounceAmp
        val rotation = sin(2 * PI * beats) * swayAmp
        val frac = beats - floor(beats)
        val pulse = exp(-frac * 5.0) * (if (bounceHeavy) 0.03 else 0.02) * tempo
        // v1.0.22 beat accent: a dip that lands on the beat and
        // rebounds inside it — strong for modern, soft for ancient.
        val accent = beatAccent(beats, if (bounceHeavy) ACCENT_MODERN else ACCENT_ANCIENT) * slow * tempo
        return Pose(
            bounceFraction = bounce.toFloat(),
            rotationDeg = rotation.toFloat(),
            scale = (1.0 + pulse).toFloat(),
            sinkFraction = accent.toFloat(),
        )
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
        // v1.0.22 beat accent, robot style: no decay curve — a
        // hard dip held for exactly the first quarter-beat step,
        // then gone. Quantised like everything else he does.
        val sink = if (step == 0) ACCENT_ROBOT * slow else 0f
        return Pose(bounces[step] * slow, rotations[step], scales[step], sink)
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
                // v1.0.36: the tempo taper (see
                // DanceMotion.tempoFactor) scales his dancing-phase
                // wobble and accent like the other humans' — only
                // the FALLING / DOWN / RISING phases are exempt,
                // because the fall is a timed gag (0.3 s down,
                // one beat down, 0.4 s up), not a beat amplitude.
                val tempo = DanceMotion.tempoFactor(bpm)
                val wobble = beats * 1.5
                val bounce = abs(sin(PI * wobble)) * 0.35f * tempo
                // v1.0.41: dancing-phase sway scaled by ~0.45
                // like the other humans (5.0 → 2.25, 1.8 → 0.8);
                // the FALLING / DOWN / RISING phases below are
                // NOT scaled — the fall is a timed gag, exempt
                // here exactly as it is from the tempo taper.
                val rotation = (sin(2 * PI * wobble) * 2.25 + sin(PI * beats) * 0.8) * tempo
                // v1.0.22: a small beat accent on the song's beat
                // grid (not the 1.5x wobble), dancing phase only —
                // the fall phases own the sink while he is down.
                val slow = if (bpm < 80.0) 0.7 else 1.0
                val accent = DanceMotion.beatAccent(beats, DanceMotion.ACCENT_BABY) * slow * tempo
                DanceMotion.Pose(
                    bounce.toFloat(), rotation.toFloat(), 1.0f,
                    sinkFraction = accent.toFloat(),
                )
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
