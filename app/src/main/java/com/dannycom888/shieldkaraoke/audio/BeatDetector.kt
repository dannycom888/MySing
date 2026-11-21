package com.dannycom888.shieldkaraoke.audio

/**
 * Estimates tempo (BPM) from an energy envelope by onset-flux
 * autocorrelation over the 60–200 BPM lag range. Pure function —
 * unit tested against synthetic click trains at known BPMs.
 * Returns null when there is no confident periodic peak.
 *
 * The range topped out at 160 until v1.0.76: a genuinely
 * faster song (《惊雷》 runs at ≈170 BPM, a beat period below
 * the old minimum lag) had its true beat outside the search
 * range, so the detector locked onto the double period and
 * reported half tempo — and that wrong value was cached.
 * Widening the range to 200 lets the true lag compete; the
 * anti-octave rule below keeps normal songs from doubling.
 */
object BeatDetector {

    const val MIN_BPM = 60.0
    const val MAX_BPM = 200.0

    /**
     * [envelope] holds one energy value per frame, [fps] frames per
     * second. Needs at least ~4 seconds of envelope to say anything.
     */
    fun estimateBpm(envelope: FloatArray, fps: Int): Double? {
        if (fps <= 0) return null
        val n = envelope.size
        if (n < fps * 4) return null

        // Onset flux: positive energy jumps only.
        val flux = FloatArray(n)
        var fluxSum = 0.0
        for (i in 1 until n) {
            val d = envelope[i] - envelope[i - 1]
            if (d > 0) {
                flux[i] = d
                fluxSum += d
            }
        }
        if (fluxSum <= 0.0) return null
        val mean = (fluxSum / n).toFloat()
        for (i in flux.indices) flux[i] -= mean

        val lagMin = (fps * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val lagMax = (fps * 60.0 / MIN_BPM).toInt()
        if (lagMax <= lagMin || lagMax >= n) return null

        val corrs = DoubleArray(lagMax + 1)
        var bestLag = -1
        var bestCorr = 0.0
        for (lag in lagMin..lagMax) {
            var c = 0.0
            for (i in 0 until n - lag) c += flux[i] * flux[i + lag]
            c /= (n - lag)
            corrs[lag] = c
            if (c > bestCorr) {
                bestCorr = c
                bestLag = lag
            }
        }
        if (bestLag < 0 || bestCorr <= 0.0) return null

        // Confidence: the peak must stand out from the average lag.
        var avg = 0.0
        for (lag in lagMin..lagMax) avg += corrs[lag]
        avg /= (lagMax - lagMin + 1)
        if (avg > 0 && bestCorr < avg * 2.0) return null

        // Anti-octave: prefer the smallest lag with a substantial
        // share of the peak correlation, so a 160 BPM song is not
        // reported as 80 (its double lag can correlate even more
        // strongly when beats alternate between two frame offsets).
        // A genuinely slower song has ~no correlation at half its
        // period, so the low share threshold is safe.
        var chosen = bestLag
        for (lag in lagMin..bestLag) {
            if (corrs[lag] >= bestCorr * 0.4) {
                chosen = lag
                break
            }
        }
        return (60.0 * fps / chosen).coerceIn(MIN_BPM, MAX_BPM)
    }
}
