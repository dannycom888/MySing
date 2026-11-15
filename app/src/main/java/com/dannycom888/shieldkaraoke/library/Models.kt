package com.dannycom888.shieldkaraoke.library

/** Song language, decided purely by which library folder a file sits in. */
enum class Language { CHINESE, ENGLISH }

/** Singer gender bucket, likewise decided by the folder. Groups are NOT
 *  a separate bucket (user ruling 2026-11-14): a group's songs live in
 *  whichever of the boy/girl folders they were dropped into. */
enum class Gender { MALE, FEMALE }

/** One of the four library folders on the hard disk. */
enum class Category(val folderName: String, val language: Language, val gender: Gender) {
    CHINESE_BOY("music chinese boy", Language.CHINESE, Gender.MALE),
    CHINESE_GIRL("music chinese girl", Language.CHINESE, Gender.FEMALE),
    ENGLISH_BOY("music english boy", Language.ENGLISH, Gender.MALE),
    ENGLISH_GIRL("music english girl", Language.ENGLISH, Gender.FEMALE);

    companion object {
        fun fromFolderName(name: String): Category? =
            entries.firstOrNull { it.folderName.equals(name.trim(), ignoreCase = true) }
    }
}

/** One song in the catalog. [id] is the file stem, stable across rescans. */
data class Song(
    val id: String,
    val artist: String,
    val title: String,
    val category: Category,
    /** Content URI / path of the MP3, as given by the file source. */
    val audioRef: String,
    /** Same-stem .lrc beside the MP3, or null when the song has no lyrics file. */
    val lyricsRef: String?,
) {
    val displayName: String get() = if (artist.isBlank()) title else "$artist - $title"
}

/** A raw file as reported by a library source (SAF tree, disk, test fake). */
data class RawFile(
    val folderName: String,
    val fileName: String,
    val ref: String,
)
