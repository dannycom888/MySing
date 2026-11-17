package com.dannycom888.shieldkaraoke.queue

import com.dannycom888.shieldkaraoke.i18n.TvKey
import com.dannycom888.shieldkaraoke.i18n.TvStrings
import com.dannycom888.shieldkaraoke.i18n.UiLang
import com.dannycom888.shieldkaraoke.library.Song

/**
 * Where a now-playing entry came from (v1.0.20). The distinction
 * drives real behaviour — applause, the next-up banner, the queue
 * panel's label and whether a new order cuts it off — so it is a
 * typed marker on the entry, never a guess from the orderer name.
 */
enum class EntrySource { ORDERED, FILLER }

/** One queued song and who ordered it (name from the phone page).
 *  Filler entries (idle random background songs) carry
 *  [EntrySource.FILLER] and an empty orderer; they are constructed
 *  standalone and never added to the [PlayQueue], so the phones'
 *  queue view never sees them.
 *
 *  v1.0.52 (the user's call): the dancer is bound to the *song*,
 *  not to the orderer. [dancerKey] is a snapshot of the dancer the
 *  orderer had picked at the moment of ordering, frozen into the
 *  entry for its whole life — waiting, singing, ceremony keeper.
 *  Changing one's pick afterwards only affects the *next* order;
 *  it never reaches back into an existing entry (the old mid-song
 *  live swap is gone). A null key (filler, or an order that carried
 *  no pick anywhere) resolves to the default dancer at read time,
 *  the same fallback [com.dannycom888.shieldkaraoke.ui.Dancers.byKey]
 *  has always applied. */
data class QueueEntry(
    val song: Song,
    val orderedBy: String,
    val source: EntrySource = EntrySource.ORDERED,
    val dancerKey: String? = null,
) {
    val isFiller: Boolean get() = source == EntrySource.FILLER
}

/** Result of adding a song: where it landed and whether it was
 *  already in the queue (the phone page turns that into a warning). */
data class AddResult(
    /** 0-based position in the waiting queue after adding. */
    val position: Int,
    /** Songs that will be sung before this one, incl. the current song. */
    val aheadCount: Int,
    val wasDuplicate: Boolean,
)

/**
 * The shared KTV queue. Ordering a song — from a phone or the TV
 * browse screen — only ever appends; it never interrupts the song
 * being sung. The current song ends only by finishing (applause,
 * auto-advance) or by the singer pressing 不唱了 (no applause).
 *
 * v1.0.19 (the user's call, 2026-11-15, replacing the old
 * phone-only-queue / no-promote rules): the TV remote holder manages
 * the waiting list from the on-screen queue panel — delete any
 * waiting song, or promote one to the front ("下一首唱"). Promote is
 * a TV-remote privilege only: the phone API has no such endpoint.
 * Neither operation ever touches the current song.
 *
 * v1.0.27 (the user's follow-up call, same day): the panel gains a
 * third remote action, "现在唱" — cut straight to a waiting song.
 * That one DOES replace the current song, deliberately: the cut
 * song is dropped unfinished (the UI treats it like 不唱了 — no
 * applause, no ceremony) and the chosen song becomes current at
 * once. Still a TV-remote privilege only.
 */
class PlayQueue {

    var current: QueueEntry? = null
        private set

    private val waiting = mutableListOf<QueueEntry>()

    val waitingList: List<QueueEntry> get() = waiting.toList()
    val isIdle: Boolean get() = current == null && waiting.isEmpty()

    /** Append a song. When nothing is playing it becomes the current
     *  song at once (the caller starts playback); otherwise it waits.
     *  [dancerKey] is the orderer's pick at this moment, snapshotted
     *  into the new entry (v1.0.52 — see [QueueEntry.dancerKey]). */
    @Synchronized
    fun add(song: Song, orderedBy: String, dancerKey: String? = null): AddResult {
        val duplicate = waiting.any { it.song.id == song.id } || current?.song?.id == song.id
        val entry = QueueEntry(song, orderedBy, dancerKey = dancerKey)
        if (current == null) {
            current = entry
            return AddResult(position = -1, aheadCount = 0, wasDuplicate = duplicate)
        }
        waiting += entry
        return AddResult(
            position = waiting.lastIndex,
            aheadCount = waiting.size, // current song + everyone waiting ahead of the new one, minus itself → size-1 +1
            wasDuplicate = duplicate,
        )
    }

    /** The "next up" banner text for the TV: in Chinese exactly
     *  `下一首：{点歌人}点的 {歌手} - {歌名}`; the wording lives in
     *  the zh/en string table (v1.0.13) and the song name passes
     *  through untranslated. Null when nothing waits. */
    fun nextUpText(lang: UiLang = UiLang.ZH): String? {
        val next = waiting.firstOrNull() ?: return null
        return TvStrings.get(lang, TvKey.NEXT_UP, next.song.displayName, next.orderedBy)
    }

    fun peekNext(): QueueEntry? = waiting.firstOrNull()

    /** Current song finished naturally → applause, then advance. */
    @Synchronized
    fun finishCurrent(): QueueEntry? = advance()

    /** Singer pressed 不唱了 → advance without applause semantics
     *  (the return value is the same; the UI skips the applause). */
    @Synchronized
    fun quitCurrent(): QueueEntry? = advance()

    private fun advance(): QueueEntry? {
        current = if (waiting.isEmpty()) null else waiting.removeAt(0)
        return current
    }

    /** Remove a waiting song by index (shared queue: anyone may). */
    @Synchronized
    fun removeWaitingAt(index: Int): QueueEntry? =
        if (index in waiting.indices) waiting.removeAt(index) else null

    /**
     * Move the waiting song at [index] to the front of the waiting
     * list — the TV panel's "下一首唱" (v1.0.19): once the current
     * song ends, this one sings. The current song is untouched and
     * every other waiting song keeps its relative order. Promoting
     * the song already at the front is a no-op that returns it.
     * Out-of-range indices return null and change nothing.
     */
    @Synchronized
    fun promoteWaitingAt(index: Int): QueueEntry? {
        if (index !in waiting.indices) return null
        if (index == 0) return waiting[0]
        val entry = waiting.removeAt(index)
        waiting.add(0, entry)
        return entry
    }

    /**
     * Take the waiting song at [index] out of the waiting list and
     * make it the current song at once — the TV panel's "现在唱"
     * (v1.0.27): the remote holder cuts straight to this song,
     * including the one already first in line (that simply skips
     * the rest of the current song). The previous current song is
     * dropped unfinished — it was cut, not completed, so it gets
     * no finish semantics — and every other waiting song keeps its
     * relative order. When nothing is current (an idle filler song
     * is on the stage — filler never enters this queue), the chosen
     * song simply becomes current. Out-of-range indices return
     * null and change nothing.
     */
    @Synchronized
    fun jumpWaitingAt(index: Int): QueueEntry? {
        if (index !in waiting.indices) return null
        val entry = waiting.removeAt(index)
        current = entry
        return entry
    }

    @Synchronized
    fun clearWaiting() = waiting.clear()

    /** "前面还有 X 首" for a waiting position: the current song plus
     *  the waiting songs ahead of [position]. */
    fun aheadCountFor(position: Int): Int = (if (current != null) 1 else 0) + position
}
