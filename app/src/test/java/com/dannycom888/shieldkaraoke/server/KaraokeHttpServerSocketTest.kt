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
        server = KaraokeHttpServer(state, port)
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

    private fun orderJson(id: String, name: String): String =
        """{"id":"$id","name":"$name"}"""

    private fun postOrder(id: String, name: String): Response {
        val body = orderJson(id, name).toByteArray(Charsets.UTF_8)
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
