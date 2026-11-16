package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.i18n.ServerKey
import com.dannycom888.shieldkaraoke.i18n.ServerStrings
import com.dannycom888.shieldkaraoke.i18n.UiLang
import com.dannycom888.shieldkaraoke.library.Gender
import com.dannycom888.shieldkaraoke.library.Language
import com.dannycom888.shieldkaraoke.state.KaraokeState
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * The phone-ordering web service: a tiny HTTP server on the LAN.
 * Phones on the same Wi-Fi scan the TV's QR code and land here —
 * no app to install, no internet involved. Search and the queue
 * order live here (phone only, per spec); playback control does not.
 */
class KaraokeHttpServer(
    private val state: KaraokeState,
    val port: Int = DEFAULT_PORT,
    private val favoritesProvider: () -> Set<String> = { emptySet() },
    private val recentsProvider: () -> List<String> = { emptyList() },
    private val favoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    private val dancerSelectionsProvider: () -> Map<String, String> = { emptyMap() },
    private val dancerSetter: (String, String) -> Unit = { _, _ -> },
    private val dancerImageProvider: (String) -> ByteArray? = { null },
    private val dancerAnimProvider: (String) -> ByteArray? = { null },
    /** Read-only view of the host's dancer renames (v1.0.21), so
     *  the served page can label its picker cards with them.
     *  Deliberately a provider and not a setter: renaming happens
     *  on the TV admin page only, this server has no rename route. */
    private val dancerNamesProvider: () -> Map<String, String> = { emptyMap() },
) {
    companion object {
        const val DEFAULT_PORT = 8931

        /** Bind attempts before giving up (v1.0.8): an app restart
         *  or upgrade can leave the port briefly held by the dying
         *  previous process, and one failed bind used to kill the
         *  phone service for the whole session — silently. */
        const val MAX_BIND_ATTEMPTS = 5
        const val BIND_RETRY_DELAY_MS = 500L

        /** Read timeout on accepted sockets (v1.0.9): a malformed
         *  or abandoned request must time out, not hang a handler
         *  thread for the rest of the session. */
        const val SOCKET_TIMEOUT_MS = 15_000
    }

    /** Identity of this server session, minted once per start of
     *  the app. Served pages carry it and /api/queue echoes it, so
     *  a page left over from a previous session detects the
     *  mismatch and reloads itself (see [BootSession]). */
    val bootId: String = BootSession.newId(System.currentTimeMillis(), Random.nextLong())

    /** True once the socket is bound and accepting. */
    @Volatile
    var alive: Boolean = false
        private set

    /** Why the server is not alive, when it isn't (bind failure
     *  after all retries, or the accept loop dying). Surfaced on
     *  the TV home screen — never a silent death again. */
    @Volatile
    var lastError: String? = null
        private set

    /** Notified on every alive/error transition so the app layer
     *  can mirror the status into UI state. */
    @Volatile
    var statusListener: ((alive: Boolean, error: String?) -> Unit)? = null

    private fun publishStatus(isAlive: Boolean, error: String?) {
        alive = isAlive
        lastError = error
        try {
            statusListener?.invoke(isAlive, error)
        } catch (e: Exception) { /* a listener must never kill the server */ }
    }

    @Volatile
    private var running = false
    private var socket: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        thread(name = "karaoke-http", isDaemon = true) {
            var server: ServerSocket? = null
            var attempt = 0
            while (server == null && running && attempt < MAX_BIND_ATTEMPTS) {
                attempt++
                try {
                    val s = ServerSocket()
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(port))
                    server = s
                } catch (e: Exception) {
                    publishStatus(false, "端口 $port 被占用：${e.message ?: e.javaClass.simpleName}")
                    if (attempt < MAX_BIND_ATTEMPTS) {
                        try { Thread.sleep(BIND_RETRY_DELAY_MS) } catch (ie: InterruptedException) { break }
                    }
                }
            }
            if (server == null) {
                // All retries spent: stay visibly down (lastError is
                // already published) instead of dying in silence.
                return@thread
            }
            socket = server
            publishStatus(true, null)
            try {
                while (running) {
                    val client = server.accept()
                    thread(isDaemon = true) { handle(client) }
                }
            } catch (e: Exception) {
                if (running) {
                    publishStatus(false, "点歌服务中断：${e.message ?: e.javaClass.simpleName}")
                }
            } finally {
                if (!running) publishStatus(false, lastError)
            }
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        publishStatus(false, lastError)
    }

    /** One request/header line read byte-wise: bytes up to '\n'
     *  with a trailing '\r' stripped. Header bytes are ASCII in
     *  practice; ISO-8859-1 keeps the mapping byte-exact so nothing
     *  is mangled before the path/query are URL-decoded. Null on a
     *  clean EOF before any byte. */
    private fun readHeaderLine(input: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else decodeHeaderLine(buf)
            if (b == '\n'.code) break
            buf.write(b)
        }
        return decodeHeaderLine(buf)
    }

    private fun decodeHeaderLine(buf: ByteArrayOutputStream): String {
        var bytes = buf.toByteArray()
        if (bytes.isNotEmpty() && bytes[bytes.size - 1] == '\r'.code.toByte()) {
            bytes = bytes.copyOf(bytes.size - 1)
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun handle(client: java.net.Socket) {
        client.use { sock ->
            // A wedged or half-sent request must never park a
            // handler thread forever (v1.0.9).
            try { sock.soTimeout = SOCKET_TIMEOUT_MS } catch (e: Exception) { /* best effort */ }
            // Byte-level reading only (v1.0.9 root-cause fix):
            // Content-Length counts BYTES on the wire, but the old
            // code spent that budget on UTF-8 CHARS — a body with
            // multi-byte text (Chinese song ids / orderer names)
            // could never fill the char buffer, so the handler
            // blocked mid-request and the phone heard nothing back.
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readHeaderLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            var contentLength = 0
            while (true) {
                val line = readHeaderLine(input) ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = input.read(buf, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                String(buf, 0, read, Charsets.UTF_8)
            } else ""

            val path = rawPath.substringBefore('?')
            val query = parseQuery(rawPath.substringAfter('?', ""))

            // Dancer artwork is binary; it bypasses the text
            // route table. Two flavours share this bypass: the
            // static portraits (PNG, /dancer-img/) and the looping
            // picker animations (WebP, /dancer-anim/, v1.0.11).
            // Key validation is DancerImages'/DancerAnims'
            // (unknown keys 404), bytes come from the app's
            // decode-once / read-once caches.
            if (method == "GET") {
                val imageKey = DancerImages.keyForPath(path)
                val animKey = if (imageKey == null) DancerAnims.keyForPath(path) else null
                if (imageKey != null || animKey != null) {
                    val bytes = if (imageKey != null) {
                        dancerImageProvider(imageKey)
                    } else {
                        dancerAnimProvider(animKey!!)
                    }
                    val contentType = if (imageKey != null) "image/png" else "image/webp"
                    val out = sock.getOutputStream()
                    if (bytes != null) {
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(bytes)
                    } else {
                        val msg = "not found".toByteArray(Charsets.UTF_8)
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${msg.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(msg)
                    }
                    out.flush()
                    return
                }
            }

            val (status, contentType, response) = route(method, path, query, body)
            val out = sock.getOutputStream()
            val bytes = response.toByteArray(Charsets.UTF_8)
            val statusText = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "Error" }
            // The ordering page must never come from a phone's HTTP
            // cache: a cached copy is exactly the stale page this
            // release defends against (old boot id, old script).
            val cacheHeader = if (method == "GET" && (path == "/" || path == "/index.html")) {
                "Cache-Control: no-store\r\n"
            } else {
                ""
            }
            out.write("HTTP/1.1 $status $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n${cacheHeader}Connection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }
    }

    private fun parseQuery(qs: String): Map<String, String> =
        qs.split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    private fun route(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String,
    ): Triple<Int, String, String> {
        return when {
            method == "GET" && (path == "/" || path == "/index.html") ->
                Triple(200, "text/html; charset=utf-8", PhonePage.html(bootId, dancerNamesProvider()))

            method == "GET" && path == "/api/songs" -> {
                val lang = when (query["lang"]) {
                    "zh" -> Language.CHINESE; "en" -> Language.ENGLISH; else -> null
                }
                val gender = when (query["gender"]) {
                    "m" -> Gender.MALE; "f" -> Gender.FEMALE; else -> null
                }
                val q = query["q"] ?: ""
                var list = state.catalog.filtered(lang, gender)
                if (q.isNotBlank()) list = list.filter {
                    com.dannycom888.shieldkaraoke.library.PinyinInitials.matches(it, q)
                }
                val arr = JSONArray()
                list.forEach { s ->
                    arr.put(JSONObject().apply {
                        put("id", s.id); put("artist", s.artist); put("title", s.title)
                        put("lang", if (s.category.language == Language.CHINESE) "zh" else "en")
                        put("gender", if (s.category.gender == Gender.MALE) "m" else "f")
                        // v1.0.18: the pinyin-initials key rides along
                        // so the page can run its search suggestions
                        // locally (the whole catalog is fetched once).
                        // initialsOf is a per-char map, so this one
                        // combined string covers every contains()
                        // check PinyinInitials.matches does server-side.
                        put("pin", com.dannycom888.shieldkaraoke.library.PinyinInitials.initialsOf(s.artist + s.title))
                        // v1.0.47: the page badges songs that have
                        // a paired accompaniment file on disk. The
                        // file itself is never a catalog entry, so
                        // it can never appear in this list.
                        put("acc", s.hasAccompanimentFile)
                    })
                }
                Triple(200, "application/json; charset=utf-8", JSONObject().put("songs", arr).toString())
            }

            method == "GET" && path == "/api/queue" -> {
                val obj = JSONObject()
                val cur = state.queue.current
                obj.put("current", if (cur == null) JSONObject.NULL else JSONObject().apply {
                    put("artist", cur.song.artist); put("title", cur.song.title); put("by", cur.orderedBy)
                })
                val arr = JSONArray()
                state.queue.waitingList.forEach { e ->
                    arr.put(JSONObject().apply {
                        put("artist", e.song.artist); put("title", e.song.title); put("by", e.orderedBy)
                    })
                }
                obj.put("waiting", arr)
                obj.put("boot", bootId)
                obj.put("nextUp", state.queue.nextUpText() ?: "")
                obj.put("favorites", JSONArray(favoritesProvider().toList()))
                obj.put("recents", JSONArray(recentsProvider()))
                obj.put("dancers", JSONObject(dancerSelectionsProvider()))
                Triple(200, "application/json; charset=utf-8", obj.toString())
            }

            method == "POST" && path == "/api/order" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val name = json.optString("name").trim()
                val id = json.optString("id")
                // The page sends its own UI language (v1.0.13) and
                // the reply follows it; no lang (an older page)
                // keeps the original Chinese wording.
                val ui = UiLang.parse(json.optString("lang"))
                if (name.isBlank()) {
                    return Triple(400, "application/json; charset=utf-8",
                        JSONObject().put("error", ServerStrings.get(ui, ServerKey.ORDER_NAME_REQUIRED)).toString())
                }
                val song = state.catalog.byId(id)
                    ?: return Triple(404, "application/json; charset=utf-8",
                        JSONObject().put("error", ServerStrings.get(ui, ServerKey.ORDER_NOT_FOUND)).toString())
                val result = state.order(song, name)
                val ahead = if (result.position < 0) 0 else state.queue.aheadCountFor(result.position)
                val msg = if (result.position < 0) {
                    ServerStrings.get(ui, ServerKey.ORDER_START_NOW)
                } else {
                    ServerStrings.get(ui, ServerKey.ORDER_QUEUED, ahead)
                }
                Triple(200, "application/json; charset=utf-8", JSONObject().apply {
                    put("message", msg); put("duplicate", result.wasDuplicate); put("ahead", ahead)
                }.toString())
            }

            method == "POST" && path == "/api/remove" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                state.removeWaitingAt(json.optInt("index", -1))
                Triple(200, "application/json; charset=utf-8", JSONObject().put("ok", true).toString())
            }

            // Per-orderer dancer choice: whoever ordered the song
            // picks the dancer that comes out for their song. Any
            // connected phone may set its own name's dancer.
            method == "POST" && path == "/api/dancer" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val name = json.optString("name").trim()
                val who = json.optString("who")
                val ui = UiLang.parse(json.optString("lang"))
                if (name.isBlank() || !com.dannycom888.shieldkaraoke.state.DancerKeys.isValid(who)) {
                    Triple(400, "application/json; charset=utf-8",
                        JSONObject().put("error", ServerStrings.get(ui, ServerKey.DANCER_INVALID)).toString())
                } else {
                    dancerSetter(name, who)
                    Triple(200, "application/json; charset=utf-8",
                        JSONObject().put("ok", true).put("who", who).toString())
                }
            }

            method == "POST" && path == "/dancer" -> {
                val name = (query["name"] ?: "").trim()
                val who = query["who"] ?: ""
                val ui = UiLang.parse(query["lang"])
                if (name.isBlank() || !com.dannycom888.shieldkaraoke.state.DancerKeys.isValid(who)) {
                    Triple(400, "application/json; charset=utf-8",
                        JSONObject().put("error", ServerStrings.get(ui, ServerKey.DANCER_INVALID)).toString())
                } else {
                    dancerSetter(name, who)
                    Triple(200, "application/json; charset=utf-8",
                        JSONObject().put("ok", true).put("who", who).toString())
                }
            }

            method == "POST" && path == "/api/fav" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                favoriteToggle(json.optString("id"), json.optBoolean("fav"))
                Triple(200, "application/json; charset=utf-8", JSONObject().put("ok", true).toString())
            }

            else -> Triple(404, "text/plain; charset=utf-8", "not found")
        }
    }
}
