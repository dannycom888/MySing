package com.dannycom888.shieldkaraoke.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the phone page's queue panel: the collapsible 已点歌单 at
 *  the top of the page, my-position badges and the delete buttons
 *  that moved into it from the old bottom list. */
class PhonePageTest {

    private val html = PhonePage.html()

    @Test
    fun hasCollapsibleQueuePanelAtTheTop() {
        assertTrue(html.contains("id=\"qpanel\""))
        assertTrue(html.contains("id=\"qhead\""))
        assertTrue(html.contains("id=\"qsummary\""))
        assertTrue(html.contains("id=\"qlist\""))
        assertTrue(html.contains("已点歌单（共"))
        // The panel sits above the search box, not below the songs.
        assertTrue(html.indexOf("id=\"qpanel\"") < html.indexOf("id=\"q\""))
    }

    @Test
    fun oldBottomQueueBlockIsGone() {
        assertFalse(html.contains("已点顺序"))
        assertFalse(html.contains("id=\"queue\""))
    }

    @Test
    fun recognisesMyOwnSongsAndPositions() {
        // Name comes from the same localStorage key as the name gate.
        assertTrue(html.contains("localStorage.getItem('sk_name')"))
        // Waiting rows of mine carry the position badge; the summary
        // names my next song's position or that I have none queued.
        assertTrue(html.contains("我点的 · 第 "))
        assertTrue(html.contains("我点的下一首在第 "))
        assertTrue(html.contains("我还没点歌"))
        assertTrue(html.contains("正在唱"))
    }

    @Test
    fun deleteButtonsStillRideAlong() {
        assertTrue(html.contains("/api/remove"))
        assertTrue(html.contains("className = 'del'"))
    }

    @Test
    fun queueDataComesFromTheExistingEndpoint() {
        assertTrue(html.contains("/api/queue"))
        assertTrue(html.contains("setInterval(loadQueue, 5000)"))
    }
}
