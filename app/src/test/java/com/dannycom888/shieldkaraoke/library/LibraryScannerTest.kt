package com.dannycom888.shieldkaraoke.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScannerTest {

    private fun file(folder: String, name: String) = RawFile(folder, name, "uri://$folder/$name")

    @Test
    fun `mp3 and same-stem lrc pair into one song, category from folder`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
            file("music chinese boy", "周杰伦 - 晴天.lrc"),
        ))
        assertEquals(1, songs.size)
        assertEquals("周杰伦", songs[0].artist)
        assertEquals("晴天", songs[0].title)
        assertEquals(Category.CHINESE_BOY, songs[0].category)
        assertTrue(songs[0].lyricsRef != null)
    }

    @Test
    fun `tags are ignored - only file names decide`() {
        // A file whose name disagrees with any tag still wins by name.
        val songs = LibraryScanner.scan(listOf(file("music english girl", "Adele - Hello.mp3")))
        assertEquals("Adele", songs[0].artist)
        assertEquals("Hello", songs[0].title)
        assertEquals(null, songs[0].lyricsRef) // no lrc: still listed, no lyrics
    }

    @Test
    fun `rescan drops deleted songs and picks up new ones`() {
        val before = LibraryScanner.scan(listOf(file("music chinese girl", "邓紫棋 - 光年之外.mp3")))
        val after = LibraryScanner.scan(listOf(file("music chinese girl", "邓紫棋 - 喜欢你.mp3")))
        assertEquals("光年之外", before[0].title)
        assertEquals("喜欢你", after[0].title) // old song gone after rescan
    }

    @Test
    fun `files outside the four category folders are ignored`() {
        val songs = LibraryScanner.scan(listOf(
            file("downloads", "Someone - Something.mp3"),
            file("music chinese boy", "Beyond - 海阔天空.mp3"),
        ))
        assertEquals(1, songs.size)
        assertEquals("Beyond", songs[0].artist)
    }

    @Test
    fun `group act dropped in a girl folder is classified by that folder`() {
        // User ruling: groups are not separated; folder decides.
        val songs = LibraryScanner.scan(listOf(
            file("music chinese girl", "凤凰传奇 - 最炫民族风.mp3"),
            file("music chinese girl", "凤凰传奇 - 最炫民族风.lrc"),
        ))
        assertEquals(Category.CHINESE_GIRL, songs[0].category)
        assertEquals("凤凰传奇", songs[0].artist)
    }
}
