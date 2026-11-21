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
 * Widening the range to 200 lets the true lag compete.
 *
 * v1.0.79: the widening also widened the anti-octave scan
 * below, and that doubled ordinary songs. The scan walks
 * from the minimum lag upward and stops at the first lag
 * carrying a substantial share of the peak correlation;
 * with the minimum lag down at 30, the first qualifying
 * lag for a ≤100 BPM song is routinely its peak's own
 * HALF lag (30–31), where subdivision flux (8th/16th
 * notes) carries 0.40–0.52 of the peak — over the bar.
 * Measured by exact offline replication on the device's
 * own files: 《月亮之上》 (true ≈96, peak lag 63) read
 * 193.5 via lag 31 (share 0.44), and 《爱情错觉》 (true
 * 50, in-range peak at its half period, lag 60) read
 * 200 via lag 30 (share 0.52). The scan is therefore
 * two-tier now (see the scan itself): the pre-v1.0.76
 * range keeps the legacy bar, and the v1.0.76 extension
 * band (lags for >160 BPM) is consulted only when the
 * legacy tier finds no candidate, with a decisive-share
 * bar genuine fast material clears (a true 185 BPM
 * train's lag carries 0.63; the measured false candidates
 * carry 0.40–0.52) and subdivision artifacts do not.
 */
object BeatDetector {

    const val MIN_BPM = 60.0
    const val MAX_BPM = 200.0

    /** The pre-v1.0.76 cap: lags at or above the one it
     *  implies form the legacy tier of the anti-octave
     *  scan; the lags the 200 cap added below it form the
     *  extension band. */
    const val LEGACY_MAX_BPM = 160.0

    /** Share of the peak correlation a lag in the legacy
     *  tier needs to be taken as the beat over the peak
     *  itself. The bar is low on purpose: when a fast
     *  song's raw peak sits at the double period, its true
     *  lag can carry barely half the peak (measured ≈0.46
     *  when beats alternate between two frame offsets). */
    const val ANTI_OCTAVE_SHARE = 0.4

    /** Share an extension-band lag needs before it may
     *  override the peak: decisive, not marginal (see the
     *  class doc — the false candidates measured on real
     *  songs carry 0.40–0.52, genuine fast material 0.63+). */
    const val EXTENDED_BAND_SHARE = 0.6

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
        //
        // Two tiers (v1.0.79, see the class doc): the legacy
        // tier — the pre-v1.0.76 scan range — runs first, at
        // the legacy share bar, over the lags strictly below
        // the peak (the peak itself is the fallback, exactly
        // as the single scan's inclusive end made it). Only
        // when the legacy tier finds no candidate does the
        // extension band (the lags the 200 cap added) get a
        // say, and there the bar is decisive: a marginal
        // candidate in the band is the subdivision signature
        // that doubled slow songs, not a beat.
        var chosen = bestLag
        val legacyLagMin = (fps * 60.0 / LEGACY_MAX_BPM).toInt()
            .coerceAtLeast(lagMin)
        var found = false
        for (lag in legacyLagMin until bestLag) {
            if (corrs[lag] >= bestCorr * ANTI_OCTAVE_SHARE) {
                chosen = lag
                found = true
                break
            }
        }
        if (!found) {
            for (lag in lagMin until minOf(legacyLagMin, bestLag)) {
                if (corrs[lag] >= bestCorr * EXTENDED_BAND_SHARE) {
                    chosen = lag
                    break
                }
            }
        }
        return (60.0 * fps / chosen).coerceIn(MIN_BPM, MAX_BPM)
    }
}
