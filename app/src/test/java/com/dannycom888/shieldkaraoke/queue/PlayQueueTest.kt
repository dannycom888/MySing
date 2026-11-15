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
    fun `next up banner in english keeps the song name as-is`() {
        // v1.0.13: the banner follows the TV language; the song
        // name is file-name data and is never translated.
        val q = PlayQueue()
        q.add(song("1", "周杰伦", "晴天"), "小明")
        q.add(song("2", "凤凰传奇", "最炫民族风"), "William")
        assertEquals(
            "Next up: 凤凰传奇 - 最炫民族风 (ordered by William)",
            q.nextUpText(com.dannycom888.shieldkaraoke.i18n.UiLang.EN),
        )
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

    // --- v1.0.19: TV remote promote （下一首唱） ---

    @Test
    fun `promote moves a waiting song to the front, others keep their order`() {
        val q = PlayQueue()
        q.add(song("1"), "a"); q.add(song("2"), "b"); q.add(song("3"), "c"); q.add(song("4"), "d")
        val promoted = q.promoteWaitingAt(2)
        assertEquals("4", promoted?.song?.id)
        assertEquals(listOf("4", "2", "3"), q.waitingList.map { it.song.id })
        assertEquals("1", q.current?.song?.id) // current never touched
    }

    @Test
    fun `promote the song already first is a no-op`() {
        val q = PlayQueue()
        q.add(song("1"), "a"); q.add(song("2"), "b"); q.add(song("3"), "c")
        val promoted = q.promoteWaitingAt(0)
        assertEquals("2", promoted?.song?.id)
        assertEquals(listOf("2", "3"), q.waitingList.map { it.song.id })
    }

    @Test
    fun `promote out of range changes nothing`() {
        val q = PlayQueue()
        q.add(song("1"), "a"); q.add(song("2"), "b")
        assertNull(q.promoteWaitingAt(1))
        assertNull(q.promoteWaitingAt(-1))
        assertEquals(listOf("2"), q.waitingList.map { it.song.id })
        // And on a fully idle queue.
        val idle = PlayQueue()
        assertNull(idle.promoteWaitingAt(0))
        assertTrue(idle.isIdle)
    }

    @Test
    fun `promoted song is what the banner and the advance see`() {
        val q = PlayQueue()
        q.add(song("1", "周杰伦", "晴天"), "小明")
        q.add(song("2", "凤凰传奇", "最炫民族风"), "小红")
        q.add(song("3", "张信哲", "爱如潮水"), "小明")
        q.promoteWaitingAt(1)
        assertEquals("下一首：小明点的 张信哲 - 爱如潮水", q.nextUpText())
        assertEquals("3", q.finishCurrent()?.song?.id)
        assertEquals(listOf("2"), q.waitingList.map { it.song.id })
    }

    @Test
    fun `remove then promote keeps the remaining order straight`() {
        val q = PlayQueue()
        q.add(song("1"), "a"); q.add(song("2"), "b"); q.add(song("3"), "c"); q.add(song("4"), "d")
        q.removeWaitingAt(0) // drop "2" — indices shift
        assertEquals(listOf("3", "4"), q.waitingList.map { it.song.id })
        q.promoteWaitingAt(1) // "4" to the front
        assertEquals(listOf("4", "3"), q.waitingList.map { it.song.id })
        // Positions renumber cleanly: the new front has nothing waiting ahead.
        assertEquals(1, q.aheadCountFor(0))
    }
}
