package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DanceMotionTest {

    @Test
    fun poseIsDeterministic() {
        val a = DanceMotion.pose(DanceMotion.Profile.MODERN, 12_345L, 96.0)
        val b = DanceMotion.pose(DanceMotion.Profile.MODERN, 12_345L, 96.0)
        assertEquals(a, b)
    }

    @Test
    fun pauseFreezesPose() {
        // Same position -> same pose, always: the pose is a pure
        // function of playback position, so a paused song's dancer
        // cannot drift.
        repeat(3) {
            assertEquals(
                DanceMotion.pose(DanceMotion.Profile.ANCIENT, 5_000L, 120.0),
                DanceMotion.pose(DanceMotion.Profile.ANCIENT, 5_000L, 120.0),
            )
        }
    }

    @Test
    fun humanPoseStaysInRange() {
        for (profile in listOf(DanceMotion.Profile.MODERN, DanceMotion.Profile.ANCIENT)) {
            var pos = 0L
            while (pos < 20_000L) {
                val p = DanceMotion.pose(profile, pos, 120.0)
                assertTrue(p.bounceFraction in 0f..1f)
                assertTrue(abs(p.rotationDeg) <= 5.01f)
                assertTrue(p.scale in 1.0f..1.06f)
                // v1.0.22: the sink is the beat accent now — no
                // longer identically zero, but never past the
                // modern peak (0.12) plus a rounding whisker.
                assertTrue(p.sinkFraction in 0f..0.13f)
                pos += 37
            }
        }
    }

    @Test
    fun ancientSwaysMoreAndBouncesLessThanModern() {
        fun maxOver(profile: DanceMotion.Profile, pick: (DanceMotion.Pose) -> Float): Float {
            var best = 0f
            var pos = 0L
            while (pos < 10_000L) {
                best = maxOf(best, abs(pick(DanceMotion.pose(profile, pos, 120.0))))
                pos += 10
            }
            return best
        }
        assertTrue(
            maxOver(DanceMotion.Profile.ANCIENT) { it.rotationDeg } >
                maxOver(DanceMotion.Profile.MODERN) { it.rotationDeg },
        )
        assertTrue(
            maxOver(DanceMotion.Profile.MODERN) { it.bounceFraction } >
                maxOver(DanceMotion.Profile.ANCIENT) { it.bounceFraction },
        )
    }

    @Test
    fun slowSongsBounceLess() {
        fun maxBounce(bpm: Double): Float {
            var best = 0f
            var pos = 0L
            while (pos < 20_000L) {
                best = maxOf(best, DanceMotion.pose(DanceMotion.Profile.MODERN, pos, bpm).bounceFraction)
                pos += 10
            }
            return best
        }
        assertTrue(maxBounce(70.0) < maxBounce(120.0))
    }

    @Test
    fun robotMovesInStepsNotSine() {
        // At 60 BPM a quarter-beat step is 250 ms: the pose must be
        // constant inside a step and jump between steps.
        val bpm = 60.0
        assertEquals(
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 0L, bpm),
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 100L, bpm),
        )
        assertNotEquals(
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 0L, bpm),
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 300L, bpm),
        )
        // Rotation only ever takes the table values.
        val seen = mutableSetOf<Float>()
        var pos = 0L
        while (pos < 8_000L) {
            seen += DanceMotion.pose(DanceMotion.Profile.ROBOT, pos, bpm).rotationDeg
            pos += 10
        }
        assertEquals(setOf(-5f, 5f), seen)
    }

    @Test
    fun clapIsSynchronisedAcrossDancers() {
        for (elapsed in listOf(0L, 90L, 160L, 310L, 700L, 1_500L)) {
            val modern = DanceMotion.clapPose(DanceMotion.Profile.MODERN, elapsed)
            val ancient = DanceMotion.clapPose(DanceMotion.Profile.ANCIENT, elapsed)
            val baby = DanceMotion.clapPose(DanceMotion.Profile.BABY, elapsed)
            assertEquals(modern, ancient)
            assertEquals(modern, baby)
            assertEquals(0f, baby.sinkFraction) // baby never falls while clapping
        }
    }

    // ---- v1.0.22: the beat accent — a dip that lands exactly on
    // the beat and rebounds inside it, from the same beat phase
    // as the frame stepping (no second clock).

    @Test
    fun accentPeaksExactlyOnTheBeat() {
        // v1.0.36: run at 96 BPM (one beat is 625 ms) — inside
        // the tempo taper's flat zone (<= 110 BPM), so the accent
        // still peaks at the full ACCENT_MODERN, exactly as
        // before the taper existed. (At 120 BPM the peak is the
        // tapered value; see fastSongsAreCalmerByTheTempoFactor.)
        val onBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 0L, 96.0)
        assertEquals(DanceMotion.ACCENT_MODERN.toFloat(), onBeat.sinkFraction, 0.001f)
        // Half a beat later the accent has decayed to ~nothing.
        val midBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 312L, 96.0)
        assertTrue(midBeat.sinkFraction < 0.01f)
        assertTrue(midBeat.sinkFraction < onBeat.sinkFraction)
        // The next beat line peaks again — it is a grid, not a
        // one-shot.
        val nextBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 625L, 96.0)
        assertEquals(onBeat.sinkFraction, nextBeat.sinkFraction, 0.001f)
    }

    @Test
    fun accentEnvelopeDecaysWithinTheBeat() {
        assertEquals(0.12, DanceMotion.beatAccent(3.0, 0.12), 1e-9)
        assertTrue(DanceMotion.beatAccent(3.25, 0.12) < DanceMotion.beatAccent(3.0, 0.12))
        assertTrue(DanceMotion.beatAccent(3.9, 0.12) < 0.01)
        // Same phase next beat, same value: pure beat function.
        assertEquals(
            DanceMotion.beatAccent(3.3, 0.12),
            DanceMotion.beatAccent(9.3, 0.12),
            1e-9,
        )
    }

    @Test
    fun modernAccentHitsHarderThanAncient() {
        // v1.0.36: at 96 BPM (tempo factor exactly 1.0) so the
        // ancient peak is the untapered ACCENT_ANCIENT.
        val modern = DanceMotion.pose(DanceMotion.Profile.MODERN, 0L, 96.0)
        val ancient = DanceMotion.pose(DanceMotion.Profile.ANCIENT, 0L, 96.0)
        assertEquals(DanceMotion.ACCENT_ANCIENT.toFloat(), ancient.sinkFraction, 0.001f)
        assertTrue(modern.sinkFraction > ancient.sinkFraction)
    }

    @Test
    fun sumoAccentRunsAtHalfFrequency() {
        // 120 BPM: beats at 0 / 500 / 1000 ms. The even beats get
        // knee sink + accent stacked; the odd beat gets the knee
        // sink with the accent almost fully decayed.
        val down = DanceMotion.pose(DanceMotion.Profile.SUMO, 0L, 120.0)
        val off = DanceMotion.pose(DanceMotion.Profile.SUMO, 500L, 120.0)
        val downAgain = DanceMotion.pose(DanceMotion.Profile.SUMO, 1_000L, 120.0)
        assertTrue(down.sinkFraction > off.sinkFraction + 0.04f)
        assertEquals(down.sinkFraction, downAgain.sinkFraction, 0.001f)
    }

    @Test
    fun robotAccentIsAHardStep() {
        // 120 BPM (above the slow-song threshold, so no 0.7
        // gentling): quarter-beat steps are 125 ms. The dip is
        // fully on for the first step of the beat and fully off
        // after — no decay curve anywhere.
        val bpm = 120.0
        assertEquals(
            DanceMotion.ACCENT_ROBOT,
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 0L, bpm).sinkFraction,
        )
        assertEquals(
            DanceMotion.ACCENT_ROBOT,
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 124L, bpm).sinkFraction,
        )
        assertEquals(0f, DanceMotion.pose(DanceMotion.Profile.ROBOT, 125L, bpm).sinkFraction)
        assertEquals(0f, DanceMotion.pose(DanceMotion.Profile.ROBOT, 499L, bpm).sinkFraction)
    }

    @Test
    fun slowSongsGetAGentlerAccent() {
        fun maxSink(bpm: Double): Float {
            var best = 0f
            var pos = 0L
            while (pos < 10_000L) {
                best = maxOf(best, DanceMotion.pose(DanceMotion.Profile.MODERN, pos, bpm).sinkFraction)
                pos += 5
            }
            return best
        }
        assertTrue(maxSink(70.0) < maxSink(120.0))
    }

    // ---- v1.0.36: the tempo taper — human motion amplitudes
    // scale down as songs get faster, so a 140+ BPM song no
    // longer pumps the whole bounce/sway/accent stack at full
    // strength 2.4 times a second ("跳舞时人抖动得很厉害").

    @Test
    fun tempoFactorCurvePins() {
        // Flat 1.0 through the start of the taper.
        assertEquals(1.0, DanceMotion.tempoFactor(60.0), 1e-9)
        assertEquals(1.0, DanceMotion.tempoFactor(96.0), 1e-9)
        assertEquals(1.0, DanceMotion.tempoFactor(110.0), 1e-9)
        // Smoothstep down the 110 -> 140 ramp: 120 sits at
        // 1 - 0.6 * smoothstep(1/3) = 1 - 0.6 * 7/27.
        assertEquals(0.844444, DanceMotion.tempoFactor(120.0), 1e-6)
        assertEquals(0.70, DanceMotion.tempoFactor(125.0), 1e-9)
        assertEquals(0.40, DanceMotion.tempoFactor(140.0), 1e-9)
        // The gentler second ease to the floor.
        assertEquals(0.375, DanceMotion.tempoFactor(150.0), 1e-9)
        assertEquals(0.35, DanceMotion.tempoFactor(160.0), 1e-9)
        assertEquals(0.35, DanceMotion.tempoFactor(220.0), 1e-9)
        // Monotonic non-increasing across the whole taper.
        var prev = Double.MAX_VALUE
        var bpm = 100.0
        while (bpm <= 180.0) {
            val f = DanceMotion.tempoFactor(bpm)
            assertTrue("factor rose at $bpm", f <= prev + 1e-12)
            prev = f
            bpm += 5.0
        }
    }

    @Test
    fun normalSpeedSongsDanceExactlyAsBeforeTheTaper() {
        // Regression pins at 96 BPM (tempo factor exactly 1.0,
        // slow factor 1.0): the values below are the formulas
        // evaluated by hand — if the taper ever leaks into the
        // flat zone, these break. The bounce / scale / sink pins
        // are unchanged since v1.0.22; the ROTATION pins were
        // re-based in v1.0.41, when all human sway amplitudes
        // were scaled by ~0.45 (modern 2.0° → 0.9°, ancient
        // 3.5° → 1.6°, sumo 4.5° → 2.0°) against the left-right
        // "flash" — same beat, same formulas, new sway values.
        // MODERN at beat 1.6 (1000 ms at 96 BPM).
        val modern = DanceMotion.pose(DanceMotion.Profile.MODERN, 1_000L, 96.0)
        assertEquals(0.523086f, modern.bounceFraction, 0.001f)
        assertEquals(-0.529007f, modern.rotationDeg, 0.001f)
        assertEquals(1.001494f, modern.scale, 0.001f)
        assertEquals(0.001799f, modern.sinkFraction, 0.001f)
        // ANCIENT at the same beat.
        val ancient = DanceMotion.pose(DanceMotion.Profile.ANCIENT, 1_000L, 96.0)
        assertEquals(0.266298f, ancient.bounceFraction, 0.001f)
        assertEquals(-0.940456f, ancient.rotationDeg, 0.001f)
        assertEquals(1.000996f, ancient.scale, 0.001f)
        assertEquals(0.000900f, ancient.sinkFraction, 0.001f)
        // SUMO at the same beat.
        val sumo = DanceMotion.pose(DanceMotion.Profile.SUMO, 1_000L, 96.0)
        assertEquals(0.291104f, sumo.bounceFraction, 0.001f)
        assertEquals(-1.902113f, sumo.rotationDeg, 0.001f)
        assertEquals(1.000747f, sumo.scale, 0.001f)
        assertEquals(0.004198f, sumo.sinkFraction, 0.001f)
        // BABY dancing at beat 1.0 (cycle 0 dances 12+ beats, so
        // beat 1 is safely inside DANCING): full wobble amplitude.
        val baby = BabyFall.pose(1.0, 96.0)
        assertEquals(0.35f, baby.bounceFraction, 0.001f)
        assertEquals(0.05f, baby.sinkFraction, 0.001f)
    }

    @Test
    fun fastSongsAreCalmerByTheTempoFactor() {
        // At 140 BPM the modern accent peak is 0.40x the full
        // ACCENT_MODERN — the stack no longer pumps full-size.
        val onBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 0L, 140.0)
        assertEquals(
            (DanceMotion.ACCENT_MODERN * 0.40).toFloat(),
            onBeat.sinkFraction, 0.001f,
        )
        fun maxBounce(bpm: Double): Float {
            var best = 0f
            var pos = 0L
            while (pos < 20_000L) {
                best = maxOf(best, DanceMotion.pose(DanceMotion.Profile.MODERN, pos, bpm).bounceFraction)
                pos += 10
            }
            return best
        }
        assertEquals(0.55f, maxBounce(96.0), 0.01f)
        assertEquals(0.55f * 0.40f, maxBounce(140.0), 0.01f)
        assertTrue(maxBounce(160.0) < maxBounce(140.0))
        // The baby's dancing wobble is tapered too.
        val babyFast = BabyFall.pose(1.0, 140.0)
        assertEquals(0.35f * 0.40f, babyFast.bounceFraction, 0.001f)
        assertEquals(0.05f * 0.40f, babyFast.sinkFraction, 0.001f)
    }

    @Test
    fun robotIsExemptFromTheTempoTaper() {
        // 150 BPM (factor 0.375 for humans): the robot's hard
        // accent step and his scale table are bit-for-bit the
        // pre-taper values — stepped is his character.
        assertEquals(
            DanceMotion.ACCENT_ROBOT,
            DanceMotion.pose(DanceMotion.Profile.ROBOT, 0L, 150.0).sinkFraction,
        )
        // Quarter-beat step 1 at 150 BPM is 100 ms in.
        val step1 = DanceMotion.pose(DanceMotion.Profile.ROBOT, 100L, 150.0)
        assertEquals(1.04f, step1.scale)
        assertEquals(0.55f, step1.bounceFraction)
        assertEquals(-5f, step1.rotationDeg)
    }

    @Test
    fun babyFallPhasesAreExemptFromTheTempoTaper() {
        // The fall is a timed gag, not a beat amplitude: even at
        // 150 BPM a DOWN baby lies at the full 84 degrees / 0.38
        // sink the state machine has always used.
        var beats = 0.0
        var found = false
        while (beats < 300.0) {
            if (BabyFall.stateAt(beats, 150.0).phase == BabyFall.Phase.DOWN) {
                val p = BabyFall.pose(beats, 150.0)
                assertEquals(84f, p.rotationDeg)
                assertEquals(0.38f, p.sinkFraction)
                found = true
                break
            }
            beats += 0.01
        }
        assertTrue(found)
    }

    @Test
    fun robotClapIsStepped() {
        val seen = mutableSetOf<Float>()
        var t = 0L
        while (t < 3_000L) {
            seen += DanceMotion.clapPose(DanceMotion.Profile.ROBOT, t).rotationDeg
            t += 20
        }
        assertEquals(setOf(-4f, 4f), seen)
    }
}

