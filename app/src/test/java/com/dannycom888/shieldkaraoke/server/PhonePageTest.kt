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
            // v1.0.65: three card sets share the artwork — the
            // standing picker's data-who, the order-time box's
            // data-opick, and the queue rows' change box's
            // data-pick.
            assertTrue(html.contains("data-who=\"$key\""))
            assertTrue(html.contains("data-opick=\"$key\""))
            assertTrue(html.contains("data-pick=\"$key\""))
        }
        // v1.0.12: the retired modern girl is off the page entirely.
        assertFalse(html.contains("data-who=\"girl\""))
        // v1.0.12: cards carry the dancers' stage names. Since
        // v1.0.13 the name span is language-switched (data-dancer),
        // initially rendered in Chinese like the rest of the page.
        for ((key, name) in listOf(
            "modern" to "Amy", "ancient" to "秀颖", "sumo" to "姿三四郎",
            "ancient_girl" to "汉娜", "baby" to "哪吒", "robot" to "瓦力",
        )) {
            assertTrue(html.contains("<span data-dancer=\"$key\">$name</span>"))
        }
        assertTrue(html.contains("class=\"dcard\""))
        assertTrue(html.contains(".dcard.on"))
        // The old text-only dancer chips are gone.
        assertFalse(html.contains("<button class=\"chip\" data-who="))
        assertFalse(html.contains("<button class=\"chip\" data-opick="))
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
            "Pick a dancer", "Order", "No songs queued yet",
            "can't reach the TV", "The TV app restarted",
        )) {
            assertTrue("missing page string: $s", html.contains(s))
        }
        // Dancer cards in English use the shared stage-name mapping
        // (DancerKeys.LABELS_EN is the authority; pinned here).
        for (name in listOf("Amy", "Xiuying", "Sanshiro", "Hannah", "Nezha", "WALL-E")) {
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
    fun tappingASuggestionOpensTheDancerBoxAndClearsTheSearch() {
        // v1.0.65: a suggestion tap no longer orders directly —
        // it opens the dancer box (whose card tap places the
        // order through the same channel, same receipts, same
        // duplicate warning), and the search box and suggestion
        // list clear for the next guest either way.
        assertTrue(html.contains("function pickSuggest(s)"))
        assertTrue(html.contains("askDancerForOrder(s.id"))
        assertFalse(html.contains("order(s.id)"))
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
        // v1.0.52: the order also carries the pick showing right now
        // (who:myDancer) — the server freezes it into the song's
        // queue entry.
        assertTrue(html.contains("JSON.stringify({id:id, name:name, who:myDancer, lang:ui})"))
        assertTrue(html.contains("JSON.stringify({name:name, who:myDancer, lang:ui})"))
    }

    @Test
    fun dancerHintSaysWaitingSongsCanStillChangeDancer() {
        // v1.0.56 loosens the v1.0.52 copy: the pick still binds
        // to the song at order time, but a song that is still
        // waiting can be re-picked from the queue — the hint says
        // exactly that, in both languages, and the old "定了就不变"
        // wording is gone.
        assertTrue(html.contains("点歌时选的伴舞就配这首歌；开唱之前，点你排队里的歌可以换一位"))
        assertTrue(html.contains("before it starts, tap your song in the queue to change it"))
        assertFalse(html.contains("定了就不变"))
        assertFalse(html.contains("立刻换"))
        assertFalse(html.contains("updates right away"))
    }

    @Test
    fun queueRowsShowTheirBoundDancerAndMineOpenTheChangePicker() {
        // v1.0.56: every queue row is tagged （伴舞：××） from the
        // entry's dancer in /api/queue; tapping one of MY waiting
        // rows opens the change picker (its own cards, data-pick —
        // not the order box's data-opick), and the pick posts to
        // /api/queue-dancer addressed by row index + song id.
        assertTrue(html.contains("rowDancer: '（伴舞：{dancer}）'"))
        assertTrue(html.contains("rowDancer: '(Dancer: {dancer})'"))
        assertTrue(html.contains("fmt(t('rowDancer'), {dancer: dancerName(e.dancer || 'modern')}"))
        assertTrue(html.contains("id=\"dpick\""))
        assertTrue(html.contains("data-pick=\"modern\""))
        assertTrue(html.contains("data-pick=\"robot\""))
        assertTrue(html.contains("openDancerPicker(idx, entry)"))
        assertTrue(html.contains("fetch('/api/queue-dancer'"))
        assertTrue(html.contains("index:target.index, id:target.id, who:who, lang:ui"))
        // The delete button must not also open the picker.
        assertTrue(html.contains("ev.stopPropagation()"))
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
    fun songRowsBadgePairedAccompaniment() {
        // v1.0.47: a song whose /api/songs entry carries acc=true
        // gets the small badge next to its name, in the page's
        // own language — 伴奏 in Chinese, Acc. in English.
        assertTrue(html.contains("accBadge: '伴奏'"))
        assertTrue(html.contains("accBadge: 'Acc.'"))
        assertTrue(html.contains("s.acc ? ' <span class=\"badge\">'+t('accBadge')+'</span>' : ''"))
    }

    @Test
    fun customNameWithQuoteCannotBreakTheScript() {
        val page = PhonePage.html("bootX", mapOf("baby" to "A\"B"))
        assertTrue(page.contains("\"baby\":\"A\\\"B\""))
    }

    @Test
    fun mainBlockOrderIsQueueSearchDancerChipsSongs() {
        // v1.0.65 final order: the queue panel first, then the
        // SEARCH box, then the standing dancer picker, then
        // the filter chips, then the song list. (The search
        // moved up from below the dancers; the standing picker
        // itself stays — the user ruled it is the easy place
        // to change one's mind.)
        val queue = html.indexOf("id=\"qpanel\"")
        val search = html.indexOf("id=\"searchWrap\"")
        val dancer = html.indexOf("data-i=\"dancerTitle\"")
        val chips = html.indexOf("data-lang")
        val songs = html.indexOf("id=\"songs\"")
        assertTrue(queue >= 0 && search > queue)
        assertTrue(dancer > search)
        assertTrue(chips > dancer)
        assertTrue(songs > chips)
        // The input itself rides inside the wrap, and the
        // suggestions still open directly under it.
        assertTrue(html.indexOf("id=\"q\"") > search)
        assertTrue(html.indexOf("id=\"sug\"") > html.indexOf("id=\"q\""))
        assertTrue(html.indexOf("id=\"sug\"") < dancer)
        // The standing picker is present in the main block.
        assertTrue(html.contains("id=\"dancers\""))
    }

    @Test
    fun orderingPopsTheDancerBoxLikeTheTv() {
        // v1.0.65: tapping a song — from the list or from a
        // suggestion — opens the dancer box first; tapping a
        // card places the order with that dancer in one step.
        assertTrue(html.contains("id=\"opick\""))
        assertTrue(html.contains("id=\"opickSong\""))
        assertTrue(html.contains("function askDancerForOrder(id, label)"))
        assertTrue(html.contains("askDancerForOrder(s.id, s.artist + ' - ' + s.title)"))
        assertTrue(html.contains("askDancerForOrder(s.id, s.artist ? (s.artist + ' - ' + s.title) : s.title)"))
        // The box opens preselected with this orderer's
        // current dancer — the same ONE state (myDancer) the
        // standing picker shows; the two are entrances to it.
        assertTrue(html.contains("c.getAttribute('data-opick') === myDancer"))
        assertTrue(html.contains("function paintOrderPicker()"))
        assertTrue(html.contains("function paintDancers()"))
        // A card tap makes the pick the state, repaints the
        // standing picker to match, posts it to the existing
        // /api/dancer record, and only then places the order
        // (which carries who=myDancer).
        assertTrue(html.contains("myDancer = who; localStorage.setItem('sk_dancer', myDancer); paintDancers();"))
        assertTrue(html.contains("order(target.id)"))
        // …and the standing picker's own taps write the same
        // state the box will open on.
        assertTrue(html.contains("myDancer=c.getAttribute('data-who'); localStorage.setItem('sk_dancer', myDancer); paintDancers();"))
        // Cancel is a real way out that queues nothing.
        assertTrue(html.contains("id=\"opickCancel\""))
        assertTrue(html.contains("document.getElementById('opickCancel').onclick = closeOrderPicker;"))
        assertTrue(html.contains("function closeOrderPicker()"))
        // The change-dancer box for queue rows is a separate
        // overlay and keeps its own flow.
        assertTrue(html.contains("id=\"dpick\""))
        assertTrue(html.contains("fetch('/api/queue-dancer'"))
    }

    @Test
    fun searchBoxIsBigGlowingAndSpeaksPlainly() {
        // v1.0.65: the search frame breathes a slow gold glow
        // (2.4 s ease-in-out, halo swelling and fading — never
        // a strobe), the input is taller with bigger type and
        // a magnifier, and the placeholder says plainly what
        // to type, in the page's own language (data-iph keeps
        // it switching with the rest of the page).
        assertTrue(html.contains("id=\"searchWrap\""))
        assertTrue(html.contains("<span class=\"mag\">🔍</span>"))
        assertTrue(html.contains("@keyframes searchGlow"))
        assertTrue(html.contains("animation:searchGlow 2.4s ease-in-out infinite"))
        assertTrue(html.contains("box-shadow:0 0 18px 7px rgba(255,213,79,.70)"))
        assertTrue(html.contains("#q{flex:1;min-width:0;width:auto;border:none;background:transparent;font-size:19px;padding:13px 2px}"))
        assertTrue(html.contains("placeholder=\"输入歌手或歌名（或拼音首字母）\""))
        assertTrue(html.contains("searchPh: '输入歌手或歌名（或拼音首字母）'"))
        assertTrue(html.contains("searchPh: 'Enter singer or song title (or pinyin initials)'"))
        assertTrue(html.contains("data-iph=\"searchPh\""))
        // Reduced-motion guests get the steady glow instead.
        assertTrue(html.contains("prefers-reduced-motion:reduce"))
    }

    @Test
    fun chipsFormOneCombinedFilterRenderedFromTheCatalog() {
        // v1.0.59: one pure predicate applies language × gender
        // × mode (all / favorites / recent) × search text at
        // once, between markers so tools/check_filter.js can run
        // the exact shipped code against Chinese fixtures.
        assertTrue(html.contains("// FILTER-BEGIN"))
        assertTrue(html.contains("// FILTER-END"))
        assertTrue(html.contains("function filterSongs(songs, lang, gender, mode, favs, recents, q)"))
        assertTrue(html.contains("function songMatchesQ(s, q)"))
        // The list renders from the one catalog fetch — a chip
        // tap is local, instant, and lists EVERY match: the old
        // per-tap server round-trip and its 300-row cap are gone.
        assertTrue(html.contains("filterSongs(allSongs, lang, gender, mode, favs, recents,"))
        assertFalse(html.contains("slice(0,300)"))
        assertFalse(html.contains("/api/songs?lang="))
    }

    @Test
    fun chipHighlightsTellTheTruthAboutTheCombinedFilter() {
        // v1.0.59: every chip repaints from the current filter
        // state, favorites/recent included; those two are
        // toggles in the same filter, not separate pages; and
        // typing in the search box no longer silently resets
        // the mode behind the user's back.
        assertTrue(html.contains("function paintChips()"))
        assertTrue(html.contains("classList.toggle('on', mode === 'fav')"))
        assertTrue(html.contains("classList.toggle('on', mode === 'recent')"))
        assertTrue(html.contains("mode = (mode === 'fav') ? 'all' : 'fav'"))
        assertTrue(html.contains("mode = (mode === 'recent') ? 'all' : 'recent'"))
        assertFalse(html.contains("mode='all'; if(allSongs"))
        assertFalse(html.contains("mode='all'; loadSongs()"))
    }
}
