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

    /** TV interface language (v1.0.13): "zh" / "en", default "zh".
     *  Stored as the wire code; [com.dannycom888.shieldkaraoke.i18n.UiLang]
     *  parses it. The phone page keeps its own per-phone choice and
     *  never reads this. */
    var uiLangCode: String
        get() = prefs.getString("ui_lang", "zh") ?: "zh"
        set(value) = prefs.edit().putString("ui_lang", value).apply()

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

    /** Measured tempo cache, alongside the loudness cache. */
    fun bpm(songId: String): Double? =
        prefs.getString("bpm_$songId", null)?.toDoubleOrNull()

    fun setBpm(songId: String, bpm: Double) {
        prefs.edit().putString("bpm_$songId", bpm.toString()).apply()
    }

    /** Whether the little dancers show on the player screen at all
     *  (TV control-bar switch; the *choice* of dancer is per-orderer
     *  and made on the phone page). */
    var dancersEnabled: Boolean
        get() = prefs.getBoolean("dancers_enabled", true)
        set(value) = prefs.edit().putBoolean("dancers_enabled", value).apply()

    /** Idle filler playback (v1.0.20): when nobody has ordered a
     *  song, random library songs play as background. TV control-bar
     *  switch, default on (the user's ruling 2026-11-15). */
    var randomFillerEnabled: Boolean
        get() = prefs.getBoolean("random_filler_enabled", true)
        set(value) = prefs.edit().putBoolean("random_filler_enabled", value).apply()

    /**
     * Per-orderer dancer choice: orderer name -> dancer key.
     * Stored as `name\u0001key` lines in one string.
     */
    fun dancerSelections(): Map<String, String> {
        val raw = prefs.getString("dancer_by_name", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val idx = line.lastIndexOf('\u0001')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
    }

    /** The dancer [name] chose, or null (caller uses the default). */
    fun dancerFor(name: String): String? = dancerSelections()[name]

    fun setDancer(name: String, key: String) {
        val clean = name.replace('\u0001', ' ').replace('\n', ' ').trim()
        if (clean.isBlank() || !DancerKeys.isValid(key)) return
        val next = dancerSelections().toMutableMap()
        next[clean] = key
        prefs.edit().putString(
            "dancer_by_name",
            next.entries.joinToString("\n") { "${it.key}\u0001${it.value}" },
        ).apply()
    }
}
