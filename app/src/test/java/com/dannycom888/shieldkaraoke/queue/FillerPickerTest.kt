package com.dannycom888.shieldkaraoke.queue

import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** v1.0.20 idle filler: picker no-repeat window + policy gates. */
class FillerPickerTest {

    private fun song(id: String) =
        Song(id, "歌手$id", "歌$id", Category.CHINESE_BOY, "uri://$id", null)

    private fun catalog(n: Int): List<Song> = (1..n).map { song(it.toString()) }

    @Test
    fun `empty catalog picks nothing`() {
        assertNull(FillerPicker(Random(1)).pick(emptyList()))
    }

    @Test
    fun `single song catalog always returns that song`() {
        val picker = FillerPicker(Random(7))
        repeat(5) { assertEquals("1", picker.pick(catalog(1))?.id) }
    }

    @Test
    fun `two song catalog strictly alternates`() {
        // Window = min(8, size-1) = 1: the song just played is the
        // one excluded, so the picks must alternate.
        val picker = FillerPicker(Random(3))
        val picks = (1..10).map { picker.pick(catalog(2))!!.id }
        for (i in 1 until picks.size) {
            assertTrue("picks $picks repeat at $i", picks[i] != picks[i - 1])
        }
    }

    @Test
    fun `no repeat inside the recent window`() {
        // 20 songs, window 8: within any 9 consecutive picks every
        // song differs from the previous 8.
        val picker = FillerPicker(Random(42))
        val picks = (1..200).map { picker.pick(catalog(20))!!.id }
        for (i in picks.indices) {
            val back = minOf(FillerPicker.RECENT_WINDOW, i)
            for (d in 1..back) {
                assertTrue(
                    "pick $i (${picks[i]}) repeats pick ${i - d} within window",
                    picks[i] != picks[i - d],
                )
            }
        }
    }

    @Test
    fun `small catalog window shrinks to size minus one`() {
        // 5 songs → window 4: after the first pick, each next pick
        // is forced (only one candidate left out of the last 4),
        // and no song repeats within 4 plays.
        val picker = FillerPicker(Random(9))
        val picks = (1..50).map { picker.pick(catalog(5))!!.id }
        for (i in picks.indices) {
            for (d in 1..minOf(4, i)) {
                assertTrue("picks $picks repeat at distance $d", picks[i] != picks[i - d])
            }
        }
        // And every song does come around again (no starvation).
        assertEquals(setOf("1", "2", "3", "4", "5"), picks.toSet())
    }

    @Test
    fun `reset forgets the no-repeat history`() {
        val picker = FillerPicker(Random(5))
        val songs = catalog(3)
        picker.pick(songs)
        picker.reset()
        // After a reset the picker must still return valid songs
        // from the catalog (history no longer constrains it).
        repeat(10) { assertTrue(picker.pick(songs) in songs) }
    }

    // ---------- FillerPolicy gates ----------

    @Test
    fun `auto start needs every gate open`() {
        assertTrue(FillerPolicy.shouldAutoStart(true, true, true, false))
        assertFalse("switch off", FillerPolicy.shouldAutoStart(false, true, true, false))
        assertFalse("no library yet", FillerPolicy.shouldAutoStart(true, false, true, false))
        assertFalse("real queue busy", FillerPolicy.shouldAutoStart(true, true, false, false))
        assertFalse("already playing", FillerPolicy.shouldAutoStart(true, true, true, true))
    }

    @Test
    fun `applause only for ordered songs`() {
        assertTrue(FillerPolicy.applauseOnNaturalEnd(EntrySource.ORDERED))
        assertFalse(FillerPolicy.applauseOnNaturalEnd(EntrySource.FILLER))
        assertFalse(FillerPolicy.applauseOnNaturalEnd(null))
    }

    @Test
    fun `only filler yields to a new order`() {
        assertTrue(FillerPolicy.yieldsToOrder(EntrySource.FILLER))
        assertFalse(FillerPolicy.yieldsToOrder(EntrySource.ORDERED))
        assertFalse(FillerPolicy.yieldsToOrder(null))
    }

    @Test
    fun `queue entries default to ordered, filler is marked`() {
        val ordered = QueueEntry(song("1"), "小明")
        assertEquals(EntrySource.ORDERED, ordered.source)
        assertFalse(ordered.isFiller)
        val filler = QueueEntry(song("2"), "", EntrySource.FILLER)
        assertTrue(filler.isFiller)
    }
}
