package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v1.0.57: the star field twinkles SLOWLY — the user asked for
 * stars that "慢慢地闪亮". These pins make the slowness a
 * contract: a hard floor on the twinkle period, a sine that
 * never reaches darkness, and a smoothness bound no strobe
 * could satisfy.
 */
class StarsTest {

    @Test
    fun generationIsDeterministic() {
        assertEquals(Stars.generate(32), Stars.generate(32))
        assertEquals(Stars.generate(16, 7L), Stars.generate(16, 7L))
    }

    @Test
    fun countsMatchTheScreenModes() {
        assertEquals(Stars.COUNT_FULL, Stars.generate(Stars.COUNT_FULL).size)
        assertEquals(Stars.COUNT_COMPACT, Stars.generate(Stars.COUNT_COMPACT).size)
        assertTrue(Stars.COUNT_COMPACT < Stars.COUNT_FULL)
        // v1.0.59: the user asked for MORE stars ("星光再大些，
        // 多些") — the field roughly doubles the .57 counts
        // (64 / 44) and the radii grow with it.
        assertTrue(Stars.COUNT_FULL >= 100)
        assertTrue(Stars.COUNT_COMPACT >= 64)
        assertTrue(Stars.MAX_RADIUS_DP >= 2.4f)
        assertTrue(Stars.MIN_RADIUS_DP >= 0.8f)
    }

    @Test
    fun fieldIsDealtLeftHeavyWithASparseLyricsSky() {
        // v1.0.59: most of the sky hangs over the dancer's side
        // of the stage; the lyrics band's sky is deliberately
        // sparse so no star competes with a lyric character.
        val n = Stars.COUNT_FULL
        val stars = Stars.generate(n)
        val byZone = stars.groupBy { Stars.zoneOf(it.xFrac) }
        val left = byZone[0]?.size ?: 0
        val lyrics = byZone[1]?.size ?: 0
        val right = byZone[2]?.size ?: 0
        assertEquals(n, left + lyrics + right)
        assertTrue("the left takes at least half the sky: $left of $n", left >= n / 2)
        assertTrue("the lyrics sky stays a small share: $lyrics of $n", lyrics <= n / 5)
        assertTrue("the right still outnumbers the lyrics sky", right > lyrics)
        // Per unit of width the contrast is the real ruling:
        // the lyrics zone's density is under half the left's.
        val leftDensity = left / Stars.LYRICS_ZONE_START
        val lyricsDensity = lyrics / (Stars.LYRICS_ZONE_END - Stars.LYRICS_ZONE_START)
        assertTrue(
            "lyrics density $lyricsDensity vs left $leftDensity",
            lyricsDensity < 0.5f * leftDensity,
        )
        // The dealing itself is exact and sums to the field.
        assertEquals(Triple(55, 15, 30), Stars.zoneCounts(100))
        val (l2, y2, r2) = Stars.zoneCounts(37)
        assertEquals(37, l2 + y2 + r2)
        assertEquals(
            1f,
            Stars.LEFT_SHARE + Stars.LYRICS_SHARE + Stars.RIGHT_SHARE,
            0.0001f,
        )
        // The lyrics zone starts where the dancer zone ends —
        // one geometry, owned by LyricsBand.
        assertEquals(LyricsBand.DANCER_FRACTION, Stars.LYRICS_ZONE_START, 0.0001f)
    }

