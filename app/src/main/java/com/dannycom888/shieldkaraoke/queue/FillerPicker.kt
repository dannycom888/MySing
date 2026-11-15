package com.dannycom888.shieldkaraoke.queue

import com.dannycom888.shieldkaraoke.library.Song
import kotlin.random.Random

/**
 * Idle filler playback (v1.0.20, the user's ruling 2026-11-15,
 * replacing the old "empty queue → back to the home screen, never
 * auto-play" rule): when nobody has ordered anything, the TV does
 * not sit silent — it plays random songs from the whole library as
 * background. A real order cuts the filler off at once; a filler
 * song that ends naturally earns no applause.
 *
 * This file is the pure logic: which song to pick next, and the
 * policy decisions the Activity consults. Playback glue lives in
 * MainActivity; the filler entry itself never enters [PlayQueue].
 */
object FillerPolicy {

    /**
     * Auto-start a filler song only when the feature is on, the
     * library is there, the real queue is completely idle (no
     * current, nobody waiting — a non-empty real queue must never
     * be shadowed by filler) and nothing else is already playing.
     */
    fun shouldAutoStart(
        enabled: Boolean,
        catalogReady: Boolean,
        queueIdle: Boolean,
        somethingPlaying: Boolean,
    ): Boolean = enabled && catalogReady && queueIdle && !somethingPlaying

    /** Applause is for a singer who finished their song. A filler
     *  song ending naturally just hands over to the next one. */
    fun applauseOnNaturalEnd(source: EntrySource?): Boolean =
        source == EntrySource.ORDERED

    /** A real order takes the stage immediately only when what is
     *  playing is filler; an ordered song is never cut by another
     *  order (the queue's oldest rule). */
    fun yieldsToOrder(source: EntrySource?): Boolean =
        source == EntrySource.FILLER
}

/**
 * Random song picker with a no-repeat window: a song just played
 * cannot come back until at least [RECENT_WINDOW] other songs have
 * played (the window shrinks to catalog size − 1 for small
 * libraries, so a one-song library still plays its one song).
 * Picks from the scanned catalog index only — never reads tags.
 */
class FillerPicker(private val random: Random = Random.Default) {

    private val recent = ArrayDeque<String>()

    fun pick(songs: List<Song>): Song? {
        if (songs.isEmpty()) return null
        val window = minOf(RECENT_WINDOW, songs.size - 1).coerceAtLeast(0)
        while (recent.size > window) recent.removeFirst()
        val excluded = recent.toSet()
        val candidates = songs.filter { it.id !in excluded }
        // candidates can only be empty if the catalog shrank under
        // a stale history; fall back to the whole catalog then.
        val pool = if (candidates.isEmpty()) songs else candidates
        val picked = pool[random.nextInt(pool.size)]
        recent.addLast(picked.id)
        while (recent.size > window) recent.removeFirst()
        return picked
    }

    /** Forget the play history (e.g. after a library rescan). */
    fun reset() = recent.clear()

    companion object {
        const val RECENT_WINDOW = 8
    }
}
