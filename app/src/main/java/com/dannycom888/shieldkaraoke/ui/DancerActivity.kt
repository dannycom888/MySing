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
 * And the outro got a refinement in v1.0.22: instead of cutting
 * straight to standing when the singing ends, the last dance pose
 * is held for 2 s while it settles to neutral (OUTRO_HOLD), then
 * melts into the standing breathing phase. So a song has four
 * phases — INVITING / DANCING / OUTRO_HOLD / STILL — all derived
 * from the lyric boundaries (see [phase]).
 *
 * Pure functions of the parsed lyrics + position, so the rule is
 * unit-testable without a screen.
 */
object DancerActivity {

    /** Frame shown while standing still (each dancer's neutral pose). */
    const val STILL_FRAME = 0

    /**
     * The shortest intro that still counts as an intro (v1.0.22).
     * The user's rule: "如果没前奏就直接跳" — when the first lyric
     * line starts less than this far into the song there is no
     * intro worth bowing through, so the dancer dances from
     * position 0 instead of inviting first. Only a first line at
     * or after this threshold opens with the INVITING phase.
     */
    const val MIN_INTRO_MS = 2_500L

    /**
     * Does this song have an intro phase? Only when the singing
     * starts at or after [MIN_INTRO_MS]; an earlier first line (or
     * no LRC at all) means dancing from the very beginning.
     */
    fun hasIntro(firstLyricStartMs: Long?): Boolean =
        firstLyricStartMs != null && firstLyricStartMs >= MIN_INTRO_MS

    /** The dancer's phases over one song (OUTRO_HOLD added in
     *  v1.0.22 — see [OUTRO_HOLD_MS]). */
    enum class Phase { INVITING, DANCING, OUTRO_HOLD, STILL }

    /**
     * How long the last dance pose is held after the singing ends
     * (v1.0.22). The user's refinement of the outro: "后奏有时很
     * 长，可以最后一个动作停2秒后将双手放下喘气" — the moment
     * the last lyric line ends, the dancer freezes in its final
     * dance pose for this long (the whole-body transform easing
     * back to neutral meanwhile, see [outroHoldPose]) instead of
     * snapping straight to the standing frame.
     */
    const val OUTRO_HOLD_MS = 2_000L

    /**
     * The cross-fade from the frozen dance pose to the standing
     * frame once the hold is over (v1.0.22) — "双手放下" should
     * read as a relax, not a cut. The robot is exempt at the call
     * site (his changes stay hard steps).
     */
    const val OUTRO_SETTLE_MS = 400L

    /**
     * When the singing starts in [lines]: the first line's start.
     * No lines at all → null (no intro phase exists — symmetric
     * with [lastLyricEndMs] being null for the outro).
     */
    fun firstLyricStartMs(lines: List<LyricLine>): Long? =
        lines.firstOrNull()?.timeMs

    /**
     * The phase at [positionMs]: before the first lyric line →
     * INVITING (the invite frame + slow bow) — but only when the
     * song actually has an intro (see [hasIntro]; a first line
     * starting before [MIN_INTRO_MS] means dancing from 0);
     * the first [OUTRO_HOLD_MS] past the last lyric's end →
     * OUTRO_HOLD (last dance pose held, settling to neutral);
     * beyond that → STILL (standing frame + breathing); in
     * between → DANCING. A song without an LRC has neither
     * boundary, so it dances the whole song rather than inviting
     * or stopping by mistake.
     */
    fun phase(
        positionMs: Long,
        firstLyricStartMs: Long?,
        lastLyricEndMs: Long?,
        durationMs: Long? = null,
    ): Phase = when {
        hasIntro(firstLyricStartMs) && positionMs < firstLyricStartMs!! -> Phase.INVITING
        // v1.0.22: the outro opens with a 2 s hold on the last
        // dance pose before the standing frame. A song whose
        // outro is shorter than the hold simply ends inside it
        // (the player moves on; the applause flow is untouched) —
        // at or past the song's duration we report STILL.
        lastLyricEndMs != null && positionMs > lastLyricEndMs &&
            positionMs - lastLyricEndMs < OUTRO_HOLD_MS &&
            (durationMs == null || durationMs <= 0 || positionMs < durationMs) ->
            Phase.OUTRO_HOLD
        !isDancing(positionMs, lastLyricEndMs) -> Phase.STILL
        else -> Phase.DANCING
    }

    /**
     * The pose during the outro hold: [frozen] — the dance pose
     * exactly as it stood when the singing ended — easing back to
     * neutral (no bounce, no sway, scale 1, no sink) across
     * [OUTRO_HOLD_MS] with a smoothstep, so the figure does not
     * stay frozen mid-bounce in the air but settles gently while
     * the frame itself stays put. Pure function of position;
     * pausing freezes the settle like everything else. The baby
     * never starts a fall here: the caller evaluates the frozen
     * pose once at the lyric end, and this function only relaxes
     * it toward upright.
     */
    fun outroHoldPose(
        frozen: DanceMotion.Pose,
        positionMs: Long,
        lastLyricEndMs: Long,
    ): DanceMotion.Pose {
        val raw = (positionMs - lastLyricEndMs).coerceIn(0L, OUTRO_HOLD_MS).toFloat() /
            OUTRO_HOLD_MS.toFloat()
        val t = raw * raw * (3f - 2f * raw) // smoothstep 0→1
        val keep = 1f - t
        return DanceMotion.Pose(
            bounceFraction = frozen.bounceFraction * keep,
            rotationDeg = frozen.rotationDeg * keep,
            scale = 1f + (frozen.scale - 1f) * keep,
            sinkFraction = frozen.sinkFraction * keep,
        )
    }

    /**
     * Cross-fade progress from the frozen dance frame to the
     * standing frame during the settle window that follows the
     * outro hold: 0 at the hold's end, rising to (but never
     * reaching, the window closes first) 1 across
     * [OUTRO_SETTLE_MS]; null outside the window — before it the
     * hold is still on, after it the standing frame stands alone
     * and the breathing (an absolute-position function, so its
     * phase continues seamlessly) has taken over.
     */
    fun outroSettleAlpha(positionMs: Long, lastLyricEndMs: Long?): Float? {
        if (lastLyricEndMs == null) return null
        val t = positionMs - lastLyricEndMs - OUTRO_HOLD_MS
        if (t < 0 || t >= OUTRO_SETTLE_MS) return null
        return t.toFloat() / OUTRO_SETTLE_MS.toFloat()
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
    const val INVITE_PERIOD_MS = 4_000L

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