    @Test
    fun fieldLivesOnlyInTheTopThird() {
        // v1.0.60: the user's sky is a band — the stars appear
        // ONLY in the stage's top third ("星星只出现在上方
        // 三分一那片"), never a single one below it.
        assertEquals(1f / 3f, Stars.TOP_BAND_FRACTION, 0.0001f)
        for (n in listOf(Stars.COUNT_FULL, Stars.COUNT_COMPACT, 256)) {
            val stars = Stars.generate(n)
            assertTrue(
                "every star inside the band",
                stars.all { it.yFrac >= 0f && it.yFrac < Stars.TOP_BAND_FRACTION },
            )
            // The band is USED, not huddled against the top
            // edge: the field reaches deep into its third.
            assertTrue(
                "the field fills the band: max y ${stars.maxOf { it.yFrac }}",
                stars.maxOf { it.yFrac } > 0.30f,
            )
        }
        // The move up changed nothing horizontally: the .59
        // left-heavy dealing is the same within the band.
        val byZone = Stars.generate(Stars.COUNT_FULL)
            .groupBy { Stars.zoneOf(it.xFrac) }
        assertTrue((byZone[0]?.size ?: 0) >= Stars.COUNT_FULL / 2)
        assertEquals(Triple(55, 15, 30), Stars.zoneCounts(100))
    }

    @Test
    fun everyStarStaysInSpecRanges() {
        for (s in Stars.generate(128)) {
            assertTrue(s.xFrac in 0f..1f)
            // v1.0.60: y is band-limited to the top third.
            assertTrue(s.yFrac in 0f..Stars.TOP_BAND_FRACTION)
            assertTrue(s.phase in 0f..1f)
            assertTrue(s.radiusDp in Stars.MIN_RADIUS_DP..Stars.MAX_RADIUS_DP)
            assertTrue(s.peakAlpha in Stars.MIN_PEAK_ALPHA..Stars.MAX_PEAK_ALPHA)
            assertTrue(s.periodSec in Stars.MIN_PERIOD_SEC..Stars.MAX_PERIOD_SEC)
        }
    }

    @Test
    fun twinklePeriodHasAHardSlowFloor() {
        // The anti-strobe pin: several seconds per cycle, and
        // the floor itself may never drop below three seconds.
        assertTrue(Stars.MIN_PERIOD_SEC >= 3f)
        assertTrue(Stars.generate(256).all { it.periodSec >= 3f })
        assertTrue(Stars.MAX_PERIOD_SEC > Stars.MIN_PERIOD_SEC)
    }

    @Test
    fun starsNeverGoDarkAndNeverExceedTheirPeak() {
        for (s in Stars.generate(Stars.COUNT_FULL)) {
            var t = 0f
            while (t < 60f) {
                val a = Stars.alphaAt(s, t)
                assertTrue(
                    "alpha $a outside the star's glow band at t=$t",
                    a >= s.peakAlpha * Stars.MIN_ALPHA_FRACTION - 0.001f &&
                        a <= s.peakAlpha + 0.001f,
                )
                assertTrue("a star never fully winks out", a > 0f)
                t += 0.37f
            }
        }
        assertTrue(Stars.MIN_ALPHA_FRACTION > 0f)
    }

    @Test
    fun twinkleIsSmoothNeverAFlash() {
        // Over a 100 ms step (three ambient frames) no star may
        // change brightness by more than a tenth of its peak —
        // a strobe or a blink would blow straight through this.
        for (s in Stars.generate(Stars.COUNT_FULL)) {
            var t = 0f
            while (t < 30f) {
                val jump = abs(Stars.alphaAt(s, t + 0.1f) - Stars.alphaAt(s, t))
                assertTrue(
                    "brightness jump $jump too fast at t=$t (period ${s.periodSec}s)",
                    jump <= s.peakAlpha * 0.10f,
                )
                t += 0.53f
            }
        }
    }

    @Test
    fun twinkleActuallyBreathes() {
        // Slow, but alive: over one full period each star must
        // sweep most of its glow band.
        for (s in Stars.generate(Stars.COUNT_FULL)) {
            var lo = Float.MAX_VALUE
            var hi = 0f
            var t = 0f
            while (t < s.periodSec) {
                val a = Stars.alphaAt(s, t)
                lo = minOf(lo, a)
                hi = maxOf(hi, a)
                t += s.periodSec / 40f
            }
            assertTrue(
                "star barely twinkles: $lo..$hi of peak ${s.peakAlpha}",
                hi - lo > s.peakAlpha * 0.5f,
            )
        }
    }

