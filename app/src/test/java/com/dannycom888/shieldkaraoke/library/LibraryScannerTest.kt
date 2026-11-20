package com.dannycom888.shieldkaraoke.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---- v1.0.47: accompaniment-file pairing -------------------
    // Fixtures use the user's real file-name shapes (Chinese
    // artist/title, full-width brackets) and go through the real
    // scan path — the pairing must hold for the library he
    // actually has, not for ASCII stand-ins.

    @Test
    fun `full-width accompaniment pairs with its original and is not a song`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
            file("music chinese boy", "周杰伦 - 晴天.lrc"),
            file("music chinese boy", "周杰伦 - 晴天（伴奏）.mp3"),
        ))
        assertEquals(1, songs.size) // the accompaniment never becomes a song
        val song = songs[0]
        assertEquals("晴天", song.title)
        assertTrue(song.hasAccompanimentFile)
        assertEquals("uri://music chinese boy/周杰伦 - 晴天（伴奏）.mp3", song.accompanimentRef)
        // The original's own pairing (lyrics) is untouched.
        assertEquals("uri://music chinese boy/周杰伦 - 晴天.lrc", song.lyricsRef)
    }

    @Test
    fun `half-width accompaniment also pairs`() {
        val songs = LibraryScanner.scan(listOf(
            file("music english girl", "Adele - Hello.mp3"),
            file("music english girl", "Adele - Hello(伴奏).mp3"),
        ))
        assertEquals(1, songs.size)
        assertTrue(songs[0].hasAccompanimentFile)
        assertEquals("uri://music english girl/Adele - Hello(伴奏).mp3", songs[0].accompanimentRef)
    }

    @Test
    fun `orphan accompaniment is silently dropped`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese girl", "邓紫棋 - 光年之外（伴奏）.mp3"),
        ))
        assertEquals(0, songs.size)
    }

    @Test
    fun `song without an accompaniment file has none`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese boy", "林俊杰 - 江南.mp3"),
        ))
        assertEquals(1, songs.size)
        assertFalse(songs[0].hasAccompanimentFile)
        assertNull(songs[0].accompanimentRef)
    }

    @Test
    fun `accompaniment in another folder does not pair`() {
        // Same name, different category folder: folder decides
        // everything in this library, pairing included.
        val songs = LibraryScanner.scan(listOf(
            file("music chinese boy", "张信哲 - 爱如潮水.mp3"),
            file("music chinese girl", "张信哲 - 爱如潮水（伴奏）.mp3"),
        ))
        assertEquals(1, songs.size)
        assertFalse(songs[0].hasAccompanimentFile)
    }

    @Test
    fun `same-name conflict resolves deterministically by file name order`() {
        // Both bracket shapes on disk for one original: the
        // winner is the first in plain file-name order (the
        // half-width name sorts before the full-width one), on
        // every rescan, regardless of listing order.
        val forward = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
            file("music chinese boy", "周杰伦 - 晴天（伴奏）.mp3"),
            file("music chinese boy", "周杰伦 - 晴天(伴奏).mp3"),
        ))
        val reversed = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天(伴奏).mp3"),
            file("music chinese boy", "周杰伦 - 晴天（伴奏）.mp3"),
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
        ))
        assertEquals(1, forward.size)
        assertEquals("uri://music chinese boy/周杰伦 - 晴天(伴奏).mp3", forward[0].accompanimentRef)
        assertEquals(forward[0].accompanimentRef, reversed[0].accompanimentRef)
    }

    @Test
    fun `an accompaniment-side lrc neither creates a song nor steals lyrics`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
            file("music chinese boy", "周杰伦 - 晴天（伴奏）.mp3"),
            file("music chinese boy", "周杰伦 - 晴天（伴奏）.lrc"),
        ))
        assertEquals(1, songs.size)
        assertNull(songs[0].lyricsRef) // only the original's own .lrc counts
        assertTrue(songs[0].hasAccompanimentFile)
    }

    @Test
    fun `a title ending in the bare word is an ordinary song`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese girl", "歌手 - 伴奏.mp3"),
        ))
        assertEquals(1, songs.size)
        assertEquals("伴奏", songs[0].title)
        assertFalse(songs[0].hasAccompanimentFile)
    }

    @Test
    fun `rescan picks up an accompaniment dropped in later`() {
        val before = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
        ))
        val after = LibraryScanner.scan(listOf(
            file("music chinese boy", "周杰伦 - 晴天.mp3"),
            file("music chinese boy", "周杰伦 - 晴天（伴奏）.mp3"),
        ))
        assertFalse(before[0].hasAccompanimentFile)
        assertTrue(after[0].hasAccompanimentFile)
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

    // ---- v1.0.74: hyphen spacing is optional on either side ----
    // The user's real deviating stems: most of the library is
    // "歌手 - 歌名", but these carry the space on one side of the
    // hyphen only, or none. The old bare-dash fallback demanded
    // no space before the hyphen, so they fell through unsplit —
    // blank artist, whole stem as title — and downstream the
    // LRC's title card stopped matching the song's pair.

    @Test
    fun `one-sided hyphen spacing still splits artist and title`() {
        assertEquals("张宇" to "雨一直下", LibraryScanner.splitArtistTitle("张宇 -雨一直下"))
        assertEquals("罗志祥" to "爱疯头", LibraryScanner.splitArtistTitle("罗志祥 -爱疯头"))
        assertEquals("周杰伦" to "夜曲", LibraryScanner.splitArtistTitle("周杰伦- 夜曲"))
        assertEquals("小虎队" to "红蜻蜓", LibraryScanner.splitArtistTitle("小虎队- 红蜻蜓"))
        assertEquals("王杰 王韵婵" to "祈祷", LibraryScanner.splitArtistTitle("王杰 王韵婵- 祈祷"))
        assertEquals("张宇" to "雨一直下", LibraryScanner.splitArtistTitle("张宇-雨一直下"))
    }

    @Test
    fun `the normal spaced form splits as before`() {
        assertEquals("Beyond" to "海阔天空", LibraryScanner.splitArtistTitle("Beyond - 海阔天空"))
        assertEquals("周杰伦" to "晴天", LibraryScanner.splitArtistTitle("周杰伦 - 晴天"))
    }

    @Test
    fun `a later hyphen inside the title survives intact`() {
        // The split takes the FIRST hyphen; everything after it
        // is the title, hyphens included.
        assertEquals("歌手" to "歌名-加长版", LibraryScanner.splitArtistTitle("歌手 - 歌名-加长版"))
        assertEquals("歌手" to "歌名-加长版", LibraryScanner.splitArtistTitle("歌手-歌名-加长版"))
    }

    @Test
    fun `a stem without any hyphen is all title`() {
        assertEquals("" to "红蜻蜓", LibraryScanner.splitArtistTitle("红蜻蜓"))
    }

    @Test
    fun `one-sided file pairs with its lrc and yields the pair`() {
        val songs = LibraryScanner.scan(listOf(
            file("music chinese boy", "张宇 -雨一直下.mp3"),
            file("music chinese boy", "张宇 -雨一直下.lrc"),
        ))
        assertEquals(1, songs.size)
        assertEquals("张宇", songs[0].artist)
        assertEquals("雨一直下", songs[0].title)
        assertTrue(songs[0].lyricsRef != null)
    }
}
