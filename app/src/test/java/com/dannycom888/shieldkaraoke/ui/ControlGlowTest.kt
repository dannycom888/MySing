package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.64 pins for the player control bar (the user: "将下方
 * 菜单的横条背景去掉" — take away the bottom menu's strip
 * background — and "按钮做成外发光" — make the buttons
 * glow).
 *
 * The strip first, because the finding shapes the rest:
 * audited against the v1.0.63 source, the player screen
 * draws NO background behind its control bar at all — no
 * strip composable, no background modifier anywhere in
 * PlayerScreen; the chips already float directly on the
 * stage backdrop (root colour, beams, bubbles). There was
 * nothing to delete, so this version pins the invariant
 * instead: PlayerScreen must never grow a background
 * again. No other screen shares the control bar (the
 * browse page's filter chips are a different row with no
 * bar either), so nothing else is touched.
 *
 * The glow: layered silhouettes in the chip's own colour
 * (see ControlGlow) — pins for the layer table, the focus
 * boost, the no-blur rule, and the wiring (exactly the six
 * control-bar chips glow; the banner's language chips and
 * every other chip do not).
 */
class ControlGlowTest {

    @Test
    fun haloLayersGrowAndFade() {
        val layers = ControlGlow.LAYERS
        assertTrue("a halo needs several passes", layers.size >= 3)
        for (i in 1 until layers.size) {
            assertTrue(
                "outsets strictly grow",
                layers[i].first > layers[i - 1].first,
            )
            assertTrue(
                "alphas strictly fall",
                layers[i].second < layers[i - 1].second,
            )
        }
        assertTrue(
            "the innermost pass stays a glow, not a fill",
            layers.first().second <= 0.25f,
        )
        assertTrue(
            "the outermost pass is a whisper — no visible rim",
            layers.last().second <= 0.05f,
        )
        assertTrue(
            "the halo's reach is a soft skirt, not a blob",
            ControlGlow.REACH_DP in 10f..18f,
        )
    }

    @Test
    fun focusBoostsTheHaloOneNotch() {
        assertTrue(
            "the boost is one clear notch",
            ControlGlow.FOCUS_ALPHA_BOOST in 1.6f..2.4f,
        )
        for ((_, alpha) in ControlGlow.LAYERS) {
            assertEquals(
                alpha,
                ControlGlow.alphaFor(alpha, focused = false),
                0.0001f,
            )
            val boosted = ControlGlow.alphaFor(alpha, focused = true)
            assertTrue("focused is stronger", boosted > alpha)
            assertTrue(
                "but never a glare",
                boosted <= ControlGlow.MAX_ALPHA,
            )
        }
        assertEquals(
            "the cap binds",
            ControlGlow.MAX_ALPHA,
            ControlGlow.alphaFor(0.4f, focused = true),
            0.0001f,
        )
    }

    @Test
    fun glowNeverUsesBlur() {
        // The Shield is API 30, where the blur modifier
        // silently no-ops (the v1.0.44 brand-logo lesson):
        // the glow is layered silhouettes or it is nothing.
        val src = controlGlowSource()
        assertTrue("no blur call in the glow", !src.contains(".blur("))
        assertTrue(
            "the halo is drawn as stacked round rects",
            src.contains("drawRoundRect"),
        )
    }

    @Test
    fun exactlyTheSixControlChipsGlow() {
        val src = mainActivitySource()
        assertTrue(
            "the chip takes the glow switch, default off",
            src.contains("glow: Boolean = false"),
        )
        assertEquals(
            "one wiring point inside the chip",
            1,
            Regex("""controlGlow\(""").findAll(src).count(),
        )
        // The glow colour is the chip's own background
        // colour — the same expression feeds both.
        assertTrue(
            src.contains(
                "Modifier.controlGlow(\n" +
                    "                            if (selected) gold else cardBg, focused,",
            ),
        )
        assertEquals(
            "exactly six chips glow",
            6,
            Regex("""glow = true""").findAll(src).count(),
        )
        // …and all six stand in the control bar: every
        // "glow = true" sits after the control bar's marker
        // comment (the banner's language chips, the browse
        // filters and the admin chips all stay dark).
        val barStart = src.indexOf("// --- control bar")
        assertTrue("control bar found", barStart >= 0)
        val glows = Regex("""glow = true""").findAll(src).map { it.range.first }
        assertTrue(glows.all { it > barStart })
    }

    @Test
    fun playerScreenDrawsNoBackgroundStrip() {
        // The invariant the user's strip request lands on:
        // nowhere in PlayerScreen — the control bar
        // included — may a background be drawn; the chips
        // float on the stage backdrop itself.
        val src = mainActivitySource()
        val start = src.indexOf("private fun PlayerScreen")
        val end = src.indexOf("private fun diagnosticsText")
        assertTrue("player screen found", start >= 0 && end > start)
        val body = src.substring(start, end)
        assertTrue(
            "no background anywhere in the player screen",
            !body.contains(".background("),
        )
        assertTrue(
            "the control bar is in this screen",
            body.contains("// --- control bar"),
        )
    }

    private fun mainActivitySource(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("MainActivity.kt not found")
        return file.readText()
    }

    private fun controlGlowSource(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/ControlGlow.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/ControlGlow.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("ControlGlow.kt not found")
        return file.readText()
    }
}
