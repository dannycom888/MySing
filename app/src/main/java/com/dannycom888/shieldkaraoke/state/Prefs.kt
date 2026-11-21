package com.dannycom888.shieldkaraoke.state

import android.content.Context
import android.content.SharedPreferences
import com.dannycom888.shieldkaraoke.audio.BpmCacheFormat

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

    /** Measured tempo cache, alongside the loudness cache.
     *  Entries are estimator-versioned ([BpmCacheFormat],
     *  v1.0.76): a value written by an older detector reads
     *  as uncached here, so the song is re-measured instead
     *  of dancing forever at a stale tempo. */
    fun bpm(songId: String): Double? =
        BpmCacheFormat.decode(prefs.getString("bpm_$songId", null))

    fun setBpm(songId: String, bpm: Double) {
        prefs.edit().putString("bpm_$songId", BpmCacheFormat.encode(bpm)).apply()
    }

    /** Whether the dancers show on the player screen at all
     *  (TV control-bar switch; *which* dancer comes out is locked
     *  to each song at order time, v1.0.52). */
    var dancersEnabled: Boolean
        get() = prefs.getBoolean("dancers_enabled", true)
        set(value) = prefs.edit().putBoolean("dancers_enabled", value).apply()

    /** Karaoke sweep colour (v1.0.26), chosen on the TV admin
     *  page. Stored as the [SweepColor] enum name; parsing and
     *  the gold fallback live in [SweepColor.fromStoredName]. */
    var sweepColor: SweepColor
        get() = SweepColor.fromStoredName(prefs.getString("sweep_color", null))
        set(value) = prefs.edit().putString("sweep_color", value.name).apply()

    /**
     * Per-orderer last-used dancer pick: orderer name -> dancer key.
     * Stored as `name\u0001key` lines in one string.
     *
     * v1.0.52: this store is a *preselection* memory only — the
     * pickers (phone page, TV order dialog) open on the name's last
     * pick. The dancer that actually dances for a song is the
     * snapshot frozen into its queue entry at order time
     * ([com.dannycom888.shieldkaraoke.queue.QueueEntry.dancerKey]);
     * nothing reads this store at playback time anymore.
     */
    fun dancerSelections(): Map<String, String> {
        val raw = prefs.getString("dancer_by_name", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val idx = line.lastIndexOf('\u0001')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
    }

    /**
     * Custom dancer display names (v1.0.21): dancer key -> the
     * name the host typed on the TV admin page. Stored as
     * `key\u0001name` lines in one string, same pattern as the
     * per-orderer selections below. An absent key means the
     * dancer keeps its default stage name; resolution lives in
     * [DancerNames.display]. Renaming is TV-only by design — the
     * phone server gets read access (to label the picker cards)
     * and no write path at all.
     */
    fun customDancerNames(): Map<String, String> {
        val raw = prefs.getString("dancer_custom_names", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val idx = line.lastIndexOf('\u0001')
            if (idx <= 0) null else {
                val key = line.substring(0, idx)
                val name = line.substring(idx + 1)
                if (DancerKeys.isValid(key) && name.isNotBlank()) key to name else null
            }
        }.toMap()
    }

    /** Store one dancer's custom name; unusable names (blank
     *  after cleaning) and unknown keys are ignored, so an empty
     *  edit can never wipe a name. */
    fun setCustomDancerName(key: String, name: String) {
        val clean = DancerNames.sanitize(name) ?: return
        if (!DancerKeys.isValid(key)) return
        val next = customDancerNames().toMutableMap()
        next[key] = clean
        prefs.edit().putString(
            "dancer_custom_names",
            next.entries.joinToString("\n") { "${it.key}\u0001${it.value}" },
        ).apply()
    }

    /** "Restore default names" on the admin page: drop every
     *  custom name at once. */
    fun clearCustomDancerNames() {
        prefs.edit().remove("dancer_custom_names").apply()
    }

    /** The dancer [name] picked last (picker preselection only
     *  since v1.0.52), or null (caller uses the default). */
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
