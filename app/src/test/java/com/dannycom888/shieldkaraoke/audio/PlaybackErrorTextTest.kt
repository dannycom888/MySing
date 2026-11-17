package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.58: the diagnostics error text keeps the cause chain —
 * until now only the code name and the first message line
 * survived, and on the user's tablet that top message was the
 * content-free "Unexpected runtime error"; the root fault sat
 * in a cause nobody kept.
 */
class PlaybackErrorTextTest {

    @Test
    fun noCauseKeepsTheOldShape() {
        val text = PlaybackErrorText.describe(
            "ERROR_CODE_FAILED_RUNTIME_CHECK",
            RuntimeException("Unexpected runtime error"),
        )
        assertEquals("ERROR_CODE_FAILED_RUNTIME_CHECK: Unexpected runtime error", text)
    }

    @Test
    fun causeChainIsAppendedInOrder() {
        val root = IllegalArgumentException("float output unsupported")
        val mid = IllegalStateException("sink configure failed", root)
        val top = RuntimeException("Unexpected runtime error", mid)
        val text = PlaybackErrorText.describe("ERROR_CODE_FAILED_RUNTIME_CHECK", top)
        assertEquals(
            "ERROR_CODE_FAILED_RUNTIME_CHECK: Unexpected runtime error" +
                " <- caused by IllegalStateException: sink configure failed" +
                " <- caused by IllegalArgumentException: float output unsupported",
            text,
        )
    }

    @Test
    fun onlyTheFirstLineOfEachMessageIsKept() {
        val cause = RuntimeException("root line\nsecond line must not appear")
        val top = RuntimeException("top line\nnor this one", cause)
        val text = PlaybackErrorText.describe("CODE", top)
        assertEquals("CODE: top line <- caused by RuntimeException: root line", text)
    }

    @Test
    fun missingMessagesDegradeToNamesOnly() {
        val top = RuntimeException(null as String?, RuntimeException(null as String?))
        val text = PlaybackErrorText.describe("CODE", top)
        assertEquals("CODE <- caused by RuntimeException", text)
    }

    @Test
    fun lengthIsHardCapped() {
        var e: Throwable = RuntimeException("x".repeat(200))
        repeat(6) { e = RuntimeException("y".repeat(200), e) }
        val text = PlaybackErrorText.describe("ERROR_CODE_FAILED_RUNTIME_CHECK", e)
        assertTrue(text.length <= PlaybackErrorText.MAX_CHARS)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun cyclicCauseChainTerminates() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b) // a <-> b cycle; Throwable permits this
        val text = PlaybackErrorText.describe("CODE", a)
        assertTrue(text.length <= PlaybackErrorText.MAX_CHARS)
        assertTrue(text.startsWith("CODE: a"))
    }
}
