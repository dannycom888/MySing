package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.roundToInt

class BeatDetectorTest {

    private val fps = 100

    /** Synthetic click train: a short decaying bump at every beat. */
    private fun clickTrain(bpm: Double, seconds: Int = 30): FloatArray {
        val env = FloatArray(fps * seconds)
        val periodFrames = fps * 60.0 / bpm
        var k = 0
        while (true) {
            val at = (k * periodFrames).roundToInt()
            if (at >= env.size) break
            for (j in 0 until 6) {
                if (at + j < env.size) env[at + j] += exp(-j / 2.0).toFloat()
            }
            k++
        }
        return env
    }

    @Test
    fun detectsKnownBpms() {
        for (bpm in listOf(60.0, 96.0, 120.0, 160.0)) {
            val est = BeatDetector.estimateBpm(clickTrain(bpm), fps)
            assertTrue("expected an estimate for $bpm BPM", est != null)
            assertEquals("$bpm BPM detected as $est", bpm, est!!, 3.0)
        }
    }

    @Test
    fun detectsFastBpmsAboveTheOld160Cap() {
        // v1.0.76: the range widened 160 -> 200. A ~170 BPM
        // song's true beat period (35 frames at 100 fps) sat
        // below the old minimum lag (37), so the detector
        // locked onto the double period and reported ~85.
        for (bpm in listOf(170.0, 185.0)) {
            val est = BeatDetector.estimateBpm(clickTrain(bpm), fps)
            assertTrue("expected an estimate for $bpm BPM", est != null)
            assertEquals("$bpm BPM detected as $est", bpm, est!!, 3.0)
        }
    }

    @Test
    fun slowBpmsAreNotDoubledByTheWiderRange() {
        // The widened search range must not let the anti-
        // octave rule promote a slow song to double tempo:
        // a genuine 85 BPM train has ~no correlation at its
        // half period, so it still reads 85, not 170.
        for (bpm in listOf(85.0, 100.0)) {
            val est = BeatDetector.estimateBpm(clickTrain(bpm), fps)
            assertTrue("expected an estimate for $bpm BPM", est != null)
            assertEquals("$bpm BPM detected as $est", bpm, est!!, 3.0)
        }
    }

    @Test
    fun silenceGivesNull() {
        assertNull(BeatDetector.estimateBpm(FloatArray(fps * 30), fps))
    }

    @Test
    fun constantEnvelopeGivesNull() {
        assertNull(BeatDetector.estimateBpm(FloatArray(fps * 30) { 0.5f }, fps))
    }

    @Test
    fun tooShortGivesNull() {
        assertNull(BeatDetector.estimateBpm(clickTrain(120.0, seconds = 2), fps))
    }

    /** A [bpm] click train whose beats alternate between
     *  two frame offsets (odd beats one frame late): the
     *  documented case the anti-octave rule exists for —
     *  the double lag correlates MORE than the true lag,
     *  whose share of the peak is only ≈0.46, so any
     *  "stricter share" fix that raises the bar past that
     *  silently re-breaks this song class. */
    private fun alternatingTrain(bpm: Double, seconds: Int = 30): FloatArray {
        val env = FloatArray(fps * seconds)
        val periodFrames = fps * 60.0 / bpm
        var k = 0
        while (true) {
            val at = (k * periodFrames).roundToInt() + if (k % 2 == 1) 1 else 0
            if (at >= env.size) break
            for (j in 0 until 6) {
                if (at + j < env.size) env[at + j] += exp(-j / 2.0).toFloat()
            }
            k++
        }
        return env
    }

    @Test
    fun alternatingOffsetFastSongKeepsItsTempo() {
        // The raw peak sits at the double lag (80 BPM); the
        // legacy tier must still rescue the true tempo —
        // the pin is "not halved", with the tolerance the
        // fixture's frame rounding supports (the alternating
        // train's local spacings quantise to neighbouring
        // lags, so the rescue may land a few BPM off).
        val est = BeatDetector.estimateBpm(alternatingTrain(160.0), fps)
        assertTrue("expected an estimate for alternating 160 BPM", est != null)
        assertTrue("alternating 160 BPM detected as $est — halved?", est!! > 120.0)
        assertEquals("alternating 160 BPM detected as $est", 160.0, est, 10.0)
    }

    /** A [bpm] beat train with irregular 8th-note clicks
     *  between the beats (seeded jitter in position and
     *  amplitude): the shape real mid-tempo songs present.
     *  The correlation peak stays at the beat lag, but the
     *  half lag — inside the v1.0.76 extension band —
     *  carries a marginal share from the subdivisions. */
    private fun subdividedTrain(bpm: Double, seed: Long = 11): FloatArray {
        val env = FloatArray(fps * 30)
        val periodFrames = fps * 60.0 / bpm
        val rng = java.util.Random(seed)
        var k = 0
        while (true) {
            val at = (k * periodFrames).roundToInt()
            if (at >= env.size) break
            for (j in 0 until 6) {
                if (at + j < env.size) env[at + j] += exp(-j / 2.0).toFloat()
            }
            if (rng.nextFloat() < 0.75f) {
                val sub = (k * periodFrames + periodFrames / 2).roundToInt() +
                    (rng.nextInt(5) - 2)
                val amp = 0.35f + 0.5f * rng.nextFloat()
                for (j in 0 until 6) {
                    if (sub + j in env.indices) {
                        env[sub + j] += (amp * exp(-j / 2.0)).toFloat()
                    }
                }
            }
            k++
        }
        return env
    }

    @Test
    fun subdividedMidTempoSongIsNotDoubledByTheWidenedRange() {
        // v1.0.79 regression pin (measured on the real
        // 《月亮之上》: peak lag 63, half-lag share 0.44 —
        // the single-tier widened scan stopped at the half
        // lag and reported 193.5 for a 96 BPM song). The
        // two-tier scan must keep the song at its own tempo.
        val est = BeatDetector.estimateBpm(subdividedTrain(96.0), fps)
        assertTrue("expected an estimate for subdivided 96 BPM", est != null)
        assertTrue("subdivided 96 BPM detected as $est — doubled?", est!! < 130.0)
        assertEquals("subdivided 96 BPM detected as $est", 96.0, est, 16.0)
    }
}
