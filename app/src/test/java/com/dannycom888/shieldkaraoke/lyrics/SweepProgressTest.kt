package com.dannycom888.shieldkaraoke.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** The smooth in-character sweep fill (v1.0.2). */
class SweepProgressTest {

    @Test
    fun `progress is zero before and at the character start`() {
        assertEquals(0f, SweepProgress.progress(9_999, 10_000, 10_400), 0f)
        assertEquals(0f, SweepProgress.progress(10_000, 10_000, 10_400), 0f)
    }

    @Test
    fun `progress is linear inside the character window`() {
        assertEquals(0.25f, SweepProgress.progress(10_100, 10_000, 10_400), 0.0001f)
        assertEquals(0.5f, SweepProgress.progress(10_200, 10_000, 10_400), 0.0001f)
        assertEquals(0.75f, SweepProgress.progress(10_300, 10_000, 10_400), 0.0001f)
    }

    @Test
    fun `progress is one at and after the character end`() {
        assertEquals(1f, SweepProgress.progress(10_400, 10_000, 10_400), 0f)
        assertEquals(1f, SweepProgress.progress(12_000, 10_000, 10_400), 0f)
    }

    @Test
    fun `degenerate window fills whole at its start`() {
        // endMs == startMs: the trailing fragment with no next start.
        assertEquals(0f, SweepProgress.progress(9_999, 10_000, 10_000), 0f)
        assertEquals(1f, SweepProgress.progress(10_000, 10_000, 10_000), 0f)
        assertEquals(1f, SweepProgress.progress(10_001, 10_000, 10_000), 0f)
        // endMs < startMs gets the same fallback.
        assertEquals(0f, SweepProgress.progress(9_999, 10_000, 9_500), 0f)
        assertEquals(1f, SweepProgress.progress(10_000, 10_000, 9_500), 0f)
    }

    @Test
    fun `char windows follow the next character start`() {
        // One character per fragment, closed by an end marker:
        // each character ends exactly where the next one starts.
        val words = listOf(
            WordTiming(10_000, "苍"),
            WordTiming(10_400, "茫"),
            WordTiming(10_800, "天"),
            WordTiming(11_200, ""),
        )
        assertEquals(
            listOf(
                SweepProgress.CharWindow(10_000, 10_400),
                SweepProgress.CharWindow(10_400, 10_800),
                SweepProgress.CharWindow(10_800, 11_200),
            ),
            SweepProgress.charWindows(words),
        )
    }

    @Test
    fun `char windows divide a multi-character fragment evenly`() {
        val words = listOf(
            WordTiming(10_000, "abcd"),
            WordTiming(10_800, "e"),
            WordTiming(11_200, ""),
        )
        val windows = SweepProgress.charWindows(words)
        assertEquals(5, windows.size)
        assertEquals(SweepProgress.CharWindow(10_000, 10_200), windows[0])
        assertEquals(SweepProgress.CharWindow(10_200, 10_400), windows[1])
        assertEquals(SweepProgress.CharWindow(10_600, 10_800), windows[3])
        assertEquals(SweepProgress.CharWindow(10_800, 11_200), windows[4])
    }

    @Test
    fun `trailing fragment without a next start gets degenerate windows`() {
        val words = listOf(
            WordTiming(10_000, "苍"),
            WordTiming(10_400, "茫天"),
        )
        val windows = SweepProgress.charWindows(words)
        assertEquals(3, windows.size)
        // First fragment is bounded by the second one's start.
        assertEquals(SweepProgress.CharWindow(10_000, 10_400), windows[0])
        // The trailing fragment lights whole at its start.
        assertEquals(SweepProgress.CharWindow(10_400, 10_400), windows[1])
        assertEquals(SweepProgress.CharWindow(10_400, 10_400), windows[2])
    }
}