class BabyFallTest {

    @Test
    fun intervalsArePseudoRandomBetween12And20() {
        val intervals = (0..60).map { BabyFall.intervalForCycle(it) }
        assertTrue(intervals.all { it in 12..20 })
        assertTrue("intervals must not be metronome-regular", intervals.toSet().size > 3)
    }

    @Test
    fun fallsHappenRegularlyAndInOrder() {
        val bpm = 96.0
        val order = mapOf(
            BabyFall.Phase.DANCING to listOf(BabyFall.Phase.FALLING),
            BabyFall.Phase.FALLING to listOf(BabyFall.Phase.DOWN),
            BabyFall.Phase.DOWN to listOf(BabyFall.Phase.RISING),
            BabyFall.Phase.RISING to listOf(BabyFall.Phase.DANCING),
        )
        var last = BabyFall.Phase.DANCING
        var downEpisodes = 0
        var beats = 0.0
        while (beats < 1500.0) {
            val s = BabyFall.stateAt(beats, bpm)
            if (s.phase != last) {
                assertTrue(
                    "illegal transition $last -> ${s.phase} at beat $beats",
                    order.getValue(last).contains(s.phase),
                )
                if (s.phase == BabyFall.Phase.DOWN) downEpisodes++
                last = s.phase
            }
            beats += 0.01
        }
        assertTrue("expected many falls in 1500 beats, got $downEpisodes", downEpisodes >= 40)
    }

