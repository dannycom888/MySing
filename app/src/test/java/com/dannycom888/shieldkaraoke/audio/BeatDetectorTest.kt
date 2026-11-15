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
}
