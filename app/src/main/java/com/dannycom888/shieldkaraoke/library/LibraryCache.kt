package com.dannycom888.shieldkaraoke.library

import android.content.Context
import java.io.File

/**
 * On-disk cache of the last successful library scan. Walking the
 * SAF tree of a ~1,000-song hard-disk library takes seconds, so
 * without this the singer/song lists sat empty on every cold start
 * until the walk finished. Now the app opens with the cached
 * catalog filled in at once, and the usual full scan still runs
 * right behind it and replaces the catalog wholesale (new/deleted
 * files take effect when the scan lands, exactly as before).
 *
 * Format: a header line (magic, version, owning tree URI) and one
 * tab-separated line per song; fields are backslash-escaped so
 * tabs/newlines in file names cannot break the framing. Decoding
 * is tolerant: a bad header, a version bump or a different tree
 * URI voids the whole cache, while individual malformed lines are
 * simply skipped — a damaged cache behaves like no cache.
 */
object LibraryCache {

    private const val MAGIC = "MSING-CATALOG-CACHE"
    private const val VERSION = "1"

    fun encode(treeUri: String, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append(MAGIC).append('\t').append(VERSION).append('\t').append(escape(treeUri)).append('\n')
        for (song in songs) {
            sb.append(escape(song.id)).append('\t')
                .append(escape(song.artist)).append('\t')
                .append(escape(song.title)).append('\t')
                .append(song.category.name).append('\t')
                .append(escape(song.audioRef)).append('\t')
                .append(escape(song.lyricsRef ?: ""))
                .append('\n')
        }
        return sb.toString()
    }

    /** The cached songs for [expectedTreeUri], or null when the
     *  text is not a usable cache for that tree. */
    fun decode(text: String, expectedTreeUri: String): List<Song>? {
        val lines = text.split('\n')
        if (lines.isEmpty()) return null
        val header = lines[0].split('\t')
        if (header.size != 3 || header[0] != MAGIC || header[1] != VERSION) return null
        if (unescape(header[2]) != expectedTreeUri) return null
        val songs = mutableListOf<Song>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val song = parseLine(line) ?: continue // damaged line: skip it, keep the rest
            songs += song
        }
        return songs.ifEmpty { null }
    }

    private fun parseLine(line: String): Song? {
        val fields = line.split('\t')
        if (fields.size != 6) return null
        val category = try {
            Category.valueOf(fields[3])
        } catch (e: IllegalArgumentException) {
            return null
        }
        val lyrics = unescape(fields[5])
        return Song(
            id = unescape(fields[0]),
            artist = unescape(fields[1]),
            title = unescape(fields[2]),
            category = category,
            audioRef = unescape(fields[4]),
            lyricsRef = lyrics.ifEmpty { null },
        )
    }

    private fun escape(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(escaped: String): String {
        if ('\\' !in escaped) return escaped
        val sb = StringBuilder(escaped.length)
        var i = 0
        while (i < escaped.length) {
            val c = escaped[i]
            if (c == '\\' && i + 1 < escaped.length) {
                when (escaped[i + 1]) {
                    '\\' -> sb.append('\\')
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(escaped[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

/** Reads/writes the cache file in the app's private storage.
 *  Best-effort: every failure just means "no cache". Call from a
 *  background dispatcher — this does file IO. */
class LibraryCacheStore(context: Context) {

    private val file = File(context.filesDir, "catalog_cache.txt")

    fun load(treeUri: String): List<Song>? = try {
        if (!file.isFile) null
        else LibraryCache.decode(file.readText(Charsets.UTF_8), treeUri)
    } catch (e: Exception) {
        null
    }

    fun save(treeUri: String, songs: List<Song>) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(LibraryCache.encode(treeUri, songs), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { // fallback: write in place
                file.writeText(LibraryCache.encode(treeUri, songs), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            // Cache is an optimisation only; losing it costs nothing.
        }
    }
}