    @Test
    fun downPoseIsFlatOnTheFloor() {
        // Find a DOWN moment and check the pose: rotated ~flat, sunk.
        val bpm = 120.0
        var beats = 0.0
        var found = false
        while (beats < 200.0) {
            if (BabyFall.stateAt(beats, bpm).phase == BabyFall.Phase.DOWN) {
                val p = BabyFall.pose(beats, bpm)
                assertTrue("down rotation ${p.rotationDeg}", p.rotationDeg >= 70f)
                assertTrue("down sink ${p.sinkFraction}", p.sinkFraction >= 0.3f)
                assertEquals(0f, p.bounceFraction)
                found = true
                break
            }
            beats += 0.01
        }
        assertTrue(found)
    }

    // ---- v1.0.53: the get-up is three stages — push up to
    // half-risen (0.25 s), stand to upright (0.35 s), then one
    // small steadying sway (0.30 s); total 0.90 s. The old
    // single 0.4 s pop (overshoot swing to −14° + scale bounce
    // to 1.09) is retired — these tests pin its replacement.

    @Test
    fun riseStagesAddUpToTheTotalRise() {
        assertEquals(
            BabyFall.RISE_TOTAL_SECONDS,
            BabyFall.RISE_PUSH_SECONDS + BabyFall.RISE_STAND_SECONDS +
                BabyFall.RISE_SETTLE_SECONDS,
            0.0001,
        )
        assertEquals(0.90, BabyFall.RISE_TOTAL_SECONDS, 0.0001)
        // The cycle length carries the longer rise: at 120 BPM
        // the fall is 0.6 beats, the lie is 1 beat, the rise is
        // 0.9 s = 1.8 beats.
        val cycle = 3
        assertEquals(
            BabyFall.intervalForCycle(cycle) + 0.6 + 1.0 + 1.8,
            BabyFall.cycleLengthBeats(cycle, 120.0),
            0.0001,
        )
    }

