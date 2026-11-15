package com.dannycom888.shieldkaraoke.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.13 bilingual tables: the zh and en sides must cover exactly
 * the same keys — a key missing on one side would silently fall
 * back to Chinese on William's screen, so parity is a hard test,
 * not a review nicety. Plus spot checks that the parameterised
 * templates render the way the TV and the server actually use them.
 */
class StringTablesTest {

    @Test
    fun tvTableKeysMatchAcrossLanguages() {
        val all = TvKey.values().toSet()
        assertEquals(all, TvStrings.keys(UiLang.ZH))
        assertEquals(all, TvStrings.keys(UiLang.EN))
    }

    @Test
    fun serverTableKeysMatchAcrossLanguages() {
        val all = ServerKey.values().toSet()
        assertEquals(all, ServerStrings.keys(UiLang.ZH))
        assertEquals(all, ServerStrings.keys(UiLang.EN))
    }

    @Test
    fun noBlankStringsOnEitherSide() {
        for (key in TvKey.values()) {
            assertTrue("zh $key blank", TvStrings.get(UiLang.ZH, key).isNotBlank())
            assertTrue("en $key blank", TvStrings.get(UiLang.EN, key).isNotBlank())
        }
        for (key in ServerKey.values()) {
            assertTrue("zh $key blank", ServerStrings.get(UiLang.ZH, key).isNotBlank())
            assertTrue("en $key blank", ServerStrings.get(UiLang.EN, key).isNotBlank())
        }
    }

    @Test
    fun tvTemplatesRender() {
        assertEquals("歌库共 1092 首", TvStrings.get(UiLang.ZH, TvKey.LIBRARY_COUNT, 1092))
        assertEquals("1092 songs in the library", TvStrings.get(UiLang.EN, TvKey.LIBRARY_COUNT, 1092))
        assertEquals("调 -2", TvStrings.get(UiLang.ZH, TvKey.PITCH_LABEL, -2))
        assertEquals("Key -2", TvStrings.get(UiLang.EN, TvKey.PITCH_LABEL, -2))
        assertEquals("5 秒后开始", TvStrings.get(UiLang.ZH, TvKey.INTERLUDE_COUNTDOWN, 5))
        assertEquals("Starting in 5 s", TvStrings.get(UiLang.EN, TvKey.INTERLUDE_COUNTDOWN, 5))
        // Song names are data: they pass through the %s slot as-is.
        assertEquals(
            "正在唱：张信哲 - 爱如潮水（chen点的）",
            TvStrings.get(UiLang.ZH, TvKey.NOW_SINGING_LINE, "张信哲 - 爱如潮水", "chen"),
        )
        assertEquals(
            "Now singing: 张信哲 - 爱如潮水 (ordered by chen)",
            TvStrings.get(UiLang.EN, TvKey.NOW_SINGING_LINE, "张信哲 - 爱如潮水", "chen"),
        )
        assertEquals("电视", TvStrings.get(UiLang.ZH, TvKey.TV_ORDERER))
        assertEquals("TV", TvStrings.get(UiLang.EN, TvKey.TV_ORDERER))
        // v1.0.19 queue panel wording.
        assertEquals("已点歌单", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_TITLE))
        assertEquals("Queue", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_TITLE))
        assertEquals("下一首唱", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_NEXT))
        assertEquals("Play next", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_NEXT))
        assertEquals("删除", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_DELETE))
        assertEquals("Delete", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_DELETE))
        // v1.0.20 filler strings: the switch chip and the panel's
        // label for a random background song.
        assertEquals("随机", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_RANDOM))
        assertEquals("Random", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_RANDOM))
        assertTrue(TvStrings.get(UiLang.ZH, TvKey.FILLER_ON).contains("随机"))
        assertTrue(TvStrings.get(UiLang.EN, TvKey.FILLER_ON).contains("Random"))
        assertTrue(TvStrings.get(UiLang.ZH, TvKey.FILLER_OFF).contains("随机"))
        assertTrue(TvStrings.get(UiLang.EN, TvKey.FILLER_OFF).contains("Random"))
        assertEquals("暂无排队", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_NO_WAITING))
        assertEquals("Nothing queued", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_NO_WAITING))
    }

    @Test
    fun serverOrderMessagesInBothLanguages() {
        assertEquals("马上开唱！", ServerStrings.get(UiLang.ZH, ServerKey.ORDER_START_NOW))
        assertEquals("You're up — singing now!", ServerStrings.get(UiLang.EN, ServerKey.ORDER_START_NOW))
        assertEquals("已加入，前面还有 3 首", ServerStrings.get(UiLang.ZH, ServerKey.ORDER_QUEUED, 3))
        assertEquals("Added — 3 song(s) ahead of you", ServerStrings.get(UiLang.EN, ServerKey.ORDER_QUEUED, 3))
        assertEquals("请先输入你的名字", ServerStrings.get(UiLang.ZH, ServerKey.ORDER_NAME_REQUIRED))
        assertEquals("Please enter your name first", ServerStrings.get(UiLang.EN, ServerKey.ORDER_NAME_REQUIRED))
        assertEquals("找不到这首歌", ServerStrings.get(UiLang.ZH, ServerKey.ORDER_NOT_FOUND))
        assertEquals("Song not found", ServerStrings.get(UiLang.EN, ServerKey.ORDER_NOT_FOUND))
        assertEquals("名字或伴舞选择不对", ServerStrings.get(UiLang.ZH, ServerKey.DANCER_INVALID))
        assertEquals("Invalid name or dancer choice", ServerStrings.get(UiLang.EN, ServerKey.DANCER_INVALID))
    }

    @Test
    fun parseDefaultsToChinese() {
        assertEquals(UiLang.ZH, UiLang.parse(null))
        assertEquals(UiLang.ZH, UiLang.parse(""))
        assertEquals(UiLang.ZH, UiLang.parse("zh"))
        assertEquals(UiLang.ZH, UiLang.parse("fr"))
        assertEquals(UiLang.EN, UiLang.parse("en"))
        assertEquals(UiLang.EN, UiLang.parse("EN"))
    }
}
