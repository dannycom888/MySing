package com.dannycom888.shieldkaraoke.library

/**
 * Turns a flat listing of the library's files into the song catalog.
 * Pure: the Android side feeds it SAF [RawFile]s, tests feed fakes.
 *
 * Rules (user spec):
 *  - category comes from the folder, never from tags;
 *  - artist/title come from the file name "歌手 - 歌名", never from tags;
 *  - an MP3 and a same-stem .lrc in the same folder are one song;
 *  - a rescan rebuilds the catalog wholesale, so songs deleted from
 *    the disk drop out and newly added songs join automatically.
 */
object LibraryScanner {

    private val audioExts = setOf("mp3", "m4a", "flac", "wav", "ogg")
    private val separators = listOf(" - ", " – ", " — ")

    fun scan(files: List<RawFile>): List<Song> {
        val lyricsByKey = mutableMapOf<String, String>()
        val audio = mutableListOf<RawFile>()
        for (file in files) {
            val ext = file.fileName.substringAfterLast('.', "").lowercase()
            val category = Category.fromFolderName(file.folderName) ?: continue
            val stem = file.fileName.substringBeforeLast('.', file.fileName)
            when {
                ext == "lrc" -> lyricsByKey[key(category, stem)] = file.ref
                ext in audioExts -> audio += file
            }
        }
        return audio.map { file ->
            val category = Category.fromFolderName(file.folderName)!!
            val stem = file.fileName.substringBeforeLast('.', file.fileName)
            val (artist, title) = splitArtistTitle(stem)
            Song(
                id = key(category, stem),
                artist = artist,
                title = title,
                category = category,
                audioRef = file.ref,
                lyricsRef = lyricsByKey[key(category, stem)],
            )
        }.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    }

    private fun key(category: Category, stem: String) = "${category.name}|${stem.lowercase()}"

    /** "歌手 - 歌名" → (歌手, 歌名). Without a separator the whole stem is
     *  the title and the artist is blank (file name is the only truth). */
    fun splitArtistTitle(stem: String): Pair<String, String> {
        for (sep in separators) {
            val idx = stem.indexOf(sep)
            if (idx > 0) {
                return stem.substring(0, idx).trim() to stem.substring(idx + sep.length).trim()
            }
        }
        // Some files use "歌手-歌名" without spaces.
        val dash = stem.indexOf('-')
        if (dash > 0 && !stem.substring(0, dash).contains(' ')) {
            return stem.substring(0, dash).trim() to stem.substring(dash + 1).trim()
        }
        return "" to stem.trim()
    }
}
