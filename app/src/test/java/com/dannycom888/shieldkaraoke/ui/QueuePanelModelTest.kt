package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.queue.QueueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player queue panel's row model.
 *
 * v1.0.51: the panel lists only the songs waiting BEHIND the
 * next one up — neither the song being sung (it has its own
 * line over the lyrics) nor the next song (the banner / card
 * already announces it) gets a row. Rows are renumbered from
 * 1 inside the panel but keep their real waiting-list index
 * for the remote actions. (The pre-v1.0.51 contract — current
 * row with ▶ plus every waiting song — is retired; the phone
 * page's full queue view is a different surface and is not
 * built from this model.)
 */
class QueuePanelModelTest {

    private fun entry(id: String, by: String = "点歌人$id") = QueueEntry(
        Song(id, "歌手$id", "歌$id", Category.CHINESE_BOY, "uri://$id", null), by,
    )

    @Test
    fun `next up gets no row, the rest renumber from one`() {
        val rows = QueuePanelModel.rows(
            listOf(entry("1"), entry("2"), entry("3"), entry("4")),
        )
        assertEquals(3, rows.size)
        // waiting[0] (id 1, the next song) is skipped entirely.
        assertEquals(listOf("2", "3", "4"), rows.map { it.entry.song.id })
        // Panel numbers restart at 1…
        assertEquals(listOf(1, 2, 3), rows.map { it.number })
        // …while the queue indices the actions use stay real.
        assertEquals(listOf(1, 2, 3), rows.map { it.waitingIndex })
    }

    @Test
    fun `a lone next up leaves the panel empty and hidden`() {
        assertTrue(QueuePanelModel.rows(listOf(entry("1"))).isEmpty())
        assertFalse(QueuePanelModel.isVisible(listOf(entry("1"))))
    }

    @Test
    fun `nothing waiting hides the panel`() {
        assertTrue(QueuePanelModel.rows(emptyList()).isEmpty())
        assertFalse(QueuePanelModel.isVisible(emptyList()))
    }

    @Test
    fun `two waiting songs are the first visible panel`() {
        val waiting = listOf(entry("1"), entry("2"))
        assertTrue(QueuePanelModel.isVisible(waiting))
        val rows = QueuePanelModel.rows(waiting)
        assertEquals(1, rows.size)
        assertEquals("2", rows[0].entry.song.id)
        assertEquals(1, rows[0].number)
        assertEquals(1, rows[0].waitingIndex)
    }

    @Test
    fun `promote is offered only below the first waiting song`() {
        assertFalse(QueuePanelModel.canPromote(-1)) // not a waiting row at all
        assertFalse(QueuePanelModel.canPromote(0)) // already next up
        assertTrue(QueuePanelModel.canPromote(1))
        assertTrue(QueuePanelModel.canPromote(5))
    }

    @Test
    fun `play now is offered for every waiting row, first included`() {
        // v1.0.27: unlike promote, 现在唱 makes sense even for the
        // song already first in line — it skips the current song's
        // remainder instead of waiting for it to end.
        assertFalse(QueuePanelModel.canPlayNow(-1)) // not a waiting row
        assertTrue(QueuePanelModel.canPlayNow(0))
        assertTrue(QueuePanelModel.canPlayNow(1))
        assertTrue(QueuePanelModel.canPlayNow(5))
    }
}
