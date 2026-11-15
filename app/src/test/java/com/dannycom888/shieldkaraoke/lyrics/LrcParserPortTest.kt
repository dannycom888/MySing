package com.dannycom888.shieldkaraoke.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spot-checks that the ported MyLRC Auto parser behaves identically. */
class LrcParserPortTest {

    @Test
    fun `plain lrc lines parse with no word timings`() {
        val lines = LrcParser.parse("[00:01.00]第一行\n[00:05.50]第二行\n")
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals("第一行", lines[0].text)
        assertTrue(lines[0].words.isEmpty())
    }

    @Test
    fun `enhanced lrc yields per-character timings and clean text`() {
        val lines = LrcParser.parse("[00:23.26]<00:23.26>苍<00:23.67>茫<00:24.09>的<00:24.50>天\n")
        assertEquals(1, lines.size)
        assertEquals("苍茫的天", lines[0].text)
        assertEquals(4, lines[0].words.size)
        assertEquals(23260L, lines[0].words[0].startMs)
    }

    @Test
    fun `word sweep counts sung characters by position`() {
        val lines = LrcParser.parse("[00:10.00]<00:10.00>苍<00:10.40>茫<00:10.80>的<00:11.20>天<00:11.60>\n")
        val words = lines[0].words
        // Identical semantics to MyLRC Auto: a character lights once
        // it has been sung, i.e. when the next fragment starts.
        assertEquals(0, WordSweep.sungCharCount(words, 9_999))
        assertEquals(0, WordSweep.sungCharCount(words, 10_000))
        assertEquals(1, WordSweep.sungCharCount(words, 10_400))
        assertEquals(3, WordSweep.sungCharCount(words, 11_200))
        assertEquals(4, WordSweep.sungCharCount(words, 11_600))
    }

    @Test
    fun `invalid seconds tags are rejected`() {
        val lines = LrcParser.parse("[00:75.00]坏行\n[00:05.00]好行\n")
        assertEquals(1, lines.size)
        assertEquals("好行", lines[0].text)
    }

    @Test
    fun `current index binary search`() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c\n")
        assertEquals(-1, LrcParser.currentIndex(lines, 500))
        assertEquals(0, LrcParser.currentIndex(lines, 1000))
        assertEquals(1, LrcParser.currentIndex(lines, 7000))
        assertEquals(2, LrcParser.currentIndex(lines, 60_000))
    }
}
