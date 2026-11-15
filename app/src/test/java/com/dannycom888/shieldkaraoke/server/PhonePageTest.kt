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

    @Test
    fun pageCarriesBootIdAndReloadsWhenTvRestarts() {
        // v1.0.8 stale-page defence: the served page embeds the
        // server's boot id, the queue poll compares it against the
        // live one in every /api/queue response, and a mismatch
        // reloads the page instead of silently swallowing orders.
        val booted = PhonePage.html("boot-xyz789")
        assertTrue(booted.contains("var bootId = 'boot-xyz789'"))
        assertTrue(booted.contains("d.boot !== bootId"))
        assertTrue(booted.contains("电视已重启，正在刷新页面…"))
        assertTrue(booted.contains("location.reload()"))
    }

    @Test
    fun noFetchFailsSilently() {
        // Every request the page makes (songs, order, fav, remove,
        // dancer, queue poll) ends in the shared failure handler,
        // which puts a plain-language reason in the #msg area —
        // v1.0.7 and earlier swallowed network failures whole.
        assertTrue(html.contains("function netErr()"))
        assertTrue(html.contains("发送失败：连不上电视，请确认同一个 WiFi，或关掉页面重新扫码"))
        assertTrue(html.split(".catch(netErr)").size - 1 >= 6)
    }

    @Test
    fun dancerPickerShowsAnimatedCardsNotJustNames() {
        // Every dancer is an image card, so guests can recognise
        // who is who (text-only chips were the v1.0.6 state the
        // user could not identify people in). Since v1.0.11 the
        // cards are fed by the animation route — each little
        // dancer dances right in the picker — with an onerror
        // fallback to the v1.0.7 static portrait route, so a
        // failed animation never leaves an empty card.
        assertTrue(html.contains("class=\"dcards\""))
        for (key in listOf("modern", "ancient", "sumo", "ancient_girl", "baby", "robot")) {
            assertTrue(html.contains("src=\"/dancer-anim/$key\""))
            assertTrue(html.contains("this.src='/dancer-img/$key'"))
            // Selection still rides on data-who, so the existing
            // paint/select/submit script keeps working untouched.
            assertTrue(html.contains("data-who=\"$key\""))
        }
        // v1.0.12: the retired modern girl is off the page entirely.
        assertFalse(html.contains("data-who=\"girl\""))
        // v1.0.12: cards carry the dancers' stage names.
        for (name in listOf("春丽", "林语嫣", "姿三四郎", "小蛮", "哪吒", "瓦力")) {
            assertTrue(html.contains("<span>$name</span>"))
        }
        assertTrue(html.contains("class=\"dcard\""))
        assertTrue(html.contains(".dcard.on"))
        // The old text-only dancer chips are gone.
        assertFalse(html.contains("<button class=\"chip\" data-who="))
    }
}