    @Test
    fun riseStageBoundariesFollowTheStageDurations() {
        val pushEnd = (BabyFall.RISE_PUSH_SECONDS / BabyFall.RISE_TOTAL_SECONDS).toFloat()
        val standEnd = ((BabyFall.RISE_PUSH_SECONDS + BabyFall.RISE_STAND_SECONDS) /
            BabyFall.RISE_TOTAL_SECONDS).toFloat()
        assertEquals(BabyFall.RiseStage.PUSH, BabyFall.riseStage(0f))
        assertEquals(BabyFall.RiseStage.PUSH, BabyFall.riseStage(pushEnd - 0.001f))
        assertEquals(BabyFall.RiseStage.STAND, BabyFall.riseStage(pushEnd + 0.001f))
        assertEquals(BabyFall.RiseStage.STAND, BabyFall.riseStage(standEnd - 0.001f))
        assertEquals(BabyFall.RiseStage.SETTLE, BabyFall.riseStage(standEnd + 0.001f))
        assertEquals(BabyFall.RiseStage.SETTLE, BabyFall.riseStage(1f))
        // Not rising at all → no stage.
        assertNull(BabyFall.riseStageAt(0.0, 120.0))
    }

    @Test
    fun riseStagesRunInOrderAndDancingResumes() {
        val bpm = 96.0
        val stages = mutableListOf<BabyFall.RiseStage>()
        var sawDancingAfter = false
        var inRise = false
        var beats = 0.0
        while (beats < 120.0) {
            val stage = BabyFall.riseStageAt(beats, bpm)
            if (stage != null) {
                inRise = true
                if (stages.lastOrNull() != stage) stages += stage
            } else if (inRise) {
                sawDancingAfter =
                    BabyFall.stateAt(beats, bpm).phase == BabyFall.Phase.DANCING
                break
            }
            beats += 0.005
        }
        assertEquals(
            listOf(
                BabyFall.RiseStage.PUSH,
                BabyFall.RiseStage.STAND,
                BabyFall.RiseStage.SETTLE,
            ),
            stages,
        )
        assertTrue("dancing should resume after the settle", sawDancingAfter)
    }

