package com.dannycom888.shieldkaraoke.state

import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.queue.EntrySource
import com.dannycom888.shieldkaraoke.queue.QueueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.20: the yield seam at the state layer. While a filler song
 * is playing it lives only in [KaraokeState.nowPlaying] — the real
 * queue is idle — so the next real order must take the stage at
 * once through the existing onShouldStart path (that is what cuts
 * the filler off), and the filler leaves no trace in the queue.
 */
class KaraokeStateTest {

    private fun song(id: String) =
        Song(id, "歌手$id", "歌$id", Category.CHINESE_BOY, "uri://$id", null)

    @Test
    fun `order during filler starts at once and leaves the queue clean`() {
        val state = KaraokeState()
        // Filler playing: not in the queue, only mirrored as nowPlaying.
        state.nowPlaying = QueueEntry(song("99"), "", EntrySource.FILLER)
        assertTrue(state.queue.isIdle)

        var started: QueueEntry? = null
        state.onShouldStart = { started = it }

        state.order(song("1"), "小明")

        assertEquals("1", started?.song?.id)
        assertEquals(EntrySource.ORDERED, started?.source)
        assertEquals("1", state.queue.current?.song?.id)
        assertTrue(state.queue.waitingList.isEmpty())
    }

    @Test
    fun `each order keeps the dancer picked at its own moment`() {
        // v1.0.52: changing the pick between two orders must not
        // reach back into the first entry — not while it waits, and
        // not when it becomes the current song.
        val state = KaraokeState()
        state.order(song("1"), "小明", "ancient")
        state.order(song("2"), "小明", "baby")
        assertEquals("ancient", state.queue.current?.dancerKey)
        assertEquals("baby", state.queue.waitingList.first().dancerKey)
        state.advanceAfterFinish()
        assertEquals("2", state.queue.current?.song?.id)
        assertEquals("baby", state.queue.current?.dancerKey)
    }

    @Test
    fun `order while an ordered song plays just queues up`() {
        val state = KaraokeState()
        var starts = 0
        state.onShouldStart = { starts++ }

        state.order(song("1"), "小明")
        state.order(song("2"), "小红")

        assertEquals(1, starts) // only the first order started playback
        assertEquals("1", state.queue.current?.song?.id)
        assertEquals(listOf("2"), state.queue.waitingList.map { it.song.id })
        assertNull(state.queue.waitingList.first().takeIf { it.isFiller })
    }

    @Test
    fun `jump hands the chosen entry to the caller and makes it current`() {
        // v1.0.27: the panel's 现在唱 seam. The state layer only
        // moves the queue — starting playback is the Activity's job
        // (it must stop the cut song first), so onShouldStart must
        // NOT fire here.
        val state = KaraokeState()
        var starts = 0
        state.onShouldStart = { starts++ }
        state.order(song("1"), "小明")
        state.order(song("2"), "小红")
        state.order(song("3"), "小刚")
        assertEquals(1, starts)

        val jumped = state.jumpWaitingAt(1)

        assertEquals("3", jumped?.song?.id)
        assertEquals("小刚", jumped?.orderedBy)
        assertEquals("3", state.queue.current?.song?.id)
        assertEquals(listOf("2"), state.queue.waitingList.map { it.song.id })
        assertEquals(1, starts) // unchanged: no implicit start

        // A stale index (the row was deleted meanwhile) is a no-op.
        assertNull(state.jumpWaitingAt(5))
        assertEquals("3", state.queue.current?.song?.id)
    }
}
