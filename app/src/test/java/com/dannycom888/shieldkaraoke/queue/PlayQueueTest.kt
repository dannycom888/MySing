package com.dannycom888.shieldkaraoke.queue

import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayQueueTest {

    private fun song(id: String, artist: String = "歌手$id", title: String = "歌$id") =
        Song(id, artist, title, Category.CHINESE_BOY, "uri://$id", null)

    @Test
    fun `first order becomes current at once`() {
        val q = PlayQueue()
        val r = q.add(song("1"), "小明")
        assertEquals(-1, r.position)
        assertEquals("1", q.current?.song?.id)
        assertFalse(r.wasDuplicate)
    }

    @Test
    fun `ordering never interrupts the song being sung`() {
        val q = PlayQueue()
        q.add(song("1"), "小明")
        q.add(song("2"), "小红")
        q.add(song("3"), "小明")
        assertEquals("1", q.current?.song?.id) // still the first song
        assertEquals(listOf("2", "3"), q.waitingList.map { it.song.id })
    }

    @Test
    fun `next up banner text format`() {
        val q = PlayQueue()
        q.add(song("1", "周杰伦", "晴天"), "小明")
        q.add(song("2", "凤凰传奇", "最炫民族风"), "小红")
        assertEquals("下一首：小红点的 凤凰传奇 - 最炫民族风", q.nextUpText())
    }

    @Test
    fun `no banner when nothing waits`() {
        val q = PlayQueue()
        q.add(song("1"), "小明")
        assertNull(q.nextUpText())
    }

    @Test
    fun `duplicate order is flagged`() {
        val q = PlayQueue()
        q.add(song("1"), "小明")
        val r = q.add(song("1"), "小红")
        assertTrue(r.wasDuplicate)
    }

    @Test
    fun `ahead count counts current plus waiting ahead`() {
        val q = PlayQueue()
        q.add(song("1"), "a")
        val r2 = q.add(song("2"), "b")
        val r3 = q.add(song("3"), "c")
        assertEquals(1, q.aheadCountFor(r2.position)) // only the current song ahead
        assertEquals(2, q.aheadCountFor(r3.position))
    }

    @Test
    fun `finishing advances to next, quitting too, empty at the end`() {
        val q = PlayQueue()
        q.add(song("1"), "a"); q.add(song("2"), "b"); q.add(song("3"), "c")
        assertEquals("2", q.finishCurrent()?.song?.id)
        assertEquals("3", q.quitCurrent()?.song?.id)
        assertNull(q.finishCurrent())
        assertTrue(q.isIdle)
    }

    @Test
    fun `anyone can remove a waiting song, current is untouchable`() {
        val q = PlayQueue()
        q.add(song("1"), "a"); q.add(song("2"), "b"); q.add(song("3"), "c")
        q.removeWaitingAt(0)
        assertEquals(listOf("3"), q.waitingList.map { it.song.id })
        assertEquals("1", q.current?.song?.id)
        assertNull(q.removeWaitingAt(5))
    }
}
