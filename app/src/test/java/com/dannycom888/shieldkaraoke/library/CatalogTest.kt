package com.dannycom888.shieldkaraoke.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    // 凤凰传奇 (a mixed group) sits in the GIRL folder, as in the real
    // library; other singers spread over the four folders.
    private val catalog = Catalog(LibraryScanner.scan(listOf(
        RawFile("music chinese girl", "凤凰传奇 - 最炫民族风.mp3", "u1"),
        RawFile("music chinese girl", "凤凰传奇 - 月亮之上.mp3", "u2"),
        RawFile("music chinese boy", "周杰伦 - 晴天.mp3", "u3"),
        RawFile("music chinese girl", "邓紫棋 - 光年之外.mp3", "u4"),
        RawFile("music english boy", "Ed Sheeran - Shape of You.mp3", "u5"),
        RawFile("music english girl", "Adele - Hello.mp3", "u6"),
    )))

    @Test
    fun `acceptance - pick Chinese then singers finds Fenghuang Chuanqi`() {
        val chineseSingers = catalog.singers(Language.CHINESE, null)
        assertTrue("凤凰传奇 must appear in the Chinese singer list", "凤凰传奇" in chineseSingers)
        assertTrue("周杰伦" in chineseSingers)
        assertTrue("邓紫棋" in chineseSingers)
        assertFalse("Adele" in chineseSingers) // English never leaks into Chinese
    }

    @Test
    fun `home singers merges every language and gender`() {
        val all = catalog.singers()
        assertEquals(setOf("凤凰传奇", "周杰伦", "邓紫棋", "Ed Sheeran", "Adele"), all.toSet())
    }

    @Test
    fun `gender sub-filter narrows only when picked`() {
        assertEquals(setOf("周杰伦"), catalog.singers(Language.CHINESE, Gender.MALE).toSet())
        // 凤凰传奇 follows its folder (girl) under the female filter.
        assertEquals(setOf("凤凰传奇", "邓紫棋"), catalog.singers(Language.CHINESE, Gender.FEMALE).toSet())
    }

    @Test
    fun `songs of a singer span both of that language folders when unfiltered`() {
        assertEquals(2, catalog.songsOf("凤凰传奇").size)
    }

    @Test
    fun `search by pinyin initials and by text`() {
        assertTrue(catalog.search("fhcq").any { it.artist == "凤凰传奇" })
        assertTrue(catalog.search("晴天").any { it.title == "晴天" })
        assertTrue(catalog.search("adele").any { it.artist == "Adele" })
        assertTrue(catalog.search("不存在").isEmpty())
    }
}
