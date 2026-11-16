package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.queue.QueueEntry

/**
 * Pure row model for the player screen's queue panel.
 *
 * v1.0.51 (the user's call, 2026-11-16): the panel no longer
 * lists the song being sung, nor the next one up. The singing
 * song already has its own line over the lyrics, and the next
 * song already has the banner / big card announcing it — the
 * panel is only for what is still waiting BEHIND the next song.
 * So the rows are the waiting list minus its head, renumbered
 * from 1 inside the panel; each row keeps its real index into
 * [PlayQueue.waitingList] so the remote actions （现在唱 /
 * 下一首唱 / 删除） call the queue with it directly. (Up to
 * v1.0.50 the panel mirrored the phones' view: current song
 * first with ▶, then every waiting song.)
 *
 * The phones' queue view is NOT built from this model and does
 * not change: on the phone page the full queue still shows.
 *
 * Kept free of Compose so the numbering / visibility / action
 * rules are unit tested; the composable only renders what this
 * builds.
 */
object QueuePanelModel {

    data class Row(
        val entry: QueueEntry,
        /** 1-based number inside the panel (renumbered: the
         *  first row shown is 1, even though it is the second
         *  song in the waiting list). */
        val number: Int,
        /** Index into the waiting list — the queue's own index,
         *  which the remote actions use. Always >= 1 for a panel
         *  row: index 0 is the next song up and never gets a row. */
        val waitingIndex: Int,
    )

    /** The waiting list's head (index 0) is the next song up:
     *  the banner already announces it, so the panel starts at
     *  index 1 and numbers its rows from 1 again. */
    fun rows(waiting: List<QueueEntry>): List<Row> =
        waiting.drop(1).mapIndexed { panelIndex, entry ->
            Row(entry = entry, number = panelIndex + 1, waitingIndex = panelIndex + 1)
        }

    /** The panel renders only when it has a row to list — with
     *  nothing waiting behind the next song there is no panel at
     *  all, not an empty shell with just its title. */
    fun isVisible(waiting: List<QueueEntry>): Boolean = waiting.size >= 2

    /**
     * "下一首唱" only makes sense for a song that is not already
     * first in line: index 0 is up next anyway, so the action is
     * not offered for it (deleting it stays available). Panel
     * rows always sit at index >= 1, so for them it is always
     * offered — the rule itself is unchanged.
     */
    fun canPromote(waitingIndex: Int): Boolean = waitingIndex > 0

    /**
     * "现在唱" is offered for every waiting row (v1.0.27) —
     * including the song already first in line, where it means
     * "skip the rest of the current song and sing this one right
     * away" instead of waiting for the current song to end.
     */
    fun canPlayNow(waitingIndex: Int): Boolean = waitingIndex >= 0
}
