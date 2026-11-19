package com.dannycom888.shieldkaraoke.video

import org.json.JSONObject
import kotlin.math.abs

/**
 * One packed Amy video clip (v1.0.70 pilot): a green-screen
 * H.264 master in the app assets plus the accent list the
 * packaging pipeline measured off it (see
 * dancers/amy-wide/pack/manifest.json — 24 clips: fast ×10,
 * slow ×10, and the invite / clap / idle / combo specials).
 */
data class AmyClip(
    /** Clip name, also its file stem ("fast-01", "invite"). */
    val name: String,
    /** "fast" | "slow" | "special" (the pack's own grouping). */
    val group: String,
    /** Asset path of the MP4 ("amy_video/fast-01.mp4"). */
    val assetPath: String,
    /** Clip length in seconds. */
    val durationS: Double,
    /** Movement accents in seconds, ascending. */
    val accentsS: List<Double>,
    /** The tempo the accent spacing implies (0 when the
     *  pipeline could not measure one — the specials). */
    val impliedBpm: Double,
)

/**
 * The Amy video pack catalogue: parsing the packaged accent
 * JSONs and choosing which clip dances next. Pure logic —
 * the asset reading itself lives with the stage glue.
 */
object AmyVideoPack {

    /** Asset directory the pack ships in. */
    const val ASSET_DIR = "amy_video"

    const val GROUP_FAST = "fast"
    const val GROUP_SLOW = "slow"
    const val GROUP_SPECIAL = "special"

    const val SPECIAL_INVITE = "invite"
    const val SPECIAL_CLAP = "clap"
    const val SPECIAL_IDLE = "idle"
    const val SPECIAL_COMBO = "combo"

    /**
     * Songs at or above this tempo draw from the fast pool,
     * slower songs from the slow pool. The split sits between
     * the two groups' heartlands (the slow group's implied
     * tempos top out in the low 140s only through sparse
     * accents; its measured centres are far lower), and the
     * per-segment speed clamp is the real guard — the pool
     * choice only has to be roughly right.
     */
    const val FAST_POOL_MIN_BPM = 100.0

    /**
     * Parse one packaged accent JSON (the exact shape
     * pack_pipeline.py writes: clip / group / duration_s /
     * accents / implied_bpm). Throws on malformed input —
     * the loader treats a bad file as a missing clip.
     */
    fun parseAccentsJson(text: String): AmyClip {
        val json = JSONObject(text)
        val name = json.getString("clip")
        val accentsJson = json.getJSONArray("accents")
        val accents = (0 until accentsJson.length())
            .map { accentsJson.getDouble(it) }
            .sorted()
        return AmyClip(
            name = name,
            group = json.optString("group", GROUP_SPECIAL),
            assetPath = "$ASSET_DIR/$name.mp4",
            durationS = json.getDouble("duration_s"),
            accentsS = accents,
            impliedBpm = json.optDouble("implied_bpm", 0.0),
        )
    }

    /** The special clip called [name] ("invite", "clap",
     *  "idle", "combo"), or null when the pack lacks it. */
    fun special(clips: List<AmyClip>, name: String): AmyClip? =
        clips.firstOrNull { it.group == GROUP_SPECIAL && it.name == name }

    /**
     * The dance pool for a song at [songBpm]: the fast group
     * for fast songs, the slow group for slow ones. Falls back
     * to every dance clip when the wanted group is empty (a
     * damaged pack still dances rather than not dancing).
     */
    fun dancePool(clips: List<AmyClip>, songBpm: Double): List<AmyClip> {
        val wanted = if (songBpm >= FAST_POOL_MIN_BPM) GROUP_FAST else GROUP_SLOW
        val group = clips.filter { it.group == wanted }
        if (group.isNotEmpty()) return group
        return clips.filter { it.group == GROUP_FAST || it.group == GROUP_SLOW }
    }

    /**
     * Pick the next clip from [pool] for a song at [songBpm]:
     * the pool ordered by how close each clip's implied tempo
     * sits to the song's, walked by [rotation] (the caller
     * advances it once per song, so consecutive songs rotate
     * through the best-fitting clips instead of always taking
     * the single closest one) — and never the clip that just
     * played when the pool offers any other.
     */
    fun pickNext(
        pool: List<AmyClip>,
        songBpm: Double,
        lastClipName: String?,
        rotation: Int,
    ): AmyClip? {
        if (pool.isEmpty()) return null
        val byFit = pool.sortedBy { abs(it.impliedBpm - songBpm) }
        var index = ((rotation % byFit.size) + byFit.size) % byFit.size
        if (byFit[index].name == lastClipName && byFit.size > 1) {
            index = (index + 1) % byFit.size
        }
        return byFit[index]
    }

    /**
     * The next clip when CHAINING inside a song (v1.0.71):
     * the clips are building blocks, not loops — at each
     * pass boundary the dance moves on to the next clip of
     * the same tempo pool, walked in pool order from
     * [currentName] and wrapping at the end, so a long song
     * cycles the whole group. Never the current clip itself
     * when the pool offers any other. Null when the pool has
     * fewer than two clips (nothing to chain to) or the
     * current clip is not in the pool — the caller then
     * simply loops the current clip, as the pilot did.
     */
    fun chainNext(pool: List<AmyClip>, currentName: String): AmyClip? {
        if (pool.size < 2) return null
        val index = pool.indexOfFirst { it.name == currentName }
        if (index < 0) return null
        return pool[(index + 1) % pool.size]
    }
}
