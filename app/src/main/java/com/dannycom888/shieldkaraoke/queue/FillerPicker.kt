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
 * Filler *session* tracking (v1.0.24 — the fix for the user's
 * "随机唱没画面，没歌词": a filler song was singing while the TV
 * showed no player screen and no lyrics).
 *
 * Root cause: the v1.0.20 code knew only individual filler starts,
 * never a session. Its screen rule was "move to the player screen
 * only from Home" on starts it considered entries, and "never
 * move" on rollovers — so a filler session that began while the TV
 * sat on Browse/Admin (the queue drained while the host was
 * browsing), or that simply continued after the host backed out to
 * Home mid-session, played invisibly forever: audio on, no
 * singing screen, no lyrics. Playback itself was never forked —
 * [com.dannycom888.shieldkaraoke.ui.MainActivity.startSong] loads
 * audio, LRC and dancer for filler and ordered entries alike; only
 * the screen navigation forked.
 *
 * The session model: the FIRST filler song of a session is an
 * entry edge — it brings the player screen forward from wherever
 * the TV is. Rollovers inside the session never touch the screen
 * (a host who backed out on purpose is not yanked back). The
 * session ends when an ordered song takes the stage, when filler
 * is switched off mid-session, or when a filler start fails and
 * the stage falls idle — the next filler start after any of those
 * is a fresh entry edge.
 */
class FillerSession {

    var active: Boolean = false
        private set

    /** A filler song is starting. Returns true when this start
     *  opens a new session — the entry edge, the one moment the
     *  player screen comes forward. */
    fun onFillerStarted(): Boolean {
        val edge = !active
        active = true
        return edge
    }

    /** An ordered song took the stage: the filler session is over. */
    fun onOrderedStarted() {
        active = false
    }

    /** Filler stopped (switch turned off, or no filler song could
     *  be started and the stage fell idle): session over. */
    fun onStopped() {
        active = false
    }
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
