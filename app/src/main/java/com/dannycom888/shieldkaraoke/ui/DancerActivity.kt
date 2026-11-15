package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import kotlin.math.PI
import kotlin.math.cos

/**
 * Whether the little dancer is still dancing at a playback position.
 *
 * The user's rule: "歌都唱完了，剩下音乐，别跳了" — once the last
 * lyric line has been sung and only the outro music remains, the
 * dancer stops: it stands in its neutral first frame with no frame
 * cycling and no bounce/sway (the baby does not fall, the robot
 * halts too), only breathing slowly.
 *
 * The intro got its own rule later (v1.0.7): "前奏也别跳，做邀请的
 * 动作" — before the first lyric line the dancer does not dance
 * either; it holds its "please sing" invite frame with a slow bow.
 * So a song has three phases — INVITING / DANCING / STILL — all
 * derived from the lyric boundaries (see [phase]).
 *
 * Pure functions of the parsed lyrics + position, so the rule is
 * unit-testable without a screen.
 */
object DancerActivity {

    /** Frame shown while standing still (each dancer's neutral pose). */
    const val STILL_FRAME = 0

    /** The dancer's three phases over one song. */
    enum class Phase { INVITING, DANCING, STILL }

    /**
     * When the singing starts in [lines]: the first line's start.
     * No lines at all → null (no intro phase exists — symmetric
     * with [lastLyricEndMs] being null for the outro).
     */
    fun firstLyricStartMs(lines: List<LyricLine>): Long? =
        lines.firstOrNull()?.timeMs

    /**
     * The phase at [positionMs]: before the first lyric line →
     * INVITING (the invite frame + slow bow); past the last
     * lyric's end → STILL (neutral frame + breathing); in between
     * → DANCING. A song without an LRC has neither boundary, so
     * it dances the whole song rather than inviting or stopping
     * by mistake.
     */
    fun phase(positionMs: Long, firstLyricStartMs: Long?, lastLyricEndMs: Long?): Phase = when {
        firstLyricStartMs != null && positionMs < firstLyricStartMs -> Phase.INVITING
        !isDancing(positionMs, lastLyricEndMs) -> Phase.STILL
        else -> Phase.DANCING
    }

    /**
     * When the singing ends in [lines]: the last line's end time.
     *
     * An enhanced (word-timed) line carries its end explicitly — the
     * trailing empty fragment is the line's end marker, and its start
     * is when the line stops being sung (see [com.dannycom888
     * .shieldkaraoke.lyrics.WordTiming]). A plain line has no end
     * marker, so its own start stands in as the fallback: the dancer
     * stops once the last line has begun and its start has passed.
     * No lines at all → null (no "sung out" moment exists).
     */
    fun lastLyricEndMs(lines: List<LyricLine>): Long? {
        val last = lines.lastOrNull() ?: return null
        val marker = last.words.lastOrNull()
        return if (marker != null && marker.text.isEmpty() && marker.startMs >= last.timeMs) {
            marker.startMs
        } else {
            last.timeMs
        }
    }

    /**
     * Dancing at [positionMs]? Past the last lyric's end → still.
     * A null [lastLyricEndMs] (the song has no same-name LRC) means
     * there is no singing to finish, so the dancer dances the whole
     * song rather than stopping by mistake.
     */
    fun isDancing(positionMs: Long, lastLyricEndMs: Long?): Boolean =
        lastLyricEndMs == null || positionMs <= lastLyricEndMs

    /** One full breath (in + out) while standing still, in ms of
     *  playback position. */
    const val BREATH_PERIOD_MS = 2_000L

    /** Peak extra scale at the top of a breath: 1.000 → 1.018. */
    const val BREATH_SCALE_AMP = 0.018f

    /** Peak upward drift at the top of a breath, as a bounce
     *  fraction — the renderer turns 1.0 into a 5.5%-of-height
     *  rise, so 0.08 lifts the figure by only ~0.4% of its height. */
    const val BREATH_RISE_AMP = 0.08f

    /**
     * The standing-still pose: not frozen solid, but breathing.
     *
     * The user's follow-up to the outro rule: "站着不动可以做喘气
     * 的动作" — while the dancer stands in its neutral frame for
     * the outro, it breathes slowly: one gentle in-out cycle every
     * [BREATH_PERIOD_MS], the body swelling by at most
     * [BREATH_SCALE_AMP] with a barely-there upward drift. Small
     * enough to read as breathing, never as dancing: the frame
     * never changes, nothing follows the beat, there is no sway.
     * The robot breathes at half amplitude (a machine idling).
     *
     * Like everything else on the player screen this is a pure
     * function of the *playback position*, so pausing freezes the
     * breath mid-cycle exactly like it freezes the dance.
     */
    fun breathPose(positionMs: Long, profile: DanceMotion.Profile): DanceMotion.Pose {
        val amp = if (profile == DanceMotion.Profile.ROBOT) 0.5 else 1.0
        val t = ((positionMs % BREATH_PERIOD_MS) + BREATH_PERIOD_MS) % BREATH_PERIOD_MS
        val phase = t.toDouble() / BREATH_PERIOD_MS.toDouble() // 0..1
        val lift = 0.5 - 0.5 * cos(2 * PI * phase) // 0 → 1 → 0 over one breath
        return DanceMotion.Pose(
            bounceFraction = (BREATH_RISE_AMP * amp * lift).toFloat(),
            rotationDeg = 0f,
            scale = (1.0 + BREATH_SCALE_AMP * amp * lift).toFloat(),
        )
    }

    /** One full invite bow (lean in + return), in ms of playback
     *  position. */
    const val INVITE_PERIOD_MS = 2_000L

    /** Peak forward lean of the invite bow, in degrees — a small,
     *  elegant tilt, not a dance move. */
    const val INVITE_LEAN_DEG = 2.4f

    /** Peak dip of the invite bow, as a sink fraction — the
     *  renderer turns 1.0 into a 30%-of-height drop, so 0.07 dips
     *  the figure by only ~2% of its height. */
    const val INVITE_SINK_AMP = 0.07f

    /**
     * The intro pose: a slow "please sing" bow over the invite
     * frame. One lean-in-and-return cycle every [INVITE_PERIOD_MS]
     * of *playback position* (pausing freezes the bow, like
     * everything else): the figure tilts forward by at most
     * [INVITE_LEAN_DEG] with a slight dip, no frame change, no
     * beat sync, no scale swell. The robot bows at half amplitude
     * (a machine being polite), matching its half-amplitude
     * breathing.
     */
    fun invitePose(positionMs: Long, profile: DanceMotion.Profile): DanceMotion.Pose {
        val amp = if (profile == DanceMotion.Profile.ROBOT) 0.5 else 1.0
        val t = ((positionMs % INVITE_PERIOD_MS) + INVITE_PERIOD_MS) % INVITE_PERIOD_MS
        val phase = t.toDouble() / INVITE_PERIOD_MS.toDouble() // 0..1
        val lean = 0.5 - 0.5 * cos(2 * PI * phase) // 0 → 1 → 0 over one bow
        return DanceMotion.Pose(
            bounceFraction = 0f,
            sinkFraction = (INVITE_SINK_AMP * amp * lean).toFloat(),
            rotationDeg = (INVITE_LEAN_DEG * amp * lean).toFloat(),
            scale = 1f,
        )
    }
}
