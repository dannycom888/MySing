package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.42: the corner QR card's address state machine. The bug
 * this pins against: the address used to be read inside
 * `remember(state.version)`, so it only refreshed when the queue
 * state bumped — during long random playback the card, once lost
 * to a late/dropped WiFi, never came back, and a DHCP change left
 * it printing a stale address. The tracker must always adopt the
 * latest fetch, report change honestly (an unchanged fetch must
 * not cost a recomposition / new QR bitmap), and drive the
 * waiting-for-network placeholder exactly while there is no
 * address.
 */
class LanIpTrackerTest {

    @Test
    fun pollCadenceIsTheDeliberateFiveSeconds() {
        assertEquals(5_000L, LanIpPoll.INTERVAL_MS)
    }

    @Test
    fun startsWithoutAddress_showsPlaceholder() {
        val tracker = LanIpTracker(null)
        assertNull(tracker.current)
        assertTrue(tracker.showsPlaceholder)
    }

    @Test
    fun startsWithAddress_showsQr() {
        val tracker = LanIpTracker("192.168.1.5")
        assertEquals("192.168.1.5", tracker.current)
        assertFalse(tracker.showsPlaceholder)
    }

    @Test
    fun networkArrivingAfterLaunch_bringsTheQrBack() {
        val tracker = LanIpTracker(QrAddressFixtures.NONE)
        val changed = tracker.onFetched("192.168.1.5")
        assertTrue(changed)
        assertEquals("192.168.1.5", tracker.current)
        assertFalse(tracker.showsPlaceholder)
    }

    @Test
    fun unchangedFetch_reportsNoChange() {
        val tracker = LanIpTracker("192.168.1.5")
        assertFalse(tracker.onFetched("192.168.1.5"))
        assertEquals("192.168.1.5", tracker.current)
    }

    @Test
    fun stillNoNetwork_reportsNoChange() {
        val tracker = LanIpTracker(null)
        assertFalse(tracker.onFetched(null))
        assertTrue(tracker.showsPlaceholder)
    }

    @Test
    fun changedAddress_replacesTheStaleOne() {
        val tracker = LanIpTracker("192.168.1.5")
        assertTrue(tracker.onFetched("192.168.1.9"))
        assertEquals("192.168.1.9", tracker.current)
        assertFalse(tracker.showsPlaceholder)
    }

    @Test
    fun networkDropping_fallsBackToPlaceholder() {
        val tracker = LanIpTracker("192.168.1.5")
        assertTrue(tracker.onFetched(null))
        assertNull(tracker.current)
        assertTrue(tracker.showsPlaceholder)
    }

    @Test
    fun cornerCardsShareTheModeratelyEnlargedCodeSize() {
        // v1.0.65 (the user's word: a moderate grow, not
        // double — too big spoils the picture): the code is
        // 82dp (compact 60dp), up from 56/44dp, and the QR
        // card and its waiting-for-network placeholder carry
        // the EXACT same size line, so the corner never
        // changes shape when the network comes and goes.
        val src = java.io.File(
            "src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt",
        ).readText()
        assertEquals(
            2,
            Regex("""val qrDp = if \(compact\) 60\.dp else 82\.dp""")
                .findAll(src).count(),
        )
        assertTrue(!src.contains("if (compact) 44.dp else 56.dp"))
    }
}

/** Named null so the launch-without-network case reads clearly. */
private object QrAddressFixtures {
    val NONE: String? = null
}
