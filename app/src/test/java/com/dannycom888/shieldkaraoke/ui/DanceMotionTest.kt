package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        // 120 BPM: one beat is 500 ms; position 0 is a beat line.
        val onBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 0L, 120.0)
        assertEquals(DanceMotion.ACCENT_MODERN.toFloat(), onBeat.sinkFraction, 0.001f)
        // Half a beat later the accent has decayed to ~nothing.
        val midBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 250L, 120.0)
        assertTrue(midBeat.sinkFraction < 0.01f)
        assertTrue(midBeat.sinkFraction < onBeat.sinkFraction)
        // The next beat line peaks again — it is a grid, not a
        // one-shot.
        val nextBeat = DanceMotion.pose(DanceMotion.Profile.MODERN, 500L, 120.0)
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
        val modern = DanceMotion.pose(DanceMotion.Profile.MODERN, 0L, 120.0)
        val ancient = DanceMotion.pose(DanceMotion.Profile.ANCIENT, 0L, 120.0)
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

    @Test
    fun risingOvershootsAndDancingResumes() {
        val bpm = 96.0
        var beats = 0.0
        var sawNegativeOvershoot = false
        var sawDancingAfter = false
        var phase = BabyFall.Phase.DANCING
        while (beats < 120.0) {
            val s = BabyFall.stateAt(beats, bpm)
            if (s.phase == BabyFall.Phase.RISING) {
                if (BabyFall.pose(beats, bpm).rotationDeg < 0f) sawNegativeOvershoot = true
            }
            if (phase == BabyFall.Phase.RISING && s.phase == BabyFall.Phase.DANCING) {
                sawDancingAfter = true
                break
            }
            phase = s.phase
            beats += 0.005
        }
        assertTrue("rise should overshoot past upright", sawNegativeOvershoot)
        assertTrue(sawDancingAfter)
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
            assertTrue(abs(p.rotationDeg) <= 4.51f)
            assertTrue(p.scale in 1.0f..1.06f)
            // v1.0.22: knee sink (0.10) + half-frequency accent
            // (0.08) can stack on a downbeat — bound is their sum.
            assertTrue(p.sinkFraction in 0f..0.19f)
            if (p.sinkFraction > 0.05f) sawSink = true
            if (p.rotationDeg > 3f) sawRight = true
            if (p.rotationDeg < -3f) sawLeft = true
            pos += 37L
        }
        assertTrue(sawSink)
        assertTrue(sawLeft && sawRight) // the rock goes both ways
    }
}
