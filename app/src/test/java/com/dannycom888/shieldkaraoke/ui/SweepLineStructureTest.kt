package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.38 structural pin for the sweep double-line fix (the
 * user, 2026-11-16: on a long English line the gold sat ~half
 * a character off the cyan, "变成双行"). SweepLine must keep
 * its single-layout structure: exactly ONE Text composable,
 * whose own TextLayoutResult is captured via onTextLayout and
 * re-drawn in the sweep colour from the SAME draw scope via
 * multiParagraph.paint — the two layers then share glyph
 * coordinates by construction. The pre-v1.0.38 shape (two stacked Texts, each
 * laying the string out independently) must not come back.
 * LyricsView is Compose UI, so the invariant is pinned on the
 * source, the same file-reading style as the asset tests.
 */
class SweepLineStructureTest {

    private fun sweepLineBody(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/LyricsView.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/LyricsView.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("LyricsView.kt not found; tried ${candidates.map { it.absolutePath }}")
        val src = file.readText()
        val start = src.indexOf("private fun SweepLine")
        assertTrue("SweepLine must exist", start >= 0)
        val end = src.indexOf("\n}\n", start)
        assertTrue("SweepLine must terminate", end > start)
        return src.substring(start, end)
    }

    @Test
    fun sweepLineIsOneTextDrawnTwiceFromOneLayout() {
        val body = sweepLineBody()
        val textCalls = Regex("""(?m)^\s*Text\(""").findAll(body).count()
        assertEquals(
            "SweepLine must contain exactly one Text composable (the old two-Text stack drifted apart)",
            1,
            textCalls,
        )
        assertTrue(
            "the base layout must be captured via onTextLayout",
            body.contains("onTextLayout"),
        )
        assertTrue(
            "the sweep pass must re-paint the SAME layout result's multiParagraph in the sweep colour",
            body.contains("result.multiParagraph.paint(drawContext.canvas, color = sweepColor)"),
        )
        assertTrue(
            "the sweep pass must stay clipped at the boundary in the same draw scope",
            body.contains("clipRect(0f, 0f, boundary, size.height)"),
        )
    }
}
