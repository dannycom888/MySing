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
 *  entry — changing one's pick afterwards only affects the *next*
 *  order; it never reaches back into an existing entry (the old
 *  mid-song live swap is gone).
 *
 *  v1.0.56 (the user loosens the rule): while the entry is still
 *  WAITING, its orderer may re-pick this song's dancer from the
 *  phone page — [PlayQueue.changeWaitingDancer] is the only write
 *  path, and it only ever touches waiting entries. The moment the
 *  entry becomes current — singing, or locked as the handover
 *  ceremony's next song — the field is frozen for good. That is
 *  why it is a var: mutable during the waiting window, by exactly
 *  one guarded method. A null key (filler, or an order that carried
 *  no pick anywhere) resolves to the default dancer at read time,
 *  the same fallback [com.dannycom888.shieldkaraoke.ui.Dancers.byKey]
 *  has always applied. */
data class QueueEntry(
    val song: Song,
    val orderedBy: String,
    val source: EntrySource = EntrySource.ORDERED,
    var dancerKey: String? = null,
) {
    val isFiller: Boolean get() = source == EntrySource.FILLER
}

/** Outcome of a waiting entry's dancer change (v1.0.56). The
 *  phone server maps each value to its own readable reply, so the
 *  phone can say WHY a change did not happen instead of failing
 *  silently. */
enum class DancerChangeResult {
    /** The waiting entry now carries the new dancer. */
    CHANGED,

    /** The song is in the queue, but under somebody else's name. */
    NOT_YOURS,

    /** The song has left the waiting list because it is the
     *  current one — singing, or locked as the ceremony's next
     *  song. Its dancer is frozen. */
    LOCKED,

    /** No such song anywhere in the queue any more. */
    NOT_FOUND,
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
     * Re-pick the dancer of a song that is still WAITING (v1.0.56):
     * the orderer taps their queued song on the phone page and
     * chooses another dancer. Only the entry's own orderer may do
     * this, and only while the entry waits — the moment it becomes
     * current (singing, or locked as the ceremony's next song) the
     * dancer is frozen, which is why this method searches the
     * waiting list only and reports a current entry as
     * [DancerChangeResult.LOCKED].
     *
     * The phone addresses the entry by its waiting [index] plus
     * the song id it saw; queues shift under it (deletes, jumps),
     * so the index is a disambiguation hint among the orderer's
     * own duplicate orders of the same song, never the identity:
     * an entry is only ever changed when BOTH the song id and the
     * orderer name match it. Races resolve here, at the server,
     * exactly as the result enum reports them.
     */
    @Synchronized
    fun changeWaitingDancer(
        index: Int,
        songId: String,
        orderedBy: String,
        dancerKey: String,
    ): DancerChangeResult {
        val addressed = waiting.getOrNull(index)
            ?.takeIf { it.song.id == songId && it.orderedBy == orderedBy }
        val entry = addressed
            ?: waiting.firstOrNull { it.song.id == songId && it.orderedBy == orderedBy }
        if (entry != null) {
            entry.dancerKey = dancerKey
            return DancerChangeResult.CHANGED
        }
        if (waiting.any { it.song.id == songId }) return DancerChangeResult.NOT_YOURS
        val cur = current
        if (cur != null && cur.song.id == songId && cur.orderedBy == orderedBy) {
            return DancerChangeResult.LOCKED
        }
        return DancerChangeResult.NOT_FOUND
    }

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
