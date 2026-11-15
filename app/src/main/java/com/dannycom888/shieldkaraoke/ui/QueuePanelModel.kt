package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.queue.QueueEntry

/**
 * Pure row model for the player screen's queue panel (v1.0.19).
 *
 * The panel lists the queue exactly as the phones see it: the
 * current song first, marked ▶ and carrying no number (it is not
 * "in line" — it is already singing), then the waiting songs
 * numbered 1, 2, 3… in singing order. Each waiting row remembers
 * its index into [PlayQueue.waitingList] so the remote actions
 * （现在唱 / 下一首唱 / 删除） can call the queue with it directly.
 *
 * Kept free of Compose so the numbering / action rules are unit
 * tested; the composable only renders what this builds.
 */
object QueuePanelModel {

    data class Row(
        val entry: QueueEntry,
        /** True for the song being sung (▶, no number, no actions). */
        val isCurrent: Boolean,
        /** 1-based singing order for waiting rows; null for current. */
        val number: Int?,
        /** Index into the waiting list; -1 for the current row. */
        val waitingIndex: Int,
        /** True when the current row is an idle filler song
         *  (v1.0.20): the panel labels it 随机/Random instead of an
         *  orderer name. Waiting rows are always real orders. */
        val isFiller: Boolean = false,
    )

    fun rows(current: QueueEntry?, waiting: List<QueueEntry>): List<Row> {
        val out = ArrayList<Row>(waiting.size + 1)
        if (current != null) {
            out += Row(
                entry = current, isCurrent = true, number = null,
                waitingIndex = -1, isFiller = current.isFiller,
            )
        }
        waiting.forEachIndexed { index, entry ->
            out += Row(entry = entry, isCurrent = false, number = index + 1, waitingIndex = index)
        }
        return out
    }

    /** The panel renders only when there is something to show —
     *  an idle player gets no empty box. */
    fun isVisible(current: QueueEntry?, waiting: List<QueueEntry>): Boolean =
        current != null || waiting.isNotEmpty()

    /**
     * "下一首唱" only makes sense for a song that is not already
     * first in line: index 0 is up next anyway, so the action is
     * not offered for it (deleting it stays available).
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
