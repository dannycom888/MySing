package com.dannycom888.shieldkaraoke.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryCacheTest {

    private val tree = "content://com.android.externalstorage.documents/tree/1234-5678%3AKaraoke"

    private val songs = listOf(
        Song(
            id = "music chinese boy|周杰伦 - 晴天",
            artist = "周杰伦", title = "晴天",
            category = Category.CHINESE_BOY,
            audioRef = "content://tree/music%20chinese%20boy/周杰伦%20-%20晴天.mp3",
            lyricsRef = "content://tree/music%20chinese%20boy/周杰伦%20-%20晴天.lrc",
        ),
        Song(
            id = "music english girl|adele - hello",
            artist = "Adele", title = "Hello",
            category = Category.ENGLISH_GIRL,
            audioRef = "content://tree/music%20english%20girl/Adele%20-%20Hello.mp3",
            lyricsRef = null, // no .lrc beside the MP3
        ),
        Song(
            id = "music chinese girl|无名歌",
            artist = "", title = "无名歌",
            category = Category.CHINESE_GIRL,
            audioRef = "content://tree/x.mp3",
            lyricsRef = null,
        ),
    )

    @Test
    fun roundTripPreservesEveryField() {
        val decoded = LibraryCache.decode(LibraryCache.encode(tree, songs), tree)
        assertEquals(songs, decoded)
    }

    @Test
    fun tabsNewlinesAndBackslashesInNamesSurvive() {
        val weird = listOf(
            Song(
                id = "id\twith\ttabs",
                artist = "A\\B", title = "line1\nline2",
                category = Category.ENGLISH_BOY,
                audioRef = "content://tree/a\tb.mp3",
                lyricsRef = "content://tree/a\rb.lrc",
            ),
        )
        val decoded = LibraryCache.decode(LibraryCache.encode(tree, weird), tree)
        assertEquals(weird, decoded)
    }

    @Test
    fun differentTreeUriVoidsTheCache() {
        val text = LibraryCache.encode(tree, songs)
        assertNull(LibraryCache.decode(text, "content://some/other/tree"))
    }

    @Test
    fun garbageAndTruncatedHeadersAreNoCache() {
        assertNull(LibraryCache.decode("", tree))
        assertNull(LibraryCache.decode("not a cache at all", tree))
        assertNull(LibraryCache.decode("MSING-CATALOG-CACHE\t99\twhatever\n", tree))
        assertNull(LibraryCache.decode("MSING-CATALOG-CACHE\t1\n", tree))
    }

    @Test
    fun damagedLinesAreSkippedGoodLinesSurvive() {
        val text = LibraryCache.encode(tree, songs)
        val lines = text.split('\n').toMutableList()
        lines.add(1, "this line is definitely not a song")
        lines.add(2, "only\tthree\tfields")
        lines.add(3, "id\ta\tt\tNO_SUCH_CATEGORY\taudio\t")
        val decoded = LibraryCache.decode(lines.joinToString("\n"), tree)
        assertEquals(songs, decoded)
    }

    @Test
    fun accompanimentPairingSurvivesTheRoundTrip() {
        // v1.0.47: the pairing is part of the cached catalog, so
        // a cold start shows the badges before the rescan lands.
        val paired = listOf(
            Song(
                id = "CHINESE_BOY|周杰伦 - 晴天",
                artist = "周杰伦", title = "晴天",
                category = Category.CHINESE_BOY,
                audioRef = "content://tree/music%20chinese%20boy/周杰伦%20-%20晴天.mp3",
                lyricsRef = "content://tree/music%20chinese%20boy/周杰伦%20-%20晴天.lrc",
                accompanimentRef = "content://tree/music%20chinese%20boy/周杰伦%20-%20晴天（伴奏）.mp3",
            ),
        )
        val decoded = LibraryCache.decode(LibraryCache.encode(tree, paired), tree)
        assertEquals(paired, decoded)
        assertEquals(true, decoded!![0].hasAccompanimentFile)
    }

    @Test
    fun version1CachesAreVoidAfterTheFormatBump() {
        // A v1 line has only six fields and no accompaniment
        // column; the v2 reader must treat the whole file as no
        // cache (the full scan rebuilds it), never half-read it.
        val v1 = "MSING-CATALOG-CACHE\t1\t" + tree + "\n" +
            "id\t周杰伦\t晴天\tCHINESE_BOY\taudio\tlyrics\n"
        assertNull(LibraryCache.decode(v1, tree))
    }

    @Test
    fun emptyLibraryEncodesToNoCache() {
        // An empty scan result must not masquerade as a usable cache:
        // decode treats "no songs" as "no cache" and the app falls
        // back to the plain scan-wait behaviour.
        assertNull(LibraryCache.decode(LibraryCache.encode(tree, emptyList()), tree))
    }
}