    @Test
    fun halfRisenAtThePushStandJoint() {
        // Beat arithmetic for cycle 0 at 120 BPM: dancing for
        // the interval, 0.6 beats falling, 1 beat down, then the
        // rise starts; the push stage is 0.25 s = 0.5 beats.
        val bpm = 120.0
        val riseStart = BabyFall.intervalForCycle(0) + 0.6 + 1.0
        val joint = riseStart + 0.5
        val atJoint = BabyFall.pose(joint - 0.01, bpm)
        val justPast = BabyFall.pose(joint + 0.01, bpm)
        // Either side of the joint he is at the half-risen lean
        // (35–40° band; the design point is 37.5°).
        assertEquals(BabyFall.RISE_HALF_DEG.toFloat(), atJoint.rotationDeg, 1.0f)
        assertEquals(BabyFall.RISE_HALF_DEG.toFloat(), justPast.rotationDeg, 1.0f)
        // And the sink tracks the lean — about half the flat sink.
        assertEquals(
            (BabyFall.DOWN_SINK * BabyFall.RISE_HALF_DEG / BabyFall.DOWN_ROTATION_DEG)
                .toFloat(),
            atJoint.sinkFraction, 0.02f,
        )
    }

    @Test
    fun riseHasNoSnapAndNoScalePop() {
        // Across the whole get-up: the lean never passes upright
        // by more than the settle sway (the old pop swung to
        // −14°), never exceeds the flat angle, the scale never
        // pops (the old rise bounced it to 1.09) and there is
        // no bounce — he is climbing, not jumping.
        val bpm = 96.0
        var beats = 0.0
        var sawRise = false
        while (beats < 120.0) {
            val s = BabyFall.stateAt(beats, bpm)
            if (s.phase == BabyFall.Phase.RISING) {
                sawRise = true
                val p = BabyFall.pose(beats, bpm)
                assertTrue(
                    "rotation ${p.rotationDeg} at beat $beats",
                    p.rotationDeg >= -BabyFall.SETTLE_SWAY_DEG.toFloat() - 0.01f,
                )
                assertTrue(
                    "rotation ${p.rotationDeg} at beat $beats",
                    p.rotationDeg <= BabyFall.DOWN_ROTATION_DEG.toFloat() + 0.01f,
                )
                assertTrue("scale popped at beat $beats: ${p.scale}", p.scale == 1.0f)
                assertEquals(0f, p.bounceFraction)
            }
            beats += 0.005
        }
        assertTrue(sawRise)
    }