    @Test
    fun fieldIsSpreadAcrossTheSky() {
        val stars = Stars.generate(Stars.COUNT_FULL)
        assertTrue(stars.maxOf { it.xFrac } - stars.minOf { it.xFrac } > 0.5f)
        // v1.0.60: vertically the spread fills the top-third
        // band instead of the whole sky (the band is 1/3 deep;
        // the field reaches across nearly all of it).
        assertTrue(stars.maxOf { it.yFrac } - stars.minOf { it.yFrac } > 0.25f)
        // Independent phases: the field never pulses in unison.
        assertTrue(stars.map { it.phase }.distinct().size > stars.size / 2)
    }

    // ---- the beat breath (v1.0.59) ---------------------------

    @Test
    fun breathAmplitudeIsCappedSmall() {
        // A breath, not a pulse: the whole field may swell by
        // at most a quarter over its twinkle brightness, and
        // the shipped amplitude sits well under that.
        assertTrue(Stars.BREATH_AMPLITUDE > 0f && Stars.BREATH_AMPLITUDE <= 0.25f)
        for (bpm in listOf(60.0, 96.0, 120.0, 180.0, 208.0)) {
            var pos = 0L
            while (pos < 20_000L) {
                val m = Stars.breathMultiplier(pos, bpm)
                assertTrue(
                    "multiplier $m out of band at ${pos}ms / $bpm bpm",
                    m >= 1f && m <= 1f + Stars.BREATH_AMPLITUDE + 0.0001f,
                )
                pos += 37L
            }
        }
    }

    @Test
    fun breathPeaksExactlyOnTheBeatAndEasesBack() {
        // 60 bpm: one beat is exactly 1000 ms. The swell is at
        // its peak ON the beat and fully settled by the half
        // beat — it rises with the beat and falls back gently,
        // it never jumps up mid-beat.
        val peak = 1f + Stars.BREATH_AMPLITUDE
        assertEquals(peak, Stars.breathMultiplier(0L, 60.0), 0.001f)
        assertEquals(peak, Stars.breathMultiplier(1000L, 60.0), 0.001f)
        assertEquals(peak, Stars.breathMultiplier(5000L, 60.0), 0.001f)
        assertEquals(1f, Stars.breathMultiplier(500L, 60.0), 0.001f)
        assertTrue(Stars.breathMultiplier(100L, 60.0) > Stars.breathMultiplier(300L, 60.0))
        assertTrue(Stars.breathMultiplier(300L, 60.0) > Stars.breathMultiplier(500L, 60.0))
        // The same phase of the next beat breathes identically.
        assertEquals(
            Stars.breathMultiplier(250L, 60.0),
            Stars.breathMultiplier(1250L, 60.0),
            0.0001f,
        )
        // Continuous across the beat boundary — no step as one
        // beat hands over to the next.
        assertTrue(
            abs(Stars.breathMultiplier(999L, 60.0) - Stars.breathMultiplier(1001L, 60.0)) < 0.01f,
        )
    }

    @Test
    fun breathFollowsThePlaybackClockAndStopsWithoutASong() {
        // A pure function of the position: a frozen position
        // (pause) yields a frozen multiplier, forever — the
        // breath stops with the music with no state anywhere.
        assertEquals(
            Stars.breathMultiplier(12_345L, 96.0),
            Stars.breathMultiplier(12_345L, 96.0),
            0f,
        )
        // No tempo / no position: exactly neutral, no breath.
        assertEquals(1f, Stars.breathMultiplier(1000L, 0.0), 0f)
        assertEquals(1f, Stars.breathMultiplier(1000L, -80.0), 0f)
        assertEquals(1f, Stars.breathMultiplier(-1L, 120.0), 0f)
    }
}
