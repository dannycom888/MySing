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
     * starts at or after [MIN_INTRO_MS]; an earlier first sung line
     * (or no LRC at all) means dancing from the very beginning.
     * The start in question is the first SUNG line's — see
     * [firstSungStartMs] for why the file's first line cannot be
     * trusted with this.
     */
    fun hasIntro(firstSungStartMs: Long?): Boolean =
        firstSungStartMs != null && firstSungStartMs >= MIN_INTRO_MS

    /**
     * A credit label at the head of a timed line: 作词/作曲/编曲…
     * or the English "Lyrics by / Composed by …" shapes. The label
     * must be followed by a colon (either width) — a sung line that
     * merely starts with the same character (词不达意， 曲终人散）
     * carries no colon and stays a lyric.
     */
    private val creditLabelRegex = Regex(
        """^(作词人|作曲人|作词|作曲|编曲|词曲|制作人|制作|监制|演唱|原唱|和声|混音|录音|出品|发行|词|曲)\s*[:：]"""
    )
    private val creditByRegex = Regex(
        """^(lyrics|music|words|composed|arranged|performed|produced|sung)\s+by\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditColonEnRegex = Regex(
        """^(lyrics|music|words|composer|arranger|performer|producer)\s*[:：]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Is this timed line a credit line （词：/曲：/作词 …) rather
     * than a sung lyric? This library's converter writes a header
     * block of timed credit lines at the head of every enhanced
     * LRC — a title line, then 词/曲 lines — before the first sung
     * line. Nobody sings them, so they must not pose as the start
     * of the singing ([firstSungStartMs] skips them) — and since
     * v1.0.74 they are not the audience's business either:
     * [displayLines] drops them from the stage's lyric list
     * outright (user report: "歌词的作曲者、作词者还在" — most of
     * the library's files still carry the lines, and hiding them
     * in the app covers every file at once). The label set
     * includes the 作词人/作曲人 forms alongside 词/曲/作词/作曲.
     */
    fun isCreditLine(text: String): Boolean {
        val t = text.trim()
        return creditLabelRegex.containsMatchIn(t) ||
            creditByRegex.containsMatchIn(t) ||
            creditColonEnRegex.containsMatchIn(t)
    }

    /**
     * The lines the stage actually shows: the parsed lyrics with
     * every credit line removed (v1.0.74, see [isCreditLine]).
     * The match runs on the line's text, which the parser has
     * already stripped of its inline word tags, so an enhanced
     * credit line is judged by its words alone. Everything else
     * keeps its order and timing — the title card stays (it is
     * the song's name, not a credit), and because credit lines
     * never counted as sung lines, the first-sung start and the
     * dance phases derived from the filtered list are exactly
     * what they were over the raw list.
     */
    fun displayLines(lines: List<LyricLine>): List<LyricLine> =
        lines.filterNot { isCreditLine(it.text) }

    /** Whitespace hugging a hyphen — insignificant in a title
     *  card (v1.0.36 — see [isTitleLine]). */
    private val hyphenSpacing = Regex("""\s*-\s*""")

    /**
     * Is this line the song's title card — the header line whose
     * text is exactly the song's own artist and title joined by
     * a hyphen, in either order ("Beyond - 海阔天空" in the file
     * name, "海阔天空 - Beyond" inside the LRC)? Only the pair
     * match counts: a sung line that merely contains a hyphen is
     * a lyric, and without the song's artist/title nothing is
     * guessed.
     *
     * The spacing around the hyphen is NOT significant
     * (v1.0.36): this library's file names are "歌手 -歌名"
     * (space before the hyphen, none after), and the title
     * cards inside the LRCs mirror the file name — 张宇《雨一直下》
     * opens with "张宇 -雨一直下" at 0.60 s. Splitting on the
     * exact " - " missed those cards, so the title line posed
     * as the first sung line and a 40-second intro got no
     * invite (user report, 2026-11-15). The comparison
     * therefore squeezes the whitespace around hyphens on
     * both sides and then compares the WHOLE line against
     * the pair — never a split into parts, so a name that
     * itself carries a hyphen (Toy-Box, a title ending in
     * "Candy-") still matches exactly. The strictness that
     * matters is unchanged: the line must still be exactly
     * the song's own pair, nothing looser.
     */
    fun isTitleLine(text: String, artist: String?, title: String?): Boolean {
        if (artist.isNullOrBlank() || title.isNullOrBlank()) return false
        val line = hyphenSpacing.replace(text.trim(), "-")
        val a = artist.trim()
        val t = title.trim()
        return line.equals(hyphenSpacing.replace("$a - $t", "-"), ignoreCase = true) ||
            line.equals(hyphenSpacing.replace("$t - $a", "-"), ignoreCase = true)
    }

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
     * When the singing starts in [lines]: the first SUNG line's
     * start, skipping the converter's header block (title card +
     * credit lines — see [isCreditLine] / [isTitleLine]).
     *
     * v1.0.30 root cause: this used to be simply the first line's
     * start, but the header block sits at 0–2 s in nearly every
     * file of this library while the singing starts 10–30 s in —
     * so [hasIntro] judged almost every song "no intro" and the
     * invite NEVER appeared (measured over the real library:
     * 924 of 1,092 songs have a first sung line at ≥ 2.5 s hidden
     * behind a header first line at < 2.5 s; only 12 headerless
     * songs could ever invite). Header lines are skipped only
     * while they lead the file — the scan stops at the first
     * line that is neither a credit nor the title card.
     * No sung lines at all → null (no intro phase exists —
     * symmetric with [lastLyricEndMs] being null for the outro).
     */
    fun firstSungStartMs(lines: List<LyricLine>, artist: String?, title: String?): Long? {
        for (line in lines) {
            if (isCreditLine(line.text) || isTitleLine(line.text, artist, title)) continue
            return line.timeMs
        }
        return null
    }

    /**
     * The phase at [positionMs]: before the first sung line →
     * INVITING (the invite frame + slow bow) — but only when the
     * song actually has an intro (see [hasIntro]; a first sung
     * line starting before [MIN_INTRO_MS] means dancing from 0);
     * the first [OUTRO_HOLD_MS] past the last lyric's end →
     * OUTRO_HOLD (last dance pose held, settling to neutral);
     * beyond that → STILL (standing frame + breathing); in
     * between → DANCING. A song without an LRC has neither
     * boundary, so it dances the whole song rather than inviting
     * or stopping by mistake.
     */
    fun phase(
        positionMs: Long,
        firstSungStartMs: Long?,
        lastLyricEndMs: Long?,
        durationMs: Long? = null,
    ): Phase = when {
        hasIntro(firstSungStartMs) && positionMs < firstSungStartMs!! -> Phase.INVITING
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

    // ---- v1.0.32: the intro step-forward --------------------------
    //
    // The user's staging note: "邀请的时候伴舞向前一大步，变大。
    // 跳舞时再退后" — while she invites during the intro she takes
    // one big step toward the audience and stands larger; the
    // moment the singing starts she steps back to her dancing
    // size. The renderer turns this progress into a LAYOUT height
    // change (dancing height 0.80 of the stage → 0.95, compact
    // 0.72 → 0.88), so she grows upward from the stage floor (her
    // feet stay planted on the bottom edge) and the spotlight
    // pool inside her wrapper — sized off her own rect — grows
    // with her. This function only decides HOW FAR the step has
    // progressed at a playback position; it is a pure function of
    // position like everything else here, so pausing freezes the
    // step mid-stride exactly like it freezes the bow.

    /** How long stepping forward (and stepping back) takes, in ms
     *  of playback position. */
    const val STEP_RAMP_MS = 700L

    /** Hard size levels the robot's step is quantised to, both
     *  ends included: 0, 1/3, 2/3, 1 — he approaches in mechanical
     *  steps, like everything else he does (v1.0.10's stepped
     *  frame cuts, the quarter-beat pose table). */
    const val ROBOT_STEP_LEVELS = 4

    /**
     * Step-forward progress at [positionMs], 0 (dancing size) to
     * 1 (fully stepped forward): ramps up across the intro's
     * first [STEP_RAMP_MS] with a smoothstep, holds at 1 for the
     * rest of the intro, then ramps back down across the first
     * [STEP_RAMP_MS] after the first sung line starts. Songs
     * without an intro — no LRC, or a first sung line before
     * [MIN_INTRO_MS] — never step (they have no INVITING phase
     * to step in), and the outro phases sit long past the
     * step-back window, so they read 0 as well. The ramp window
     * is capped at half the intro length so the up and down
     * ramps can never overlap (defensive: [hasIntro] already
     * guarantees an intro of at least 2.5 s against a 0.7 s
     * ramp). The robot's progress is quantised into
     * [ROBOT_STEP_LEVELS] hard levels.
     */
    fun stepProgress(
        positionMs: Long,
        firstSungStartMs: Long?,
        profile: DanceMotion.Profile,
    ): Float {
        if (!hasIntro(firstSungStartMs)) return 0f
        val start = firstSungStartMs!!
        val ramp = minOf(STEP_RAMP_MS, start / 2).coerceAtLeast(1L)
        val raw = when {
            positionMs < 0L -> 0f
            positionMs < ramp -> stepSmooth(positionMs.toFloat() / ramp.toFloat())
            positionMs < start -> 1f
            positionMs < start + ramp ->
                1f - stepSmooth((positionMs - start).toFloat() / ramp.toFloat())
            else -> 0f
        }
        return if (profile == DanceMotion.Profile.ROBOT) {
            val steps = (ROBOT_STEP_LEVELS - 1).toFloat()
            (kotlin.math.round(raw * steps) / steps).coerceIn(0f, 1f)
        } else {
            raw
        }
    }

    /** Smoothstep 0→1 (same curve [outroHoldPose] settles with). */
    private fun stepSmooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
