package com.dannycom888.shieldkaraoke.library

/**
 * Browse/search views over the scanned catalog. The filtering rules
 * are the user's (2026-11-14):
 *  - home "singers" = every singer, all languages and genders merged;
 *  - after picking a language, its singer list merges that language's
 *    boy+girl folders (so 凤凰传奇 is found under 中文→歌手 no matter
 *    which folder its files were dropped into);
 *  - 男歌手/女歌手 sub-filters narrow to one folder bucket only when
 *    explicitly picked.
 */
class Catalog(val songs: List<Song>) {

    fun singers(language: Language? = null, gender: Gender? = null): List<String> =
        filtered(language, gender).map { it.artist }.filter { it.isNotBlank() }
            .distinct().sortedWith(compareBy({ PinyinInitials.initialsOf(it) }, { it }))

    fun songsOf(artist: String): List<Song> =
        songs.filter { it.artist.equals(artist, ignoreCase = true) }

    fun filtered(language: Language? = null, gender: Gender? = null): List<Song> =
        songs.filter {
            (language == null || it.category.language == language) &&
                (gender == null || it.category.gender == gender)
        }

    fun byId(id: String): Song? = songs.firstOrNull { it.id == id }

    fun search(query: String): List<Song> = songs.filter { PinyinInitials.matches(it, query) }
}
