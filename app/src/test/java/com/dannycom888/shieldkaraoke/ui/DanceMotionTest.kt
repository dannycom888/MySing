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
        // The fall is a timed gag, not a beat amplitude: its
        // phases are timed in seconds, so at 150 BPM the cycle
        // still spends the full 0.39 s falling (= 0.975 beats)
        // and 1.08 s getting up (= 2.7 beats) — the taper
        // never shrinks them — and the fall poses stay the
        // neutral sequence poses (v1.0.55: no transform at all
        // rides on the drawn frames, at any tempo).
        assertEquals(
            BabyFall.intervalForCycle(0) + 0.975 + 1.0 + 2.7,
            BabyFall.cycleLengthBeats(0, 150.0),
            0.001,
        )
        var beats = 0.0
        var found = false
        while (beats < 300.0) {
            if (BabyFall.stateAt(beats, 150.0).phase == BabyFall.Phase.FALLING) {
                val p = BabyFall.pose(beats, 150.0)
                assertEquals(0f, p.rotationDeg)
                assertEquals(0f, p.sinkFraction)
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
    fun downPoseIsNeutralForTheDrawnLyingFrame() {
        // v1.0.55: DOWN shows the drawn lying frame, which is
        // flat on the floor in its own art — the pose must be
        // exactly neutral, or the drawing would tilt or sink
        // below its baseline. (Before, the pose WAS the lying:
        // the standing frame rotated 84° and sunk 0.38.)
        val bpm = 120.0
        var beats = 0.0
        var found = false
        while (beats < 200.0) {
            if (BabyFall.stateAt(beats, bpm).phase == BabyFall.Phase.DOWN) {
                val p = BabyFall.pose(beats, bpm)
                assertEquals(0f, p.rotationDeg)
                assertEquals(0f, p.sinkFraction)
                assertEquals(0f, p.bounceFraction)
                assertEquals(1f, p.scale)
                found = true
                break
            }
            beats += 0.01
        }
        assertTrue(found)
    }

    // ---- v1.0.53: the get-up became staged (transforms);
    // v1.0.54: a crouch stage joined it (a scaleY squash).
    // v1.0.55: the whole gag is a drawn frame sequence — the
    // user's chain 站立 → 倾倒 → 翻 → 躺 → 撑前 → 备撑 → 半撑
    // → 撑中 → 蹲 → 起中 → 站立, each frame one 0.13 s step
    // with a short fade, poses neutral throughout (the
    // transform stages, the fall rotation and the squash are
    // all retired; only the settle sway survives). The
    // v1.0.53/54 stage tests and the .55 draft's morph tests
    // are replaced by the sequence tests below.

    @Test
    fun sequenceTimingAddsUp() {
        // Every drawn frame sits in the 110–150 ms band.
        assertTrue(
            "step ${BabyFall.STEP_SECONDS}s outside the 110–150 ms band",
            BabyFall.STEP_SECONDS in 0.11..0.15,
        )
        assertEquals(
            BabyFall.FALL_SECONDS,
            BabyFall.FALL_STEPS * BabyFall.STEP_SECONDS,
            0.0001,
        )
        assertEquals(0.39, BabyFall.FALL_SECONDS, 0.0001)
        assertEquals(
            BabyFall.RISE_STEPS_SECONDS,
            BabyFall.RISE_STEPS * BabyFall.STEP_SECONDS,
            0.0001,
        )
        assertEquals(
            BabyFall.RISE_TOTAL_SECONDS,
            BabyFall.RISE_STEPS_SECONDS + BabyFall.SETTLE_SECONDS,
            0.0001,
        )
        assertEquals(1.08, BabyFall.RISE_TOTAL_SECONDS, 0.0001)
        // The cycle length carries the sequence: at 120 BPM
        // the fall is 0.39 s = 0.78 beats, the lie is 1 beat,
        // the get-up is 1.08 s = 2.16 beats.
        val cycle = 3
        assertEquals(
            BabyFall.intervalForCycle(cycle) + 0.78 + 1.0 + 2.16,
            BabyFall.cycleLengthBeats(cycle, 120.0),
            0.0001,
        )
    }

    @Test
    fun fallSequenceStepsThroughLeanAndFlip() {
        // Dominant frame (alpha ≥ 0.5 side) scanning the fall:
        // standing → lean → flip, in order, nothing else.
        val dominant = mutableListOf<Int>()
        var p = 0f
        while (p <= 1.0f) {
            val b = BabyFall.fallChainBlend(p)
            val frame = if (b.currentAlpha >= 0.5f) b.currentIndex else b.previousIndex
            if (dominant.lastOrNull() != frame) dominant += frame
            p += 0.002f
        }
        assertEquals(
            listOf(BabyFall.CHAIN_FROM, BabyFall.CHAIN_LEAN, BabyFall.CHAIN_FLIP),
            dominant,
        )
        // The fall opens on the frozen frame; mid-fade into the
        // lean (15% into its step = half the fade) the pair is
        // standing/lean at half alpha.
        assertEquals(
            PoseCycle.Blend(BabyFall.CHAIN_FROM, BabyFall.CHAIN_FROM, 0f),
            BabyFall.fallChainBlend(0f),
        )
        val leanFade = BabyFall.fallChainBlend(
            ((BabyFall.STEP_SECONDS * 1.15) / BabyFall.FALL_SECONDS).toFloat(),
        )
        assertEquals(BabyFall.CHAIN_FROM, leanFade.previousIndex)
        assertEquals(BabyFall.CHAIN_LEAN, leanFade.currentIndex)
        assertEquals(0.5f, leanFade.currentAlpha, 0.01f)
        val flipFade = BabyFall.fallChainBlend(
            ((BabyFall.STEP_SECONDS * 2.15) / BabyFall.FALL_SECONDS).toFloat(),
        )
        assertEquals(BabyFall.CHAIN_LEAN, flipFade.previousIndex)
        assertEquals(BabyFall.CHAIN_FLIP, flipFade.currentIndex)
        assertEquals(0.5f, flipFade.currentAlpha, 0.01f)
    }

    @Test
    fun lyingFrameFadesInOverTheFlipThenLiesAlone() {
        // The DOWN phase is the lying frame's own step: at
        // 120 BPM the beat is 0.5 s; the fade runs over the
        // first STEP × FADE = 39 ms of it, so 19.5 ms in
        // (progress 0.039) the pair is flip/lying at half
        // alpha, and by mid-beat the lying frame stands alone
        // (alpha 1 on the current slot).
        val mid = BabyFall.chainBlend(BabyFall.Phase.DOWN, 0.039f, 120.0)!!
        assertEquals(BabyFall.CHAIN_FLIP, mid.previousIndex)
        assertEquals(BabyFall.CHAIN_LYING, mid.currentIndex)
        assertEquals(0.5f, mid.currentAlpha, 0.02f)
        val settled = BabyFall.chainBlend(BabyFall.Phase.DOWN, 0.5f, 120.0)!!
        assertEquals(BabyFall.CHAIN_LYING, settled.currentIndex)
        assertEquals(1f, settled.currentAlpha)
        // Dancing has no chain at all.
        assertNull(BabyFall.chainBlend(BabyFall.Phase.DANCING, 0.5f, 120.0))
    }

    @Test
    fun riseSequenceStepsThroughAllSixFramesInOrder() {
        // The get-up's frame order is the user's chain —
        // 躺 → 撑前 → 备撑 → 半撑 → 撑中 → 蹲 → 起中 → 站立:
        // scanning the rise, the dominant frame must visit
        // the slots in order (the lying frame leads — it is
        // what the first step fades out of) and end standing
        // alone.
        val dominant = mutableListOf<Int>()
        var p = 0f
        while (p <= 1.0f) {
            val b = BabyFall.riseChainBlend(p)
            val frame = if (b.currentAlpha >= 0.5f) b.currentIndex else b.previousIndex
            if (dominant.lastOrNull() != frame) dominant += frame
            p += 0.002f
        }
        assertEquals(
            listOf(
                BabyFall.CHAIN_LYING,
                BabyFall.CHAIN_PREPUSH,
                BabyFall.CHAIN_PREPUSH_ALT,
                BabyFall.CHAIN_HALF_PUSH,
                BabyFall.CHAIN_PUSHMID,
                BabyFall.CHAIN_CROUCH,
                BabyFall.CHAIN_RISEMID,
                BabyFall.CHAIN_STAND,
            ),
            dominant,
        )
        // The rise opens fading out of the lying frame, and
        // mid-fade into the prepush the pair is lying/prepush
        // at half alpha.
        val atStart = BabyFall.riseChainBlend(0f)
        assertEquals(BabyFall.CHAIN_LYING, atStart.previousIndex)
        assertEquals(BabyFall.CHAIN_PREPUSH, atStart.currentIndex)
        assertEquals(0f, atStart.currentAlpha)
        val prepushFade = BabyFall.riseChainBlend(
            ((BabyFall.STEP_SECONDS * 0.15) / BabyFall.RISE_TOTAL_SECONDS).toFloat(),
        )
        assertEquals(0.5f, prepushFade.currentAlpha, 0.01f)
        // The end of the rise is the standing frame alone.
        val atEnd = BabyFall.riseChainBlend(1f)
        assertEquals(BabyFall.CHAIN_STAND, atEnd.currentIndex)
        assertEquals(1f, atEnd.currentAlpha)
    }

    @Test
    fun sequencePosesAreNeutralExceptTheSettleSway() {
        // v1.0.55: the drawings carry everything, so through
        // the fall, the lie and the get-up's five steps the
        // whole-body pose is exactly neutral — no rotation (the
        // old tip-over), no sink, no squash. Only the settle
        // window at the very end sways (next test).
        val bpm = 120.0
        val fallStart = BabyFall.intervalForCycle(0).toDouble()
        // Fall 0.78 beats + lie 1 beat + rise steps 1.56 beats.
        val settleStart = fallStart + 0.78 + 1.0 + 1.56
        var beats = fallStart
        var sawNeutral = false
        while (beats < settleStart) {
            val s = BabyFall.stateAt(beats, bpm)
            if (s.phase != BabyFall.Phase.DANCING) {
                val p = BabyFall.pose(beats, bpm)
                assertEquals("rotation at $beats", 0f, p.rotationDeg)
                assertEquals("sink at $beats", 0f, p.sinkFraction)
                assertEquals("scale at $beats", 1f, p.scale)
                assertEquals("bounce at $beats", 0f, p.bounceFraction)
                sawNeutral = true
            }
            beats += 0.005
        }
        assertTrue(sawNeutral)
    }

    @Test
    fun chainBlendAtWalksTheWholeSequenceByPosition() {
        // Position-domain API, 120 BPM (one beat = 500 ms),
        // cycle 0: dancing until the interval, fall 390 ms,
        // down 500 ms, rise 1 080 ms, then dancing again.
        val bpm = 120.0
        val fallStartMs = BabyFall.intervalForCycle(0) * 500L
        assertNull(BabyFall.chainBlendAt(fallStartMs - 1_000L, bpm))
        assertNull(BabyFall.chainStartPositionMs(fallStartMs - 1_000L, bpm))
        // Fall, first step (65 ms in): the frozen frame alone.
        assertEquals(
            PoseCycle.Blend(BabyFall.CHAIN_FROM, BabyFall.CHAIN_FROM, 1f),
            BabyFall.chainBlendAt(fallStartMs + 65L, bpm),
        )
        // Fall, fading into the lean (149.5 ms in = 15% into
        // the lean's step): half alpha.
        val lean = BabyFall.chainBlendAt(fallStartMs + 149L, bpm)!!
        assertEquals(BabyFall.CHAIN_FROM, lean.previousIndex)
        assertEquals(BabyFall.CHAIN_LEAN, lean.currentIndex)
        assertEquals(0.5f, lean.currentAlpha, 0.03f)
        // Down, fading into the lying frame (19.5 ms into the
        // beat): flip/lying at half alpha; later in the beat
        // the lying frame stands alone, and the chain still
        // reports the same fall start.
        val landing = BabyFall.chainBlendAt(fallStartMs + 409L, bpm)!!
        assertEquals(BabyFall.CHAIN_FLIP, landing.previousIndex)
        assertEquals(BabyFall.CHAIN_LYING, landing.currentIndex)
        assertEquals(0.5f, landing.currentAlpha, 0.03f)
        val lying = BabyFall.chainBlendAt(fallStartMs + 650L, bpm)!!
        assertEquals(BabyFall.CHAIN_LYING, lying.currentIndex)
        assertEquals(1f, lying.currentAlpha)
        val startAt650 = BabyFall.chainStartPositionMs(fallStartMs + 650L, bpm)!!
        assertTrue(
            "chain start $startAt650 vs fall start $fallStartMs",
            abs(startAt650 - fallStartMs) <= 2L,
        )
        // Rise (starts at +890 ms): fading into the prepush at
        // +909 ms, into the rise-mid at +1559 ms, and onto the
        // standing frame by +1790 ms; the fall start is still
        // the chain's anchor throughout.
        val prepush = BabyFall.chainBlendAt(fallStartMs + 909L, bpm)!!
        assertEquals(BabyFall.CHAIN_LYING, prepush.previousIndex)
        assertEquals(BabyFall.CHAIN_PREPUSH, prepush.currentIndex)
        assertEquals(0.5f, prepush.currentAlpha, 0.03f)
        val riseMid = BabyFall.chainBlendAt(fallStartMs + 1_559L, bpm)!!
        assertEquals(BabyFall.CHAIN_CROUCH, riseMid.previousIndex)
        assertEquals(BabyFall.CHAIN_RISEMID, riseMid.currentIndex)
        assertEquals(0.5f, riseMid.currentAlpha, 0.03f)
        val standing = BabyFall.chainBlendAt(fallStartMs + 1_790L, bpm)!!
        assertEquals(BabyFall.CHAIN_RISEMID, standing.previousIndex)
        assertEquals(BabyFall.CHAIN_STAND, standing.currentIndex)
        assertEquals(1f, standing.currentAlpha)
        val startAt1790 = BabyFall.chainStartPositionMs(fallStartMs + 1_790L, bpm)!!
        assertTrue(
            "chain start $startAt1790 vs fall start $fallStartMs",
            abs(startAt1790 - fallStartMs) <= 2L,
        )
        // After the settle the chain lets go: dancing again.
        assertNull(BabyFall.chainBlendAt(fallStartMs + 2_070L, bpm))
        assertNull(BabyFall.chainStartPositionMs(fallStartMs + 2_070L, bpm))
    }

    @Test
    fun riseHasNoSnapAndNoScalePop() {
        // Across the whole get-up the pose never does anything
        // but the settle sway: rotation inside ±2°, scale
        // never pops (the old 0.4 s pop bounced it to 1.09 and
        // swung to −14°) and there is no bounce — the frames
        // climb, the transform just steadies him at the end.
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
                    p.rotationDeg <= BabyFall.SETTLE_SWAY_DEG.toFloat() + 0.01f,
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
        val riseStart = BabyFall.intervalForCycle(0) + 0.78 + 1.0
        // The settle starts after the get-up's six steps
        // = 0.78 s = 1.56 beats into the rise.
        val settleStart = riseStart + 1.56
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
