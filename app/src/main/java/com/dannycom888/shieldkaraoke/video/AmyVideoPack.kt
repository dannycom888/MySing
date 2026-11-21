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
    /** The clip's measured figure height, as a fraction of
     *  the frame (v1.0.72: packaging-approach trend median,
     *  written by tools/measure_figure_scales.py; 0 when the
     *  packaged JSON predates the field). */
    val figureHeight: Double = 0.0,
    /** Playback scale factor that lands this clip's figure
     *  on the pack's common on-stage height (v1.0.72):
     *  figureHeight × figureScale is the same anchor for
     *  every clip, so chaining no longer changes Amy's size
     *  mid-song. 1.0 when the packaged JSON predates it. */
    val figureScale: Double = 1.0,
    /** The clip's foot baseline, as a texture v fraction
     *  measured UP from the frame's bottom edge (v1.0.73,
     *  tools/measure_feet.py): the wide-shot framing keeps
     *  a green margin below the boots — 0.035..0.120 across
     *  the pack — so the stage anchors the GL sampling on
     *  this row: the feet land exactly on the stage
     *  baseline and the reflection mirrors from the same
     *  row, closing the gap that made her float. The pack
     *  median when the packaged JSON predates the field. */
    val feetV: Double = AmyVideoSizing.DEFAULT_FEET_V,
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
            figureHeight = json.optDouble("figure_height", 0.0),
            figureScale = json.optDouble("figure_scale", 1.0),
            feetV = json.optDouble("feet_v", AmyVideoSizing.DEFAULT_FEET_V),
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
     * Whether the pool implied by [measuredBpm] differs
     * from the pool a song is already dancing (v1.0.76).
     * The pool is picked at song start with the tempo known
     * at that instant — for an uncached song that is the
     * 96 BPM default, which always implies the slow pool —
     * so when the background measurement lands mid-song the
     * caller rebuilds the walk if (and only if) this says
     * the measured tempo belongs to the other pool. The
     * comparison is by clip names, so the damaged-pack
     * fallback in [dancePool] (an empty group widening to
     * every dance clip) can never trigger a spurious
     * rebuild: the widened pool equals itself at any tempo.
     */
    fun poolNeedsRebuild(
        clips: List<AmyClip>,
        inUsePool: List<AmyClip>,
        measuredBpm: Double,
    ): Boolean =
        dancePool(clips, measuredBpm).map { it.name } != inUsePool.map { it.name }

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
     *
     * v1.0.73: the dance driver no longer walks with this —
     * it rotates with [DanceRotation] (shuffle-with-
     * exhaustion). chainNext stays as the stateless fallback
     * for a driver that has no rotation to consult.
     */
    fun chainNext(pool: List<AmyClip>, currentName: String): AmyClip? {
        if (pool.size < 2) return null
        val index = pool.indexOfFirst { it.name == currentName }
        if (index < 0) return null
        return pool[(index + 1) % pool.size]
    }
}

/**
 * One song's dance rotation (v1.0.73): shuffle-with-
 * exhaustion over the song's tempo pool — EVERY clip of
 * the group dances before any clip repeats (the user's
 * ruling: the group's ten clips are enough for one song;
 * what read as repetition was the walk, not the pool).
 *
 * The rotation is created in startSong and held in
 * MainActivity state, NOT inside the video figure: the
 * figure's own remembered state re-keys and dies with
 * composition, and a walk that restarts from the song's
 * pick every time the stage recomposes is a walk that
 * keeps serving the same first clips. Held here, the
 * walk survives the figure entirely — if the figure is
 * disposed and rebuilt mid-song it re-reads [current]
 * and continues where the song left off.
 *
 * The song's pick plays first (startSong chose it for
 * tempo fit); the rest of the cycle is shuffled. (v1.0.76:
 * a mid-song pool rebuild anchors the replacement rotation
 * on the outgoing walk's [peekNext] instead — the clip
 * already loaded for the next pass boundary — so that clip
 * still "plays first" for the new rotation and everything
 * after it comes from the new pool.) When a
 * cycle is exhausted the next one is pre-shuffled so
 * [peekNext] is always defined, and its first clip is
 * never the clip that just played (pool size > 1), so
 * no clip ever plays twice in a row — not even across
 * the cycle seam. [shuffle] is injectable for tests.
 */
class DanceRotation(
    private val pool: List<AmyClip>,
    first: AmyClip,
    private val shuffle: (List<AmyClip>) -> List<AmyClip> = { it.shuffled() },
) {
    private var order: List<AmyClip>
    private var nextOrder: List<AmyClip>
    private var index: Int = 0

    init {
        val rest = pool.filter { it.name != first.name }
        order = listOf(first) + shuffle(rest)
        nextOrder = shuffledCycle(after = order.last())
    }

    /** A fresh shuffled cycle whose first clip differs
     *  from [after] (the clip playing as the seam is
     *  crossed), so the seam never repeats a clip. */
    private fun shuffledCycle(after: AmyClip): List<AmyClip> {
        if (pool.size <= 1) return pool
        val candidate = shuffle(pool)
        if (candidate.first().name != after.name) return candidate
        // Rotate the repeat away: the pool has another
        // clip, and it leads the new cycle instead.
        return candidate.drop(1) + candidate.first()
    }

    /** The clip dancing now. */
    fun current(): AmyClip = order[index]

    /**
     * The clip that dances after [current] — what
     * [advance] will land on. The driver preloads exactly
     * this clip, so the pass boundary is a swap to a
     * ready player, never a guess. Null when the pool has
     * fewer than two clips (nothing to rotate to — the
     * driver loops the current clip, as the pilot did).
     */
    fun peekNext(): AmyClip? = when {
        pool.size < 2 -> null
        index + 1 < order.size -> order[index + 1]
        else -> nextOrder.first()
    }

    /**
     * Move the walk to [peekNext]'s clip and return it.
     * With a one-clip pool this is a no-op returning the
     * same clip.
     */
    fun advance(): AmyClip {
        if (pool.size < 2) return current()
        if (index + 1 < order.size) {
            index++
        } else {
            order = nextOrder
            index = 0
            nextOrder = shuffledCycle(after = order.last())
        }
        return current()
    }
}
