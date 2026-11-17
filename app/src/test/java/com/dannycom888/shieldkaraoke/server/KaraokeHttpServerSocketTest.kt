package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.library.Catalog
import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.state.KaraokeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Socket-level regression tests for the phone-ordering server —
 * the coverage hole behind v1.0.9: every earlier test stopped at
 * the route table, so nobody ever pushed a real request with a
 * multi-byte body through a real socket. The old handler spent
 * the Content-Length BYTE budget on UTF-8 CHARS, so a POST whose
 * JSON carried Chinese (song id / orderer name) blocked the
 * handler thread forever and the phone heard nothing back.
 *
 * These tests run the real server on a scratch port and talk to
 * it with raw sockets, byte for byte like a phone browser.
 */
class KaraokeHttpServerSocketTest {

    companion object {
        /** Stand-in animation payload: RIFF/WebP-flavoured bytes
         *  with non-UTF-8 values, so a byte-mangling transport
         *  cannot pass the round-trip check by accident. */
        private val FAKE_ANIM = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x10, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0xFF.toByte(), 0x80.toByte(), 0x00, 0x7F,
        )
    }

    private val chineseSong = Song(
        id = "CHINESE_BOY|张信哲 - 爱如潮水",
        artist = "张信哲",
        title = "爱如潮水",
        category = Category.CHINESE_BOY,
        audioRef = "/music chinese boy/张信哲 - 爱如潮水.mp3",
        lyricsRef = "/music chinese boy/张信哲 - 爱如潮水.lrc",
        // v1.0.47: this song has a paired accompaniment file on
        // disk; the English one below does not.
        accompanimentRef = "/music chinese boy/张信哲 - 爱如潮水（伴奏）.mp3",
    )
    private val englishSong = Song(
        id = "ENGLISH_GIRL|adele - hello",
        artist = "Adele",
        title = "Hello",
        category = Category.ENGLISH_GIRL,
        audioRef = "/music english girl/Adele - Hello.mp3",
        lyricsRef = null,
    )

    private lateinit var state: KaraokeState
    private lateinit var server: KaraokeHttpServer
    private var port: Int = 0

    @Before
    fun setUp() {
        state = KaraokeState()
        state.setCatalog(Catalog(listOf(chineseSong, englishSong)))
        port = freePort()
        server = KaraokeHttpServer(
            state, port,
            dancerAnimProvider = { key -> if (key == "modern") FAKE_ANIM else null },
        )
        server.start()
        awaitAccepting(port)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun chineseOrderOverRealSocketReturns200AndQueues() {
        val response = postOrder(chineseSong.id, "小明")
        assertTrue("expected 200, got: ${response.statusLine}", response.status == 200)
        val entry = allEntries().firstOrNull { it.song.id == chineseSong.id }
        assertTrue("ordered song must be in the queue", entry != null)
        assertEquals("小明", entry!!.orderedBy)
    }

    @Test
    fun headersAndBodyInSeparateFlushesStillOrder() {
        val body = orderJson(chineseSong.id, "阿花").toByteArray(Charsets.UTF_8)
        val head = (
            "POST /api/order HTTP/1.1\r\n" +
                "Host: ::1:$port\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "\r\n"
            ).toByteArray(Charsets.US_ASCII)
        val response = rawExchange { out ->
            out.write(head); out.flush()
            Thread.sleep(250)
            out.write(body); out.flush()
        }
        assertTrue("expected 200, got: ${response.statusLine}", response.status == 200)
        assertTrue(allEntries().any { it.song.id == chineseSong.id && it.orderedBy == "阿花" })
    }

    @Test
    fun englishOrderOverRealSocketStillWorks() {
        val response = postOrder(englishSong.id, "chen")
        assertTrue("expected 200, got: ${response.statusLine}", response.status == 200)
        assertTrue(allEntries().any { it.song.id == englishSong.id && it.orderedBy == "chen" })
    }

    @Test
    fun orderReplyIsChineseByDefault() {
        // v1.0.13: no lang in the request (a page from before this
        // release) keeps the original Chinese reply, word for word.
        val response = postOrder(chineseSong.id, "小明")
        assertEquals(200, response.status)
        assertEquals("马上开唱！", org.json.JSONObject(response.body).getString("message"))
    }

    @Test
    fun orderReplyFollowsEnglishLang() {
        val response = postOrder(englishSong.id, "William", lang = "en")
        assertEquals(200, response.status)
        assertEquals("You're up — singing now!", org.json.JSONObject(response.body).getString("message"))
    }

    @Test
    fun queuedReplyCountsAheadInBothLanguages() {
        // Occupy the current slot first, then the second order is a
        // queued one and the reply must count the songs ahead in
        // whichever language the ordering phone asked for.
        postOrder(chineseSong.id, "小明")
        val zh = postOrder(englishSong.id, "阿花", lang = "zh")
        assertEquals("已加入，前面还有 1 首", org.json.JSONObject(zh.body).getString("message"))
        val en = postOrder(chineseSong.id, "William", lang = "en")
        assertEquals("Added — 2 song(s) ahead of you", org.json.JSONObject(en.body).getString("message"))
    }

    @Test
    fun orderErrorsFollowLang() {
        val unknownEn = postOrder("NOPE|missing", "William", lang = "en")
        assertEquals(404, unknownEn.status)
        assertEquals("Song not found", org.json.JSONObject(unknownEn.body).getString("error"))
        val unknownZh = postOrder("NOPE|missing", "小明")
        assertEquals(404, unknownZh.status)
        assertEquals("找不到这首歌", org.json.JSONObject(unknownZh.body).getString("error"))
    }

    @Test
    fun dancerAnimRouteServesWebpBytes() {
        // v1.0.11: the picker cards are animated WebP loops. The
        // transport contract is what matters here — 200, the webp
        // content type, and the provider's bytes delivered intact
        // (non-UTF-8 bytes included; fake payload on purpose).
        val response = rawGet("/dancer-anim/modern")
        assertEquals(200, response.status)
        assertEquals("image/webp", response.contentType)
        assertTrue(FAKE_ANIM.contentEquals(response.bodyBytes))
    }

    @Test
    fun dancerAnimUnknownKeyIs404() {
        val response = rawGet("/dancer-anim/nobody")
        assertEquals(404, response.status)
    }

    @Test
    fun dancerAnimValidKeyWithoutAssetIs404() {
        // A real dancer whose animation bytes the provider does
        // not have must 404 (the page then falls back to the
        // static portrait) — never a 200 with an empty body.
        val response = rawGet("/dancer-anim/robot")
        assertEquals(404, response.status)
    }

    @Test
    fun songsEndpointCarriesPinyinKeys() {
        // v1.0.18: the page's local suggestion filter needs each
        // song's pinyin-initials key in the catalog payload. The
        // values are pinned against the real char table — 张信哲
        // 爱如潮水 must come out as zxz + arcs, not whatever the
        // client hopes for.
        val response = rawGet("/api/songs")
        assertEquals(200, response.status)
        val songs = org.json.JSONObject(String(response.bodyBytes, Charsets.UTF_8))
            .getJSONArray("songs")
        val byId = (0 until songs.length()).associate {
            val o = songs.getJSONObject(it); o.getString("id") to o
        }
        assertEquals("zxzarcs", byId.getValue(chineseSong.id).getString("pin"))
        assertEquals("adelehello", byId.getValue(englishSong.id).getString("pin"))
    }

    @Test
    fun songsEndpointCarriesAccompanimentFlag() {
        // v1.0.47: the phone page badges songs with a paired
        // accompaniment file. The flag rides /api/songs; the
        // accompaniment file itself is not a catalog entry, so
        // the list still holds exactly the two originals.
        val response = rawGet("/api/songs")
        assertEquals(200, response.status)
        val songs = org.json.JSONObject(String(response.bodyBytes, Charsets.UTF_8))
            .getJSONArray("songs")
        assertEquals(2, songs.length())
        val byId = (0 until songs.length()).associate {
            val o = songs.getJSONObject(it); o.getString("id") to o
        }
        assertTrue(byId.getValue(chineseSong.id).getBoolean("acc"))
        assertFalse(byId.getValue(englishSong.id).getBoolean("acc"))
    }

    @Test
    fun suggestionPickFlowLandsInQueue() {
        // The v1.0.18 phone flow end to end, exactly as the page
        // does it: fetch the catalog once, take the song a first
        // character (爱) would surface, order it by its id, and
        // find it in the queue under the guest's Chinese name.
        val catalog = rawGet("/api/songs")
        assertEquals(200, catalog.status)
        val songs = org.json.JSONObject(String(catalog.bodyBytes, Charsets.UTF_8))
            .getJSONArray("songs")
        val picked = (0 until songs.length())
            .map { songs.getJSONObject(it) }
            .first { it.getString("title").startsWith("爱") }
        assertEquals(chineseSong.id, picked.getString("id"))
        val response = postOrder(picked.getString("id"), "小明")
        assertEquals(200, response.status)
        assertEquals("马上开唱！", org.json.JSONObject(response.body).getString("message"))
        val entry = allEntries().firstOrNull { it.song.id == chineseSong.id }
        assertTrue("suggestion pick must land in the queue", entry != null)
        assertEquals("小明", entry!!.orderedBy)
    }

    @Test
    fun dancerIsBoundToTheSongAtOrderTime() {
        // v1.0.52, the user's rule end to end over a real socket:
        // 选好一首歌和舞伴后，这首歌的舞伴就不要再改变. A dedicated
        // server wired to a real (in-memory) last-used-pick store:
        //  - an order with no `who` snapshots the stored pick;
        //  - changing the pick afterwards leaves the queued entry
        //    untouched (the old mid-song swap is gone);
        //  - the next order snapshots the new pick;
        //  - an order carrying `who` snapshots that, and refreshes
        //    the stored last-used pick;
        //  - an unusable `who` falls back to the stored pick.
        val state2 = KaraokeState()
        state2.setCatalog(Catalog(listOf(chineseSong, englishSong)))
        val store = mutableMapOf<String, String>()
        val p2 = freePort()
        val srv2 = KaraokeHttpServer(
            state2, p2,
            dancerSelectionsProvider = { store.toMap() },
            dancerSetter = { n, k -> store[n] = k },
        )
        srv2.start()
        try {
            awaitAccepting(p2)
            fun post(path: String, json: String): Int {
                val body = json.toByteArray(Charsets.UTF_8)
                val head = (
                    "POST $path HTTP/1.1\r\n" +
                        "Host: ::1:$p2\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
                Socket().use { sock ->
                    sock.connect(InetSocketAddress("::1", p2), 3_000)
                    sock.soTimeout = 5_000
                    val out = sock.getOutputStream()
                    out.write(head); out.write(body); out.flush()
                    val all = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    val input = sock.getInputStream()
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        all.write(buf, 0, r)
                    }
                    val text = String(all.toByteArray(), Charsets.UTF_8)
                    return text.lineSequence().firstOrNull()
                        ?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: -1
                }
            }
            fun entries() = listOfNotNull(state2.queue.current) + state2.queue.waitingList

            // Pick the sumo, then order without `who` (an older page):
            // the stored pick is what gets frozen into the entry.
            assertEquals(200, post("/api/dancer", """{"name":"小明","who":"sumo"}"""))
            assertEquals(200, post("/api/order", """{"id":"${chineseSong.id}","name":"小明"}"""))
            assertEquals("sumo", entries().first { it.song.id == chineseSong.id }.dancerKey)

            // Change the pick mid-queue: the entry must NOT follow.
            assertEquals(200, post("/api/dancer", """{"name":"小明","who":"robot"}"""))
            assertEquals("sumo", entries().first { it.song.id == chineseSong.id }.dancerKey)

            // The next order snapshots the new pick.
            assertEquals(200, post("/api/order", """{"id":"${englishSong.id}","name":"小明"}"""))
            assertEquals("robot", entries().first { it.song.id == englishSong.id }.dancerKey)

            // An order carrying `who` snapshots it and refreshes the
            // stored last-used pick for that name.
            assertEquals(200, post("/api/order", """{"id":"${chineseSong.id}","name":"阿花","who":"baby"}"""))
            val hua = entries().filter { it.orderedBy == "阿花" }
            assertEquals(1, hua.size)
            assertEquals("baby", hua.first().dancerKey)
            assertEquals("baby", store["阿花"])

            // An unusable `who` falls back to the stored pick.
            assertEquals(200, post("/api/order", """{"id":"${englishSong.id}","name":"阿花","who":"nobody"}"""))
            assertEquals(
                "baby",
                entries().filter { it.orderedBy == "阿花" }.last().dancerKey,
            )
        } finally {
            srv2.stop()
        }
    }

    @Test
    fun waitingDancerCanBeChangedFromThePhoneUntilItStarts() {
        // v1.0.56, the loosened rule over a real socket with real
        // Chinese names: 开唱之前都可以换，开唱后锁定.
        //  - /api/queue lists every entry's bound dancer;
        //  - the orderer re-picks their waiting song's dancer and
        //    the entry (and only it) follows;
        //  - another name is refused with its own readable reason;
        //  - once the song advances to current (the ceremony-lock
        //    moment) the same request is refused as started.
        assertEquals(200, postOrder(chineseSong.id, "小明").status)
        val ordered = postJson(
            "/api/order",
            """{"id":"${englishSong.id}","name":"小明","who":"sumo"}""",
        )
        assertEquals(200, ordered.status)
        assertEquals("sumo", state.queue.waitingList.first().dancerKey)

        // The queue payload carries the binding for the page's
        // （伴舞：××） tag, plus the song id that addresses the row.
        val queueResponse = rawGet("/api/queue")
        assertEquals(200, queueResponse.status)
        val queueJson = org.json.JSONObject(String(queueResponse.bodyBytes, Charsets.UTF_8))
        val waiting0 = queueJson.getJSONArray("waiting").getJSONObject(0)
        assertEquals(englishSong.id, waiting0.getString("id"))
        assertEquals("sumo", waiting0.getString("dancer"))
        assertTrue(queueJson.getJSONObject("current").has("dancer"))

        // The orderer changes it while it waits.
        val changed = postJson(
            "/api/queue-dancer",
            """{"name":"小明","index":0,"id":"${englishSong.id}","who":"robot","lang":"zh"}""",
        )
        assertEquals(200, changed.status)
        assertEquals("伴舞换好了", org.json.JSONObject(changed.body).getString("message"))
        assertEquals("robot", state.queue.waitingList.first().dancerKey)

        // Somebody else's name is refused, in their language, and
        // the entry does not move.
        val notYours = postJson(
            "/api/queue-dancer",
            """{"name":"William","index":0,"id":"${englishSong.id}","who":"baby","lang":"en"}""",
        )
        assertEquals(403, notYours.status)
        assertEquals(
            "You can only change the dancer on your own songs",
            org.json.JSONObject(notYours.body).getString("error"),
        )
        assertEquals("robot", state.queue.waitingList.first().dancerKey)

        // An unusable dancer key is a plain 400, like /api/dancer.
        val invalid = postJson(
            "/api/queue-dancer",
            """{"name":"小明","index":0,"id":"${englishSong.id}","who":"nobody"}""",
        )
        assertEquals(400, invalid.status)

        // The song starts (advance == the ceremony locks it as
        // current): from here its dancer is frozen at the value
        // the change left behind.
        state.advanceAfterFinish()
        assertEquals(englishSong.id, state.queue.current?.song?.id)
        val locked = postJson(
            "/api/queue-dancer",
            """{"name":"小明","index":0,"id":"${englishSong.id}","who":"baby","lang":"zh"}""",
        )
        assertEquals(409, locked.status)
        assertEquals("已经开唱，不能换了", org.json.JSONObject(locked.body).getString("error"))
        assertEquals("robot", state.queue.current?.dancerKey)

        // And a song that is nowhere any more reads as gone.
        val gone = postJson(
            "/api/queue-dancer",
            """{"name":"小明","index":3,"id":"NOPE|missing","who":"baby","lang":"zh"}""",
        )
        assertEquals(404, gone.status)
        assertEquals("这首歌已经不在队列里了", org.json.JSONObject(gone.body).getString("error"))
    }

    @Test
    fun garbageRequestDoesNotKillServer() {
        rawExchange { out ->
            out.write("GARBAGE\r\n\r\n".toByteArray(Charsets.US_ASCII)); out.flush()
        }
        val response = postOrder(chineseSong.id, "小明")
        assertTrue("expected 200 after garbage, got: ${response.statusLine}", response.status == 200)
    }

    // --- helpers -------------------------------------------------

    @Test
    fun servedPageCarriesCustomDancerNames() {
        // v1.0.21: renames made on the TV admin page reach the
        // phone page through the server's read-only names
        // provider — a fresh page load shows the friend's name
        // on the picker card instead of the default stage name.
        val p2 = freePort()
        val srv2 = KaraokeHttpServer(
            state, p2,
            dancerNamesProvider = { mapOf("modern" to "小明", "sumo" to "大壮") },
        )
        srv2.start()
        try {
            awaitAccepting(p2)
            Socket().use { sock ->
                sock.connect(InetSocketAddress("::1", p2), 3_000)
                sock.soTimeout = 5_000
                val out = sock.getOutputStream()
                out.write("GET / HTTP/1.1\r\nHost: ::1:$p2\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                val all = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                val input = sock.getInputStream()
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    all.write(buf, 0, r)
                }
                val text = String(all.toByteArray(), Charsets.UTF_8)
                assertTrue("expected 200, got: ${text.lineSequence().firstOrNull()}", text.startsWith("HTTP/1.1 200"))
                assertTrue("custom name for modern missing", text.contains("\"modern\":\"小明\""))
                assertTrue("custom name for sumo missing", text.contains("\"sumo\":\"大壮\""))
            }
        } finally {
            srv2.stop()
        }
    }

    @Test
    fun noRenameEndpointOnTheServer() {
        // Renaming is TV-only (v1.0.21): the phone API must not
        // grow a write path for dancer names under any plausible
        // route name — this one stays 404 like any unknown path.
        val body = "{\"key\":\"modern\",\"name\":\"小明\"}".toByteArray(Charsets.UTF_8)
        val head = (
            "POST /api/rename HTTP/1.1\r\n" +
                "Host: ::1:$port\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "\r\n"
            ).toByteArray(Charsets.US_ASCII)
        val response = rawExchange { out ->
            out.write(head); out.write(body); out.flush()
        }
        assertEquals(404, response.status)
    }

    private fun allEntries() =
        listOfNotNull(state.queue.current) + state.queue.waitingList

    private data class RawResponse(val status: Int, val contentType: String?, val bodyBytes: ByteArray)

    /** One bare GET, response kept as raw bytes so binary routes
     *  (the dancer artwork) can be checked byte for byte. */
    private fun rawGet(path: String): RawResponse {
        Socket().use { sock ->
            sock.connect(InetSocketAddress("::1", port), 3_000)
            sock.soTimeout = 5_000
            val out = sock.getOutputStream()
            out.write("GET $path HTTP/1.1\r\nHost: ::1:$port\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            val all = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val input = sock.getInputStream()
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                all.write(buf, 0, r)
            }
            val bytes = all.toByteArray()
            val headEnd = indexOfHeaderEnd(bytes)
            val head = String(bytes, 0, headEnd, Charsets.ISO_8859_1)
            val status = head.lineSequence().firstOrNull()
                ?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: -1
            val contentType = head.lineSequence()
                .firstOrNull { it.lowercase().startsWith("content-type:") }
                ?.substringAfter(':')?.trim()
            val body = bytes.copyOfRange(headEnd + 4, bytes.size)
            return RawResponse(status, contentType, body)
        }
    }

    private fun indexOfHeaderEnd(bytes: ByteArray): Int {
        for (i in 0..bytes.size - 4) {
            if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte() &&
                bytes[i + 2] == '\r'.code.toByte() && bytes[i + 3] == '\n'.code.toByte()
            ) return i
        }
        return bytes.size
    }

    /** POST an arbitrary JSON body to [path] on the shared test
     *  server (the queue-dancer flow needs bodies postOrder's
     *  fixed order shape cannot express). */
    private fun postJson(path: String, json: String): Response {
        val body = json.toByteArray(Charsets.UTF_8)
        val head = (
            "POST $path HTTP/1.1\r\n" +
                "Host: ::1:$port\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            ).toByteArray(Charsets.US_ASCII)
        return rawExchange { out ->
            out.write(head)
            out.write(body)
            out.flush()
        }
    }

    private fun orderJson(id: String, name: String, lang: String? = null): String =
        if (lang == null) """{"id":"$id","name":"$name"}"""
        else """{"id":"$id","name":"$name","lang":"$lang"}"""

    private fun postOrder(id: String, name: String, lang: String? = null): Response {
        val body = orderJson(id, name, lang).toByteArray(Charsets.UTF_8)
        val head = (
            "POST /api/order HTTP/1.1\r\n" +
                "Host: ::1:$port\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            ).toByteArray(Charsets.US_ASCII)
        return rawExchange { out ->
            out.write(head)
            out.write(body)
            out.flush()
        }
    }

    private data class Response(val statusLine: String, val status: Int, val body: String)

    /** Sends whatever [write] writes, then reads until the server
     *  closes the connection. The client-side timeout turns a
     *  wedged handler (the v1.0.9 bug) into a test failure rather
     *  than a hung test run. */
    private fun rawExchange(write: (java.io.OutputStream) -> Unit): Response {
        Socket().use { sock ->
            sock.connect(InetSocketAddress("::1", port), 3_000)
            sock.soTimeout = 5_000
            write(sock.getOutputStream())
            val all = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val input = sock.getInputStream()
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                all.write(buf, 0, r)
            }
            val text = String(all.toByteArray(), Charsets.UTF_8)
            val statusLine = text.lineSequence().firstOrNull() ?: ""
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: -1
            val bodyText = text.substringAfter("\r\n\r\n", "")
            return Response(statusLine, status, bodyText)
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun awaitAccepting(port: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress("::1", port), 500) }
                return
            } catch (e: Exception) {
                Thread.sleep(50)
            }
        }
        fail("server did not start accepting on port $port")
    }
}
