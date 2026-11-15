package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import com.dannycom888.shieldkaraoke.lyrics.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DancerActivityTest {

    private fun plainLine(timeMs: Long) = LyricLine(timeMs = timeMs, text = "句")

    private fun enhancedLine(timeMs: Long, endMarkerMs: Long?) = LyricLine(
        timeMs = timeMs,
        text = "句子",
        words = buildList {
            add(WordTiming(timeMs, "句"))
            add(WordTiming(timeMs + 500, "子"))
            if (endMarkerMs != null) add(WordTiming(endMarkerMs, ""))
        },
    )

    @Test
    fun noLyricsMeansNoEnd() {
        assertNull(DancerActivity.lastLyricEndMs(emptyList()))
    }

    @Test
    fun noLyricsDancesTheWholeSong() {
        assertTrue(DancerActivity.isDancing(0L, null))
        assertTrue(DancerActivity.isDancing(3_600_000L, null))
    }

    @Test
    fun plainLastLineFallsBackToItsStart() {
        val lines = listOf(plainLine(10_000), plainLine(180_000))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun enhancedLastLineUsesItsEndMarker() {
        val lines = listOf(plainLine(10_000), enhancedLine(180_000, 184_500))
        assertEquals(184_500L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun enhancedLastLineWithoutMarkerFallsBackToStart() {
        val lines = listOf(enhancedLine(180_000, null))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun endMarkerBeforeLineStartIsIgnored() {
        val lines = listOf(enhancedLine(180_000, 179_000))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun dancingUntilTheEndThenStill() {
        val end = 184_500L
        assertTrue(DancerActivity.isDancing(end - 1, end))
        assertTrue(DancerActivity.isDancing(end, end))
        assertFalse(DancerActivity.isDancing(end + 1, end))
        assertFalse(DancerActivity.isDancing(end + 60_000, end))
    }

    @Test
    fun stillFrameIsTheNeutralFirstFrame() {
        assertEquals(0, DancerActivity.STILL_FRAME)
    }
}
