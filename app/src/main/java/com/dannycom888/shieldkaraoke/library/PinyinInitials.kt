package com.dannycom888.shieldkaraoke.library

/**
 * Pinyin-initial search keys: "凤凰传奇" → "fhcq", so a phone user can
 * type initials instead of full characters. The char table covers the
 * real library (see PinyinData); ASCII letters/digits pass through.
 */
object PinyinInitials {

    fun initialsOf(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch.isWhitespace() || ch in "-–—·•,，.。()（）" -> Unit
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch in 'A'..'Z' -> sb.append(ch.lowercaseChar())
                else -> sb.append(PINYIN_INITIALS[ch] ?: ch)
            }
        }
        return sb.toString()
    }

    /** True when [query] (already lowercased) matches the song by full
     *  text substring or by pinyin initials of artist/title. */
    fun matches(song: Song, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (song.artist.lowercase().contains(q) || song.title.lowercase().contains(q)) return true
        if (q.all { it in 'a'..'z' || it in '0'..'9' }) {
            return initialsOf(song.artist).contains(q) || initialsOf(song.title).contains(q) ||
                initialsOf(song.artist + song.title).contains(q)
        }
        return false
    }
}
