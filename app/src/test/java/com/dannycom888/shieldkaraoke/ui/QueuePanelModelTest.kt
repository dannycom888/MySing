package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.queue.QueueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.19: the player queue panel's row model — current song first
 * with ▶ and no number, waiting songs numbered from 1 with their
 * waiting-list index attached for the remote actions.
 */
class QueuePanelModelTest {

    private fun entry(id: String, by: String = "点歌人$id") = QueueEntry(
        Song(id, "歌手$id", "歌$id", Category.CHINESE_BOY, "uri://$id", null), by,
    )

    @Test
    fun `current first unnumbered, waiting numbered from one`() {
        val rows = QueuePanelModel.rows(entry("1"), listOf(entry("2"), entry("3")))
        assertEquals(3, rows.size)
        assertTrue(rows[0].isCurrent)
        assertNull(rows[0].number)
        assertEquals(-1, rows[0].waitingIndex)
        assertEquals("1", rows[0].entry.song.id)
        assertFalse(rows[1].isCurrent)
        assertEquals(1, rows[1].number)
        assertEquals(0, rows[1].waitingIndex)
        assertEquals(2, rows[2].number)
        assertEquals(1, rows[2].waitingIndex)
    }

    @Test
    fun `nothing at all gives no rows and hides the panel`() {
        assertTrue(QueuePanelModel.rows(null, emptyList()).isEmpty())
        assertFalse(QueuePanelModel.isVisible(null, emptyList()))
    }

    @Test
    fun `current alone still shows, as a single current row`() {
        val rows = QueuePanelModel.rows(entry("1"), emptyList())
        assertEquals(1, rows.size)
        assertTrue(rows[0].isCurrent)
        assertTrue(QueuePanelModel.isVisible(entry("1"), emptyList()))
    }

    @Test
    fun `waiting without a current song numbers from one`() {
        val rows = QueuePanelModel.rows(null, listOf(entry("2"), entry("3")))
        assertEquals(listOf(1, 2), rows.map { it.number })
        assertEquals(listOf(0, 1), rows.map { it.waitingIndex })
        assertTrue(rows.none { it.isCurrent })
        assertTrue(QueuePanelModel.isVisible(null, listOf(entry("2"))))
    }

    @Test
    fun `promote is offered only below the first waiting song`() {
        assertFalse(QueuePanelModel.canPromote(-1)) // the current row
        assertFalse(QueuePanelModel.canPromote(0)) // already next up
        assertTrue(QueuePanelModel.canPromote(1))
        assertTrue(QueuePanelModel.canPromote(5))
    }
}