    @Test
    fun settleSwayIsOneSmallDampedSwing() {
        val bpm = 120.0
        val riseStart = BabyFall.intervalForCycle(0) + 0.6 + 1.0
        // The settle starts after push + stand = 0.6 s = 1.2 beats.
        val settleStart = riseStart + 1.2
        var max = 0f
        var min = 0f
        var beats = settleStart
        while (beats < settleStart + 0.6) { // 0.30 s = 0.6 beats at 120 BPM
            val p = BabyFall.pose(beats, bpm)
            max = maxOf(max, p.rotationDeg)
            min = minOf(min, p.rotationDeg)
            beats += 0.005
        }
        // One swing each way, both inside the ±2° budget, the
        // second smaller than the first (it is dying out)…
        assertTrue("settle peak $max", max in 1.4f..2.0f)
        assertTrue("settle trough $min", min in -2.0f..-0.7f)
        assertTrue("trough should be damped below the peak", -min < max)
        // …and it lands upright as the stage ends.
        val atEnd = BabyFall.pose(settleStart + 0.6 - 0.001, bpm)
        assertTrue(
            "settle should end upright, was ${atEnd.rotationDeg}",
            abs(atEnd.rotationDeg) < 0.1f,
        )
    }

    @Test
    fun babyPoseIsDeterministic() {
        assertEquals(BabyFall.pose(333.3, 96.0), BabyFall.pose(333.3, 96.0))
    }

