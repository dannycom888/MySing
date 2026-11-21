package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.0.76: the tempo cache is estimator-versioned. A value
 * the old 60–160 BPM detector cached (a bare number — e.g.
 * the half-tempo 85.7 it stored for a ≈170 BPM song) must
 * read as uncached so the song is re-measured, while values
 * written by the current estimator round-trip untouched.
 */
class BpmCacheFormatTest {

    @Test
    fun currentVersionRoundTrips() {
        val encoded = BpmCacheFormat.encode(171.4)
        assertEquals(171.4, BpmCacheFormat.decode(encoded)!!, 1e-9)
        assertEquals(96.0, BpmCacheFormat.decode(BpmCacheFormat.encode(96.0))!!, 1e-9)
    }

    @Test
    fun legacyBareNumberReadsAsUncached() {
        // Exactly what the pre-v1.0.76 cache holds: the raw
        // Double.toString() with no version tag.
        assertNull(BpmCacheFormat.decode("85.7"))
        assertNull(BpmCacheFormat.decode("120.0"))
    }

    @Test
    fun otherVersionsReadAsUncached() {
        assertNull(BpmCacheFormat.decode("1:85.7"))
        // Version 2 is the single-tier-scan detector whose
        // widened anti-octave scan doubled ordinary songs —
        // its cached values (a 50 BPM ballad stored as 200,
        // a 96 BPM song as 193.5) must not survive into the
        // two-tier estimator (v1.0.79).
        assertNull(BpmCacheFormat.decode("2:171.4"))
        assertNull(BpmCacheFormat.decode("2:200.0"))
        assertNull(BpmCacheFormat.decode("0:120.0"))
        assertNull(BpmCacheFormat.decode("4:120.0"))
    }

    @Test
    fun malformedReadsAsUncached() {
        assertNull(BpmCacheFormat.decode(null))
        assertNull(BpmCacheFormat.decode(""))
        assertNull(BpmCacheFormat.decode("abc"))
        assertNull(BpmCacheFormat.decode("3:"))
        assertNull(BpmCacheFormat.decode("3:xyz"))
        assertNull(BpmCacheFormat.decode("x:120.0"))
    }
}
