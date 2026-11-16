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
        // v1.0.12: cards carry the dancers' stage names. Since
        // v1.0.13 the name span is language-switched (data-dancer),
        // initially rendered in Chinese like the rest of the page.
        for ((key, name) in listOf(
            "modern" to "春丽", "ancient" to "陈秀颖", "sumo" to "姿三四郎",
            "ancient_girl" to "汉娜", "baby" to "哪吒", "robot" to "瓦力",
        )) {
            assertTrue(html.contains("<span data-dancer=\"$key\">$name</span>"))
        }
        assertTrue(html.contains("class=\"dcard\""))
        assertTrue(html.contains(".dcard.on"))
        // The old text-only dancer chips are gone.
        assertFalse(html.contains("<button class=\"chip\" data-who="))
    }

    @Test
    fun pageHasPerPhoneLanguageSwitch() {
        // v1.0.13: the page carries its own 中文/EN switch; the
        // choice is this phone's alone (localStorage mysing_lang),
        // never the TV's setting and never another guest's phone.
        assertTrue(html.contains("id=\"langZh\""))
        assertTrue(html.contains("id=\"langEn\""))
        assertTrue(html.contains("localStorage.getItem('mysing_lang')"))
        assertTrue(html.contains("localStorage.setItem('mysing_lang', l)"))
        assertTrue(html.contains("function applyLang()"))
        // The switch sits outside both the name gate and the main
        // block, so it is reachable before a name is entered too.
        assertTrue(html.indexOf("id=\"langBar\"") < html.indexOf("id=\"nameGate\""))
    }

    @Test
    fun pageCarriesBothLanguagesOfText() {
        // The whole UI runs off the embedded dictionary: spot-check
        // one string per area in each language.
        for (s in listOf(
            // Chinese (defaults, rendered statically too)
            "先输入你的名字", "已点歌单（共 ", "我还没点歌", "选伴舞", "点歌",
            // English
            "Enter your name first", "Queue (", "You haven't ordered yet",
            "Pick your dancer", "Order", "No songs queued yet",
            "can't reach the TV", "The TV app restarted",
        )) {
            assertTrue("missing page string: $s", html.contains(s))
        }
        // Dancer cards in English use the shared stage-name mapping
        // (DancerKeys.LABELS_EN is the authority; pinned here).
        for (name in listOf("Chun-Li", "Chen Xiuying", "Sanshiro", "Hannah", "Nezha", "WALL-E")) {
            assertTrue("missing English dancer name: $name", html.contains(name))
        }
    }

    @Test
    fun searchSuggestionsLiveUnderTheSearchBox() {
        // v1.0.18: typing the first character already lists songs
        // whose title carries it, in a dedicated box directly under
        // the search input (before the filter chips), so a guest
        // taps one instead of typing the whole title.
        assertTrue(html.contains("id=\"sug\""))
        assertTrue(html.indexOf("id=\"q\"") < html.indexOf("id=\"sug\""))
        assertTrue(html.indexOf("id=\"sug\"") < html.indexOf("data-lang"))
        // The box scrolls internally instead of pushing the page
        // around, and its rows are big tap targets.
        assertTrue(html.contains("#sug{"))
        assertTrue(html.contains("overflow-y:auto"))
        assertTrue(html.contains(".sugrow{"))
        // Suggestions render as 歌名 — 歌手.
        assertTrue(html.contains("s.title + ' — ' + s.artist"))
    }

    @Test
    fun suggestionsAreComputedLocallyFromOneCatalogFetch() {
        // The whole catalog is fetched once from the existing
        // endpoint; every keystroke then filters locally (no
        // per-keystroke requests, no stale-response races).
        assertTrue(html.contains("fetch('/api/songs')"))
        assertTrue(html.contains("function loadCatalog()"))
        // The pure ranking function ships between markers so
        // tools/check_suggest.js can exercise the exact code.
        assertTrue(html.contains("// SUGGEST-BEGIN"))
        assertTrue(html.contains("// SUGGEST-END"))
        assertTrue(html.contains("function suggestRank(songs, q, lang, gender)"))
        assertTrue(html.contains("var SUG_LIMIT = 20"))
    }

    @Test
    fun tappingASuggestionOrdersItAndClearsTheSearch() {
        // A tap rides the existing order channel (same receipts,
        // same duplicate warning), then the search box and the
        // suggestion list clear for the next guest.
        assertTrue(html.contains("function pickSuggest(s)"))
        assertTrue(html.contains("order(s.id)"))
        assertTrue(html.contains("document.getElementById('q').value = '';"))
        assertTrue(html.contains("hideSuggest()"))
    }

    @Test
    fun suggestionOverflowHintIsBilingual() {
        assertTrue(html.contains("sugMore"))
        assertTrue(html.contains("还有更多结果，继续输入缩小范围"))
        assertTrue(html.contains("More matches — keep typing to narrow it down"))
    }

    @Test
    fun requestsCarryThePageLanguage() {
        // Order and dancer posts tell the server the page language
        // so its replies (马上开唱 / You're up…) match the phone.
        assertTrue(html.contains("JSON.stringify({id:id, name:name, lang:ui})"))
        assertTrue(html.contains("JSON.stringify({name:name, who:myDancer, lang:ui})"))
    }

    @Test
    fun customDancerNamesAreInjectedForThePickerCards() {
        // v1.0.21: the host's renames (made on the TV admin page)
        // ride into the page as CUSTOM_DANCER_NAMES and override
        // the baked stage names in both languages — applyLang
        // prefers the custom entry over the language dictionary,
        // and the card image's alt text follows the same name.
        val page = PhonePage.html("bootX", mapOf("modern" to "小明", "robot" to "阿铁"))
        assertTrue(page.contains("var CUSTOM_DANCER_NAMES = {\"modern\":\"小明\",\"robot\":\"阿铁\"};"))
        assertTrue(page.contains("CUSTOM_DANCER_NAMES[k] || t('dancers')[k]"))
        assertTrue(page.contains("im.alt = nm"))
    }

    @Test
    fun noCustomNamesInjectsAnEmptyMap() {
        assertTrue(html.contains("var CUSTOM_DANCER_NAMES = {};"))
    }

    @Test
    fun customNameWithQuoteCannotBreakTheScript() {
        val page = PhonePage.html("bootX", mapOf("baby" to "A\"B"))
        assertTrue(page.contains("\"baby\":\"A\\\"B\""))
    }
}
