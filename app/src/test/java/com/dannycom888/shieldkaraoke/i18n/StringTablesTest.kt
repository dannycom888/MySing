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
        // v1.0.20: the panel's label for a random background
        // song. (The 🎲 switch chip's own strings were retired
        // with the switch in v1.0.31 — filler is unconditional
        // now; the parity test above proves both tables dropped
        // the keys together.)
        assertEquals("随机", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_RANDOM))
        assertEquals("Random", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_RANDOM))
        assertEquals("暂无排队", TvStrings.get(UiLang.ZH, TvKey.QUEUE_PANEL_NO_WAITING))
        assertEquals("Nothing queued", TvStrings.get(UiLang.EN, TvKey.QUEUE_PANEL_NO_WAITING))
    }

    @Test
    fun adminStringsRender() {
        // v1.0.21 TV admin page: rename the dancers.
        assertEquals("⚙ 后台", TvStrings.get(UiLang.ZH, TvKey.ADMIN_TITLE))
        assertEquals("⚙ Admin", TvStrings.get(UiLang.EN, TvKey.ADMIN_TITLE))
        assertEquals("给 春丽 改名", TvStrings.get(UiLang.ZH, TvKey.ADMIN_RENAME_TITLE, "春丽"))
        assertEquals("Rename Chun-Li", TvStrings.get(UiLang.EN, TvKey.ADMIN_RENAME_TITLE, "Chun-Li"))
        assertEquals("默认：春丽", TvStrings.get(UiLang.ZH, TvKey.ADMIN_DEFAULT, "春丽"))
        assertEquals("Default: Chun-Li", TvStrings.get(UiLang.EN, TvKey.ADMIN_DEFAULT, "Chun-Li"))
        assertEquals("恢复默认名", TvStrings.get(UiLang.ZH, TvKey.ADMIN_RESTORE))
        assertEquals("Restore default names", TvStrings.get(UiLang.EN, TvKey.ADMIN_RESTORE))
        assertEquals("恢复默认名？", TvStrings.get(UiLang.ZH, TvKey.ADMIN_RESTORE_TITLE))
        assertEquals("Restore default names?", TvStrings.get(UiLang.EN, TvKey.ADMIN_RESTORE_TITLE))
        assertEquals("保存", TvStrings.get(UiLang.ZH, TvKey.SAVE))
        assertEquals("Save", TvStrings.get(UiLang.EN, TvKey.SAVE))
        assertEquals("取消", TvStrings.get(UiLang.ZH, TvKey.CANCEL))
        assertEquals("Cancel", TvStrings.get(UiLang.EN, TvKey.CANCEL))
        assertEquals("返回", TvStrings.get(UiLang.ZH, TvKey.ADMIN_BACK))
        assertEquals("Back", TvStrings.get(UiLang.EN, TvKey.ADMIN_BACK))
    }

    @Test
    fun adminSettingsStringsRender() {
        // v1.0.26: library + sweep colour moved into the admin
        // page; the gear's description names it in both languages.
        assertEquals("歌库", TvStrings.get(UiLang.ZH, TvKey.ADMIN_LIBRARY_TITLE))
        assertEquals("Library", TvStrings.get(UiLang.EN, TvKey.ADMIN_LIBRARY_TITLE))
        assertEquals(
            "当前歌库：primary:Music",
            TvStrings.get(UiLang.ZH, TvKey.ADMIN_LIBRARY_CURRENT, "primary:Music"),
        )
        assertEquals(
            "Library folder: primary:Music",
            TvStrings.get(UiLang.EN, TvKey.ADMIN_LIBRARY_CURRENT, "primary:Music"),
        )
        assertEquals("尚未选择歌库文件夹", TvStrings.get(UiLang.ZH, TvKey.ADMIN_LIBRARY_NONE))
        assertEquals("No library folder chosen yet", TvStrings.get(UiLang.EN, TvKey.ADMIN_LIBRARY_NONE))
        assertEquals("扫光颜色", TvStrings.get(UiLang.ZH, TvKey.ADMIN_SWEEP_TITLE))
        assertEquals("Sweep color", TvStrings.get(UiLang.EN, TvKey.ADMIN_SWEEP_TITLE))
        assertEquals("金色", TvStrings.get(UiLang.ZH, TvKey.SWEEP_GOLD))
        assertEquals("Gold", TvStrings.get(UiLang.EN, TvKey.SWEEP_GOLD))
        assertEquals("浅绿", TvStrings.get(UiLang.ZH, TvKey.SWEEP_GREEN))
        assertEquals("Light green", TvStrings.get(UiLang.EN, TvKey.SWEEP_GREEN))
        assertEquals("浅粉", TvStrings.get(UiLang.ZH, TvKey.SWEEP_PINK))
        assertEquals("Light pink", TvStrings.get(UiLang.EN, TvKey.SWEEP_PINK))
        assertEquals("后台设置", TvStrings.get(UiLang.ZH, TvKey.ADMIN_GEAR_DESC))
        assertEquals("Settings", TvStrings.get(UiLang.EN, TvKey.ADMIN_GEAR_DESC))
        // The admin footer carries the real versionName through
        // the %s slot: zh prefixes it with "v", en does not.
        assertEquals("版本 v1.0.26", TvStrings.get(UiLang.ZH, TvKey.ADMIN_VERSION, "1.0.26"))
        assertEquals("Version 1.0.26", TvStrings.get(UiLang.EN, TvKey.ADMIN_VERSION, "1.0.26"))
    }

    @Test
    fun v1031Wording() {
        // The brand title on the player banner: a Chinese name
        // for the Chinese UI, the app's name for English.
        assertEquals("一起唱卡拉OK", TvStrings.get(UiLang.ZH, TvKey.BRAND_TITLE))
        assertEquals("MySing", TvStrings.get(UiLang.EN, TvKey.BRAND_TITLE))
        // The dancers are 伴舞 in every user-facing Chinese
        // string now — the word 小人 is retired (the English side
        // always said "Dancers" and did not change).
        assertEquals("💃 伴舞开", TvStrings.get(UiLang.ZH, TvKey.DANCERS_ON))
        assertEquals("💃 伴舞关", TvStrings.get(UiLang.ZH, TvKey.DANCERS_OFF))
        assertEquals("选一位伴舞，按 OK 给他改名字", TvStrings.get(UiLang.ZH, TvKey.ADMIN_HINT))
        assertEquals("六位伴舞的名字都改回默认。", TvStrings.get(UiLang.ZH, TvKey.ADMIN_RESTORE_TEXT))
        for (key in TvKey.values()) {
            assertTrue("zh $key still says 小人", !TvStrings.get(UiLang.ZH, key).contains("小人"))
        }
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
