package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMathTest {

    // (The `semitone factors` test was deleted in v1.0.56 with the
    // pitch-shift feature itself, removed at the user's word.)

    @Test
    fun `gain pulls quiet and loud songs toward target`() {
        assertEquals(4.0, AudioMath.normalizationGainDb(-20.0), 0.001) // quiet → boost
        assertEquals(-4.0, AudioMath.normalizationGainDb(-12.0), 0.001) // loud → cut
        assertEquals(0.0, AudioMath.normalizationGainDb(-16.0), 0.001) // already at target
    }

    @Test
    fun `gain clamps and unknown measurement is unity`() {
        assertEquals(AudioMath.MAX_GAIN_DB, AudioMath.normalizationGainDb(-40.0), 0.001)
        assertEquals(AudioMath.MIN_GAIN_DB, AudioMath.normalizationGainDb(0.0), 0.001)
        assertEquals(0.0, AudioMath.normalizationGainDb(null), 0.001)
    }

    @Test
    fun `db to linear`() {
        assertEquals(1.0f, AudioMath.dbToLinear(0.0), 0.001f)
        assertEquals(2.0f, AudioMath.dbToLinear(6.0206), 0.01f)
    }
}
