package com.dannycom888.shieldkaraoke.audio

/**
 * The stored form of a cached tempo measurement (v1.0.76):
 * `"<estimatorVersion>:<bpm>"` in the prefs string.
 *
 * Tempo values are only comparable within the estimator
 * that produced them: the pre-v1.0.76 detector searched
 * 60–160 BPM and stored a bare number ("85.7"), so a song
 * truly at ≈170 BPM was cached at exactly half tempo —
 * permanently, because a cache hit is never re-measured.
 * Tagging every entry with its estimator version makes
 * stale entries self-invalidating: [decode] returns null
 * for anything not written by the current estimator, the
 * caller treats that as uncached, and the song is simply
 * measured again with the widened detector.
 */
object BpmCacheFormat {

    /** Version 1 is the pre-v1.0.76 detector (60–160 BPM),
     *  whose entries are bare numbers with no tag at all.
     *  Version 2 is the 60–200 BPM detector with the
     *  single-tier anti-octave scan — the one whose widened
     *  scan doubled ordinary songs (a 50 BPM ballad cached
     *  as 200, a 96 BPM song as 193.5; see BeatDetector).
     *  Version 3 is the two-tier scan (v1.0.79): version-2
     *  entries may hold those doubled values permanently,
     *  so they read as uncached and are re-measured, the
     *  same self-invalidation version 2 applied to version
     *  1's half-tempo entries. */
    const val ESTIMATOR_VERSION = 3

    fun encode(bpm: Double): String = "$ESTIMATOR_VERSION:$bpm"

    /**
     * The cached BPM, or null when the entry is missing,
     * malformed, or was written by another estimator
     * version (a legacy bare number included) — null means
     * "uncached", never "zero".
     */
    fun decode(raw: String?): Double? {
        if (raw == null) return null
        val sep = raw.indexOf(':')
        if (sep < 0) return null // legacy bare number: estimator v1
        val version = raw.substring(0, sep).toIntOrNull() ?: return null
        if (version != ESTIMATOR_VERSION) return null
        return raw.substring(sep + 1).toDoubleOrNull()
    }
}
