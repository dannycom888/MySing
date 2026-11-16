package com.dannycom888.shieldkaraoke.state

import com.dannycom888.shieldkaraoke.i18n.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.0.21 dancer renames: the host can put friends' names on the
 * six dancers from the TV admin page. [DancerNames] is the single
 * resolver every label goes through — a custom name wins in both
 * languages (a person's name is not translated), and anything
 * unusable falls back to the bilingual default stage names.
 */
class DancerNamesTest {

    @Test
    fun customNameWinsInBothLanguages() {
        val custom = mapOf(DancerKeys.MODERN to "小明")
        assertEquals("小明", DancerNames.display(DancerKeys.MODERN, UiLang.ZH, custom))
        assertEquals("小明", DancerNames.display(DancerKeys.MODERN, UiLang.EN, custom))
    }

    @Test
    fun noCustomFallsBackToBilingualDefaults() {
        assertEquals("春丽", DancerNames.display(DancerKeys.MODERN, UiLang.ZH, emptyMap()))
        assertEquals("Chun-Li", DancerNames.display(DancerKeys.MODERN, UiLang.EN, emptyMap()))
        assertEquals("姿三四郎", DancerNames.display(DancerKeys.SUMO, UiLang.ZH, emptyMap()))
        assertEquals("Sanshiro", DancerNames.display(DancerKeys.SUMO, UiLang.EN, emptyMap()))
        assertEquals("WALL-E", DancerNames.display(DancerKeys.ROBOT, UiLang.EN, emptyMap()))
    }

    @Test
    fun v1034RenamedDefaultsAndCustomStillWins() {
        // v1.0.34 factory-default renames: ancient 林语嫣 → 陈秀颖,
        // ancient_girl 小蛮 → 汉娜. Only the defaults moved — a name
        // the host already set in the admin page still wins over the
        // new factory default, in both languages.
        assertEquals("陈秀颖", DancerNames.display(DancerKeys.ANCIENT, UiLang.ZH, emptyMap()))
        assertEquals("Chen Xiuying", DancerNames.display(DancerKeys.ANCIENT, UiLang.EN, emptyMap()))
        assertEquals("汉娜", DancerNames.display(DancerKeys.ANCIENT_GIRL, UiLang.ZH, emptyMap()))
        assertEquals("Hannah", DancerNames.display(DancerKeys.ANCIENT_GIRL, UiLang.EN, emptyMap()))
        val custom = mapOf(DancerKeys.ANCIENT to "阿姐", DancerKeys.ANCIENT_GIRL to "小妹")
        assertEquals("阿姐", DancerNames.display(DancerKeys.ANCIENT, UiLang.ZH, custom))
        assertEquals("阿姐", DancerNames.display(DancerKeys.ANCIENT, UiLang.EN, custom))
        assertEquals("小妹", DancerNames.display(DancerKeys.ANCIENT_GIRL, UiLang.ZH, custom))
        assertEquals("小妹", DancerNames.display(DancerKeys.ANCIENT_GIRL, UiLang.EN, custom))
    }

    @Test
    fun blankCustomFallsBackToDefault() {
        val custom = mapOf(DancerKeys.BABY to "   ")
        assertEquals("哪吒", DancerNames.display(DancerKeys.BABY, UiLang.ZH, custom))
        assertEquals("Nezha", DancerNames.display(DancerKeys.BABY, UiLang.EN, custom))
    }

    @Test
    fun customOnAnotherDancerDoesNotLeak() {
        val custom = mapOf(DancerKeys.SUMO to "大壮")
        assertEquals("春丽", DancerNames.display(DancerKeys.MODERN, UiLang.ZH, custom))
        assertEquals("大壮", DancerNames.display(DancerKeys.SUMO, UiLang.EN, custom))
    }

    @Test
    fun unknownAndNullKeysResolveAsDefaultDancer() {
        val custom = mapOf(DancerKeys.MODERN to "小明")
        assertEquals("小明", DancerNames.display("girl", UiLang.ZH, custom))
        assertEquals("小明", DancerNames.display(null, UiLang.EN, custom))
        assertEquals("春丽", DancerNames.display("nonsense", UiLang.ZH, emptyMap()))
        assertEquals("Chun-Li", DancerNames.display(null, UiLang.EN, emptyMap()))
    }

    @Test
    fun sanitizeTrimsAndRejectsBlank() {
        assertEquals("小明", DancerNames.sanitize("  小明  "))
        assertNull(DancerNames.sanitize(""))
        assertNull(DancerNames.sanitize("   "))
        assertNull(DancerNames.sanitize(null))
    }

    @Test
    fun sanitizeStripsMarkupAndControlChars() {
        assertEquals("小明", DancerNames.sanitize("<小明>"))
        assertEquals("小明", DancerNames.sanitize("小\u0001明"))
        assertEquals("小明", DancerNames.sanitize("小\n明"))
    }

    @Test
    fun sanitizeCapsAtTenCharacters() {
        assertEquals("一二三四五六七八九十", DancerNames.sanitize("一二三四五六七八九十"))
        assertEquals("一二三四五六七八九十", DancerNames.sanitize("一二三四五六七八九十甲"))
        assertEquals(10, DancerNames.sanitize("abcdefghijklm")!!.length)
    }
}
