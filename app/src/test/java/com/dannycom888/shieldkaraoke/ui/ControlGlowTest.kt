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
 * form of the halo (TextGlow). v1.0.67 reshapes the
 * banner end of that wiring: the two language chips merge
 * into ONE pill whose halves glow one at a time (the
 * user: "做成一个形状" / "一半一半发光") — the pill
 * halves wire the per-corner form of the halo themselves,
 * so the banner's glow = true chips are gone — and the
 * control bar takes its second slimming notch.
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
    fun glowCoversControlBarAndGear() {
        val src = mainActivitySource()
        assertTrue(
            "the chip takes the glow switch, default off",
            src.contains("glow: Boolean = false"),
        )
        assertEquals(
            "three halo wiring points: the chip, the gear, the pill half",
            3,
            Regex("""controlGlow\(""").findAll(src).count(),
        )
        assertEquals(
            "six chips glow, all of them in the control bar",
            6,
            Regex("""glow = true""").findAll(src).count(),
        )
        // All six stand in the control bar (after its marker
        // comment). The banner's two language chips stood
        // before it until v1.0.66; in v1.0.67 they merge
        // into the language pill, whose halves wire their
        // own halo (pinned in the pill test below), so no
        // glow = true remains outside the bar. The browse
        // filters and the admin chips still stay dark.
        val barStart = src.indexOf("// --- control bar")
        assertTrue("control bar found", barStart >= 0)
        val glows = Regex("""glow = true""").findAll(src).map { it.range.first }
        assertEquals("six in the bar", 6, glows.count { it > barStart })
        assertEquals("none outside the bar", 0, glows.count { it < barStart })
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
        // The selected fill is untouched by the halo wiring:
        // selected still paints gold inside the chip and
        // the pill half (the fill says "active", the halo
        // says "the remote is here").
        assertTrue(src.contains("if (selected) gold else cardBg"))
    }

    @Test
    fun controlBarTierTookTheThirdNotchAndSpacingHeld() {
        // v1.0.67 slimmed the inControlBar tier a second
        // notch (type 12 -> 11sp, horizontal 10 -> 8dp,
        // vertical 3 -> 2dp) and grew the row spacing to
        // 24/18dp. v1.0.68 (the user, on .67: the buttons
        // are STILL too big — then his correction: the
        // font sizes do NOT change, he misspoke): the
        // SHAPES take a third notch while the type HOLDS
        // at .67's 11/10sp — and the spacing holds too, so
        // the gaps grow by what the chips shed. Only the
        // control bar tier moves — the plain and compact
        // chip tiers keep their values. The gear's circle
        // shrinks in the same pass, its glyph size held.
        val src = mainActivitySource()
        assertTrue(
            "control bar type holds at 11sp, compact 10sp",
            src.contains("inControlBar && compact -> 10.sp") &&
                src.contains("inControlBar -> 11.sp"),
        )
        assertTrue(
            "control bar horizontal padding 8dp -> 6dp, compact one notch under",
            src.contains("inControlBar && compact -> 5.dp") &&
                src.contains("inControlBar -> 6.dp"),
        )
        assertTrue(
            "control bar vertical padding 2dp -> 1dp",
            src.contains("vertical = if (inControlBar) 1.dp else 4.dp"),
        )
        assertTrue(
            "row spacing holds at 24dp (compact 18dp)",
            src.contains("if (compact) 18.dp else 24.dp, Alignment.CenterHorizontally"),
        )
        // The gear (v1.0.68): the circle shrinks ~15%
        // (46 -> 39dp, compact 36 -> 31dp); the glyph's
        // size holds at 24sp (compact 18sp) — font sizes
        // don't move this version.
        val gearStart = src.indexOf("private fun GearButton")
        assertTrue("gear found", gearStart >= 0)
        val gear = src.substring(gearStart, minOf(gearStart + 3200, src.length))
        assertTrue(
            "gear circle 46 -> 39dp, compact 36 -> 31dp",
            gear.contains("val sizeDp = if (compact) 31 else 39"),
        )
        assertTrue(
            "gear glyph holds at 24sp, compact 18sp",
            gear.contains("fontSize = (if (compact) 18 else 24).sp"),
        )
    }

    @Test
    fun languagePillIsOneCapsuleWhoseHalvesGlowSeparately() {
        // v1.0.67 (the user: "上方中英按钮做成一个形状" —
        // the top Chinese/English buttons become one shape —
        // "一半一半发光" — the halves glow one at a time).
        val src = mainActivitySource()
        // The banner calls the pill; the two chip calls are
        // gone (the phone page keeps its own switch — a
        // different surface, untouched).
        assertTrue("the banner calls the pill", src.contains("LangPill(compact)"))
        assertTrue("the old zh chip is gone", !src.contains("Chip(\"中文\""))
        assertTrue("the old en chip is gone", !src.contains("Chip(\"EN\""))
        val start = src.indexOf("private fun LangPill")
        val end = src.indexOf("private fun EntryCard")
        assertTrue("pill found", start >= 0 && end > start)
        val pill = src.substring(start, end)
        // One capsule: a single outlined, rounded container.
        // v1.0.68: the capsule radius steps 16 -> 13dp with
        // the pill's one-notch shrink.
        assertTrue(
            "one capsule outline",
            pill.contains(".border(2.dp, soft.copy(alpha = 0.45f), RoundedCornerShape(13.dp))"),
        )
        // Two halves (one shared half composable, called
        // once per language), each its own focusable
        // control switching to its own language.
        assertEquals(
            "one focusable in the shared half definition",
            1,
            Regex("""\.focusable\(\)""").findAll(pill).count(),
        )
        assertTrue(pill.contains("selected = uiLang == UiLang.ZH"))
        assertTrue(pill.contains("selected = uiLang == UiLang.EN"))
        assertTrue(pill.contains("switchUiLang(UiLang.ZH)"))
        assertTrue(pill.contains("switchUiLang(UiLang.EN)"))
        // A divider stands between the halves.
        assertTrue("a divider between the halves", pill.contains(".width(2.dp)"))
        // v1.0.68: the pill's shape shrinks one notch —
        // divider height 18 -> 14dp (compact 14 -> 10dp),
        // half padding horizontal 12 -> 10dp (compact
        // 8 -> 6dp) and vertical 4 -> 3dp — while the half
        // type HOLDS at .67's 13sp (compact 12sp): the
        // user's correction was that font sizes don't move.
        assertTrue(
            "divider height 18 -> 14dp, compact 14 -> 10dp",
            pill.contains(".height(if (compact) 10.dp else 14.dp)"),
        )
        assertTrue(
            "half padding one notch down",
            pill.contains(".padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 3.dp)"),
        )
        assertTrue(
            "half type holds at 13sp, compact 12sp",
            pill.contains("fontSize = (if (compact) 12 else 13).sp"),
        )
        // Per-half glow: the four-corner halo form, in the
        // chips' white/pink language, hugging each half's
        // own shape (rounded outside, square at the seam).
        assertTrue(pill.contains("topStartDp = glowTopStart"))
        assertTrue(
            pill.contains(
                "if (highlighted) ControlGlow.FOCUS_COLOR else ControlGlow.NORMAL_COLOR",
            ),
        )
        assertTrue(pill.contains("ControlGlow.FOCUS_SCALE"))
        assertTrue("left half rounded on the left", pill.contains("topStart = 11.dp"))
        assertTrue("right half rounded on the right", pill.contains("topEnd = 11.dp"))
        // Selected vs focus stay divided as ever: gold fill
        // for the current language, white border for focus.
        assertTrue(pill.contains("if (selected) gold else cardBg"))
        assertTrue(pill.contains("if (focused) Color.White else Color.Transparent"))
        // The per-corner halo itself lives in ControlGlow
        // (path-drawn, still no blur anywhere).
        val glow = controlGlowSource()
        assertTrue("per-corner overload exists", glow.contains("bottomStartDp: Float"))
        assertTrue("drawn as a per-corner path", glow.contains("addRoundRect"))
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