    // ---- v1.0.12: the sumo （姿三四郎） — sway-dominant, low blunt
    // bounce, a knee-bend sink on the beat, and never a fall.

    @Test
    fun sumoSwaysWiderThanModernButBouncesLower() {
        fun maxOver(profile: DanceMotion.Profile, pick: (DanceMotion.Pose) -> Float): Float {
            var best = 0f
            var pos = 0L
            while (pos < 20_000L) {
                best = maxOf(best, abs(pick(DanceMotion.pose(profile, pos, 120.0))))
                pos += 37L
            }
            return best
        }
        assertTrue(
            maxOver(DanceMotion.Profile.SUMO) { it.rotationDeg } >
                maxOver(DanceMotion.Profile.MODERN) { it.rotationDeg },
        )
        assertTrue(
            maxOver(DanceMotion.Profile.SUMO) { it.bounceFraction } <
                maxOver(DanceMotion.Profile.MODERN) { it.bounceFraction },
        )
    }

    @Test
    fun sumoSinksOnTheBeatAndStaysInRange() {
        var sawSink = false
        var sawLeft = false
        var sawRight = false
        var pos = 0L
        while (pos < 20_000L) {
            val p = DanceMotion.pose(DanceMotion.Profile.SUMO, pos, 120.0)
            assertTrue(p.bounceFraction in 0f..1f)
            // v1.0.41: the rock's amplitude is 2.0° now (was
            // 4.5°) — bound and both-ways thresholds re-based.
            assertTrue(abs(p.rotationDeg) <= 2.01f)
            assertTrue(p.scale in 1.0f..1.06f)
            // v1.0.22: knee sink (0.10) + half-frequency accent
            // (0.08) can stack on a downbeat — bound is their sum.
            assertTrue(p.sinkFraction in 0f..0.19f)
            if (p.sinkFraction > 0.05f) sawSink = true
            if (p.rotationDeg > 1.5f) sawRight = true
            if (p.rotationDeg < -1.5f) sawLeft = true
            pos += 37L
        }
        assertTrue(sawSink)
        assertTrue(sawLeft && sawRight) // the rock goes both ways
    }
}
