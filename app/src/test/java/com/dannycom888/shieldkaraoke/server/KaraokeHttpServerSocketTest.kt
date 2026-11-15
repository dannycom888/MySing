package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.library.Catalog
import com.dannycom888.shieldkaraoke.library.Category
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.state.KaraokeState
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun garbageRequestDoesNotKillServer() {
        rawExchange { out ->
            out.write("GARBAGE\r\n\r\n".toByteArray(Charsets.US_ASCII)); out.flush()
        }
        val response = postOrder(chineseSong.id, "小明")
        assertTrue("expected 200 after garbage, got: ${response.statusLine}", response.status == 200)
    }

    // --- helpers -------------------------------------------------

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
