package com.dannycom888.shieldkaraoke.audio

import kotlin.math.pow

/** Pure audio math shared by playback features, kept testable. */
object AudioMath {

    /** Pitch factor for a semitone shift: +12 doubles, -12 halves. */
    fun semitoneFactor(semitones: Int): Float =
        2.0.pow(semitones.coerceIn(-12, 12) / 12.0).toFloat()

    const val TARGET_RMS_DBFS = -16.0
    const val MAX_GAIN_DB = 10.0
    const val MIN_GAIN_DB = -12.0

    /**
     * Loudness-normalisation gain (dB) that pulls a song whose measured
     * RMS level is [measuredRmsDbfs] toward [TARGET_RMS_DBFS], clamped
     * so quiet recordings are not boosted into noise and loud ones are
     * not crushed. Unknown measurement (null) → unity gain.
     */
    fun normalizationGainDb(measuredRmsDbfs: Double?): Double {
        if (measuredRmsDbfs == null || measuredRmsDbfs.isNaN()) return 0.0
        return (TARGET_RMS_DBFS - measuredRmsDbfs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    fun dbToLinear(db: Double): Float = 10.0.pow(db / 20.0).toFloat()
}
