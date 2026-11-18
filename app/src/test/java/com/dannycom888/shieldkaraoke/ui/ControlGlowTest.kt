package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.graphics.Color
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
 * The glow: layered silhouettes (see ControlGlow) — pins
 * for the layer table, the highlight boost, the no-blur
 * rule, and the wiring. Colours per the user's final word
 * (v1.0.65, superseding the v1.0.64 per-chip-colour
 * halo): WHITE at rest for every chip; the highlighted
 * chip (remote focus or a pressing finger) grows a notch
 * and glows PINK. v1.0.66 extends the same wiring past
 * the control bar: the banner's two language chips and
 * the gear join in (the user: the top buttons didn't
 * change colour), and the now-singing line gets the text
 * form of the halo (TextGlow).
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
    fun highlightBoostsTheHaloOneNotch() {
        assertTrue(
            "the boost is one clear notch",
            ControlGlow.FOCUS_ALPHA_BOOST in 1.6f..2.4f,
        )
        for ((_, alpha) in ControlGlow.LAYERS) {
            assertEquals(
                alpha,
                ControlGlow.alphaFor(alpha, highlighted = false),
                0.0001f,
            )
            val boosted = ControlGlow.alphaFor(alpha, highlighted = true)
            assertTrue("highlighted is stronger", boosted > alpha)
            assertTrue(
                "but never a glare",
                boosted <= ControlGlow.MAX_ALPHA,
            )
        }
        assertEquals(
            "the cap binds",
            ControlGlow.MAX_ALPHA,
            ControlGlow.alphaFor(0.4f, highlighted = true),
            0.0001f,
        )
    }

    @Test
    fun whiteAtRestPinkAndBiggerWhenHighlighted() {
        // The user's final word: white halo for every chip
        // at rest; the pointed-at chip grows and turns pink.
        assertEquals(Color.White, ControlGlow.NORMAL_COLOR)
        val pink = ControlGlow.FOCUS_COLOR
        assertTrue("pink is red-dominant", pink.red > 0.9f)
        assertTrue("pink keeps a mid green", pink.green in 0.3f..0.7f)
        assertTrue("pink keeps a mid-high blue", pink.blue in 0.55f..0.8f)
        assertTrue(
            "one notch of growth, no more",
            ControlGlow.FOCUS_SCALE in 1.06f..1.16f,
        )
        // Wiring: the chip picks white at rest, pink when
        // highlighted, and scales by the same constant; the
        // v1.0.64 per-chip-colour wiring is gone.
        val src = mainActivitySource()
        assertTrue(
            src.contains(
                "if (highlighted) ControlGlow.FOCUS_COLOR else ControlGlow.NORMAL_COLOR",
            ),
        )
        assertTrue(src.contains("ControlGlow.FOCUS_SCALE"))
        assertTrue(!src.contains("gold else cardBg, focused"))
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
    fun glowCoversControlBarBannerChipsAndGear() {
        val src = mainActivitySource()
        assertTrue(
            "the chip takes the glow switch, default off",
            src.contains("glow: Boolean = false"),
        )
        assertEquals(
            "two halo wiring points: the chip and the gear",
            2,
            Regex("""controlGlow\(""").findAll(src).count(),
        )
        assertEquals(
            "eight chips glow: six in the control bar, two in the banner",
            8,
            Regex("""glow = true""").findAll(src).count(),
        )
        // Six stand in the control bar (after its marker
        // comment); the other two are the banner's language
        // chips (v1.0.66), which stand before it. The browse
        // filters and the admin chips still stay dark.
        val barStart = src.indexOf("// --- control bar")
        assertTrue("control bar found", barStart >= 0)
        val glows = Regex("""glow = true""").findAll(src).map { it.range.first }
        assertEquals("six in the bar", 6, glows.count { it > barStart })
        assertEquals("two in the banner", 2, glows.count { it < barStart })
        // The gear (v1.0.66): not a Chip, so it wires the
        // halo itself — same white/pink choice, same scale.
        val gearStart = src.indexOf("private fun GearButton")
        assertTrue("gear found", gearStart >= 0)
        val gear = src.substring(gearStart, minOf(gearStart + 3200, src.length))
        assertTrue(gear.contains("controlGlow("))
        assertTrue(
            gear.contains(
                "if (highlighted) ControlGlow.FOCUS_COLOR else ControlGlow.NORMAL_COLOR",
            ),
        )
        assertTrue(gear.contains("ControlGlow.FOCUS_SCALE"))
        // The language chips' selected fill is untouched by
        // the halo wiring: selected still paints gold inside
        // the chip (the fill says "active", the halo says
        // "the remote is here").
        assertTrue(src.contains("if (selected) gold else cardBg"))
    }

    @Test
    fun textGlowLayersFormASoftSymmetricHalo() {
        // v1.0.66: the now-singing line's pink bloom — the
        // text form of the halo. Same discipline as the
        // chip table: layered, falling, capped, and even
        // on every side (offsets in mirrored pairs).
        val layers = TextGlow.LAYERS
        assertTrue("several silhouette passes", layers.size >= 8)
        for ((dx, dy, alpha) in layers) {
            assertTrue("every pass is a whisper", alpha in 0.05f..0.35f)
            assertTrue(
                "offset ($dx, $dy) has its mirror",
                layers.any { it.first == -dx && it.second == -dy },
            )
        }
        val inner = layers.maxOf { it.third }
        val outer = layers.filter {
            maxOf(kotlin.math.abs(it.first), kotlin.math.abs(it.second)) >= 2f
        }
        assertTrue("an outer ring exists", outer.isNotEmpty())
        assertTrue(
            "the outer ring is fainter than the inner",
            outer.all { it.third < inner },
        )
        assertTrue(
            "the bloom stays a skirt around the glyphs",
            TextGlow.REACH_DP in 2f..3.5f,
        )
        // Wiring: the now-singing line draws its halo from
        // this table, in the buttons' focus pink.
        val src = mainActivitySource()
        val start = src.indexOf("private fun NowSingingLine")
        assertTrue("now-singing line found", start >= 0)
        val body = src.substring(start, minOf(start + 2600, src.length))
        assertTrue(body.contains("TextGlow.LAYERS"))
        assertTrue(body.contains("ControlGlow.FOCUS_COLOR"))
        assertTrue(body.contains("LyricFit.scaleFor"))
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
