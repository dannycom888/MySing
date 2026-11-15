package com.dannycom888.shieldkaraoke.state

import android.content.Context
import android.content.SharedPreferences

/** Favorites, recents, library location and loudness cache. */
class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shield_karaoke", Context.MODE_PRIVATE)

    var libraryTreeUri: String?
        get() = prefs.getString("library_tree_uri", null)
        set(value) = prefs.edit().putString("library_tree_uri", value).apply()

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun setFavorite(songId: String, favorite: Boolean) {
        val next = favorites().toMutableSet()
        if (favorite) next += songId else next -= songId
        prefs.edit().putStringSet("favorites", next).apply()
    }

    /** Most-recent-first song ids, capped at 30. */
    fun recents(): List<String> =
        (prefs.getString("recents", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun pushRecent(songId: String) {
        val next = (listOf(songId) + recents().filter { it != songId }).take(30)
        prefs.edit().putString("recents", next.joinToString("\n")).apply()
    }

    fun loudnessDb(songId: String): Double? =
        prefs.getString("loud_$songId", null)?.toDoubleOrNull()

    fun setLoudnessDb(songId: String, db: Double) {
        prefs.edit().putString("loud_$songId", db.toString()).apply()
    }
}
