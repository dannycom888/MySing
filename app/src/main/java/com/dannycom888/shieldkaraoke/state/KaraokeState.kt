package com.dannycom888.shieldkaraoke.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dannycom888.shieldkaraoke.library.Catalog
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.queue.AddResult
import com.dannycom888.shieldkaraoke.queue.PlayQueue
import com.dannycom888.shieldkaraoke.queue.QueueEntry

/**
 * Process-wide karaoke state, shared by the TV UI and the phone
 * ordering HTTP server. Playback itself is driven through callbacks
 * the Activity registers ([onShouldStart], [onQueueChanged]).
 */
class KaraokeState {

    @Volatile
    var catalog: Catalog = Catalog(emptyList())
        private set

    val queue = PlayQueue()

    /** Bumped on every queue/catalog change so Compose re-reads. */
    var version by mutableStateOf(0)
        private set

    /** The song the player is currently loaded with (mirror of queue.current). */
    var nowPlaying by mutableStateOf<QueueEntry?>(null)

    /** Seconds left in the applause interlude between songs, 0 = none. */
    var interludeSeconds by mutableStateOf(0)

    var onShouldStart: ((QueueEntry) -> Unit)? = null
    var onQueueChanged: (() -> Unit)? = null

    fun setCatalog(newCatalog: Catalog) {
        catalog = newCatalog
        bump()
    }

    /** Order a song (TV tap or phone). Never interrupts the current song. */
    @Synchronized
    fun order(song: Song, orderedBy: String): AddResult {
        val wasIdle = queue.current == null
        val result = queue.add(song, orderedBy)
        if (wasIdle && queue.current != null) {
            onShouldStart?.invoke(queue.current!!)
        }
        bump()
        onQueueChanged?.invoke()
        return result
    }

    @Synchronized
    fun removeWaitingAt(index: Int) {
        queue.removeWaitingAt(index)
        bump()
        onQueueChanged?.invoke()
    }

    @Synchronized
    fun clearWaiting() {
        queue.clearWaiting()
        bump()
        onQueueChanged?.invoke()
    }

    /** Playback of the current song ended naturally. */
    @Synchronized
    fun advanceAfterFinish(): QueueEntry? = advance()

    /** Singer pressed 不唱了. */
    @Synchronized
    fun advanceAfterQuit(): QueueEntry? = advance()

    private fun advance(): QueueEntry? {
        val next = queue.finishCurrent()
        bump()
        onQueueChanged?.invoke()
        return next
    }

    fun bump() {
        version++
    }
}
