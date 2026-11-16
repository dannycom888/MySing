package com.dannycom888.shieldkaraoke.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinInitialsTest {

    @Test
    fun `initials of chinese names`() {
        assertEquals("fhcq", PinyinInitials.initialsOf("凤凰传奇"))
        assertEquals("zjl", PinyinInitials.initialsOf("周杰伦"))
        assertEquals("zxmzf", PinyinInitials.initialsOf("最炫民族风"))
    }

    @Test
    fun `ascii passes through lowercased, spaces and dashes skipped`() {
        assertEquals("adele", PinyinInitials.initialsOf("Adele"))
        assertEquals("edsheeran", PinyinInitials.initialsOf("Ed Sheeran"))
    }

    @Test
    fun `matches by initials substring`() {
        val song = Song("id", "凤凰传奇", "月亮之上", Category.CHINESE_GIRL, "u", null)
        assertTrue(PinyinInitials.matches(song, "fhcq"))
        assertTrue(PinyinInitials.matches(song, "ylzs"))
        assertTrue(PinyinInitials.matches(song, "月亮"))
    }

    @Test
    fun `matchesText applies the same rules to a bare singer name`() {
        // v1.0.46: the TV browse page's search field filters the
        // singers list with the same text/pinyin rules as songs.
        assertTrue(PinyinInitials.matchesText("凤凰传奇", "凤凰"))
        assertTrue(PinyinInitials.matchesText("凤凰传奇", "fhcq"))
        assertTrue(PinyinInitials.matchesText("Ed Sheeran", "sheeran"))
        assertTrue(PinyinInitials.matchesText("Ed Sheeran", "eds"))
        assertTrue(PinyinInitials.matchesText("凤凰传奇", ""))
        assertTrue(!PinyinInitials.matchesText("凤凰传奇", "周杰伦"))
        assertTrue(!PinyinInitials.matchesText("凤凰传奇", "zjl"))
    }
}
