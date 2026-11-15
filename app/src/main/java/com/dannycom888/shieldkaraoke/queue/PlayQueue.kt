package com.dannycom888.shieldkaraoke.queue

import com.dannycom888.shieldkaraoke.i18n.TvKey
import com.dannycom888.shieldkaraoke.i18n.TvStrings
import com.dannycom888.shieldkaraoke.i18n.UiLang
import com.dannycom888.shieldkaraoke.library.Song

/** One queued song and who ordered it (name from the phone page). */
data class QueueEntry(val song: Song, val orderedBy: String)

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
 * The shared KTV queue. The one inviolable rule: ordering a song only
 * ever appends — it never interrupts the song being sung. The current
 * song ends only by finishing (applause, auto-advance) or by the
 * singer pressing 不唱了 (no applause).
 */
class PlayQueue {

    var current: QueueEntry? = null
        private set

    private val waiting = mutableListOf<QueueEntry>()

    val waitingList: List<QueueEntry> get() = waiting.toList()
    val isIdle: Boolean get() = current == null && waiting.isEmpty()

    /** Append a song. When nothing is playing it becomes the current
     *  song at once (the caller starts playback); otherwise it waits. */
    @Synchronized
    fun add(song: Song, orderedBy: String): AddResult {
        val duplicate = waiting.any { it.song.id == song.id } || current?.song?.id == song.id
        val entry = QueueEntry(song, orderedBy)
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

    @Synchronized
    fun clearWaiting() = waiting.clear()

    /** "前面还有 X 首" for a waiting position: the current song plus
     *  the waiting songs ahead of [position]. */
    fun aheadCountFor(position: Int): Int = (if (current != null) 1 else 0) + position
}
