package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.library.Gender
import com.dannycom888.shieldkaraoke.library.Language
import com.dannycom888.shieldkaraoke.state.KaraokeState
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.concurrent.thread

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
) {
    companion object {
        const val DEFAULT_PORT = 8931
    }

    @Volatile
    private var running = false
    private var socket: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        thread(name = "karaoke-http", isDaemon = true) {
            try {
                val server = ServerSocket(port)
                socket = server
                while (running) {
                    val client = server.accept()
                    thread(isDaemon = true) { handle(client) }
                }
            } catch (e: Exception) {
                // Port busy or socket closed: the TV UI still works.
            }
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun handle(client: java.net.Socket) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                String(buf, 0, read)
            } else ""

            val path = rawPath.substringBefore('?')
            val query = parseQuery(rawPath.substringAfter('?', ""))
            val (status, contentType, response) = route(method, path, query, body)
            val out = sock.getOutputStream()
            val bytes = response.toByteArray(Charsets.UTF_8)
            val statusText = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "Error" }
            out.write("HTTP/1.1 $status $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
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
                Triple(200, "text/html; charset=utf-8", PhonePage.html())

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
                obj.put("nextUp", state.queue.nextUpText() ?: "")
                obj.put("favorites", JSONArray(favoritesProvider().toList()))
                obj.put("recents", JSONArray(recentsProvider()))
                Triple(200, "application/json; charset=utf-8", obj.toString())
            }

            method == "POST" && path == "/api/order" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val name = json.optString("name").trim()
                val id = json.optString("id")
                if (name.isBlank()) {
                    return Triple(400, "application/json; charset=utf-8",
                        JSONObject().put("error", "请先输入你的名字").toString())
                }
                val song = state.catalog.byId(id)
                    ?: return Triple(404, "application/json; charset=utf-8",
                        JSONObject().put("error", "找不到这首歌").toString())
                val result = state.order(song, name)
                val ahead = if (result.position < 0) 0 else state.queue.aheadCountFor(result.position)
                val msg = if (result.position < 0) "马上开唱！" else "已加入，前面还有 $ahead 首"
                Triple(200, "application/json; charset=utf-8", JSONObject().apply {
                    put("message", msg); put("duplicate", result.wasDuplicate); put("ahead", ahead)
                }.toString())
            }

            method == "POST" && path == "/api/remove" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                state.removeWaitingAt(json.optInt("index", -1))
                Triple(200, "application/json; charset=utf-8", JSONObject().put("ok", true).toString())
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
