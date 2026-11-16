package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.i18n.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.46 voice search: the pure decisions behind the browse
 * page's 🎤 button — recognition language mapping, result →
 * search-query application, and the availability → button-state
 * ruling. The SpeechRecognizer itself is a device capability and
 * is deliberately NOT exercised here (no faked recognizer); what
 * is pinned is everything the app decides around it.
 */
class VoiceSearchTest {

    @Test
    fun `recognition language follows the UI language`() {
        assertEquals("zh-CN", VoiceSearch.languageTag(UiLang.ZH))
        assertEquals("en-US", VoiceSearch.languageTag(UiLang.EN))
    }

    @Test
    fun `first candidate is the first non-blank result, trimmed`() {
        assertEquals("忘情水", VoiceSearch.firstCandidate(listOf("忘情水", "往事随风")))
        assertEquals("忘情水", VoiceSearch.firstCandidate(listOf("", "  ", " 忘情水 ")))
        assertEquals("Adele", VoiceSearch.firstCandidate(listOf("Adele")))
    }

    @Test
    fun `no usable candidate when nothing was heard`() {
        assertNull(VoiceSearch.firstCandidate(null))
        assertNull(VoiceSearch.firstCandidate(emptyList()))
        assertNull(VoiceSearch.firstCandidate(listOf("", "   ")))
    }

    @Test
    fun `a heard candidate replaces the query, silence keeps it`() {
        assertEquals("刘德华", VoiceSearch.queryAfterResult("", listOf("刘德华")))
        assertEquals("刘德华", VoiceSearch.queryAfterResult("周杰伦", listOf("刘德华")))
        assertEquals("周杰伦", VoiceSearch.queryAfterResult("周杰伦", null))
        assertEquals("周杰伦", VoiceSearch.queryAfterResult("周杰伦", listOf(" ")))
        assertEquals("", VoiceSearch.queryAfterResult("", null))
    }

    @Test
    fun `button state follows recognition availability`() {
        assertEquals(
            VoiceSearch.ButtonState.READY,
            VoiceSearch.buttonState(recognitionAvailable = true),
        )
        assertEquals(
            VoiceSearch.ButtonState.NO_SERVICE,
            VoiceSearch.buttonState(recognitionAvailable = false),
        )
        assertTrue(VoiceSearch.buttonEnabled(recognitionAvailable = true))
        assertFalse(VoiceSearch.buttonEnabled(recognitionAvailable = false))
    }
}
