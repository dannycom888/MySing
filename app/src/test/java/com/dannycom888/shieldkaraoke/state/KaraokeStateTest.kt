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
}
