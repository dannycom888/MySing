package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.state.DancerKeys

/**
 * The song-changeover handover ceremony (v1.0.23).
 *
 * The user's idea (2026-11-15): when a song finishes *fully* and a
 * real ordered song is waiting, the gap between songs is a little
 * stage ceremony instead of a plain countdown —
 *
 *  1. all six dancers come out in a row and clap along with the
 *     applause sound, while a big centred card announces the next
 *     song (who ordered it, singer – title);
 *  2. the applause settles and all six make one slow invite
 *     gesture together;
 *  3. five of them fade away, leaving only the dancer the *next*
 *     song's orderer chose, who cross-fades into the regular
 *     dancer position at the left of the lyrics;
 *  4. the keeper alone makes one more invite gesture;
 *  5. the next song starts, the keeper already in place — straight
 *     into its intro invite when the song has one, straight into
 *     dancing when it has not.
 *
 * The ceremony runs ONLY on that path. No next song (home / idle
 * filler follows) keeps the plain applause send-off, and a song
 * quit with 不唱了 gets neither applause nor ceremony, exactly as
 * before.
 *
 * Everything here is a pure function of the elapsed wall time
 * since the ceremony started, so the whole timeline is
 * unit-testable without a screen; the Activity only feeds it
 * elapsed time and renders what it says.
 */
object Ceremony {

    /** Segment 1 — the six clap. Exactly the applause sound's
     *  length (applause.wav is 3.8 s; see MainActivity.APPLAUSE_MS). */
    const val CLAP_MS = 3_800L

    /** Segment 2 — all six invite together: one full bow gesture. */
    const val GROUP_INVITE_MS = 2_000L

    /** Segment 3 — the five fade out while the keeper cross-fades
     *  into the regular dancer position. */
    const val EXIT_MS = 600L

    /** Segment 4 — the keeper alone invites once more. */
    const val SOLO_INVITE_MS = 2_000L

    /** The whole ceremony: ≈ 8.5 s (3.8 + 2 + 0.6 + 2). */
    const val TOTAL_MS = CLAP_MS + GROUP_INVITE_MS + EXIT_MS + SOLO_INVITE_MS

    enum class Phase { CLAPPING, GROUP_INVITE, EXITING, SOLO_INVITE, DONE }

    /** The phase at [elapsedMs] since the ceremony started.
     *  Negative elapsed (clock read before the start stamp) reads
     *  as the opening clap. */
    fun phaseAt(elapsedMs: Long): Phase = when {
        elapsedMs < CLAP_MS -> Phase.CLAPPING
        elapsedMs < CLAP_MS + GROUP_INVITE_MS -> Phase.GROUP_INVITE
        elapsedMs < CLAP_MS + GROUP_INVITE_MS + EXIT_MS -> Phase.EXITING
        elapsedMs < TOTAL_MS -> Phase.SOLO_INVITE
        else -> Phase.DONE
    }

    /** Progress 0..1 within the phase [elapsedMs] falls in
     *  (1 once DONE). */
    fun phaseProgress(elapsedMs: Long): Float {
        val (start, length) = when (phaseAt(elapsedMs)) {
            Phase.CLAPPING -> 0L to CLAP_MS
            Phase.GROUP_INVITE -> CLAP_MS to GROUP_INVITE_MS
            Phase.EXITING -> (CLAP_MS + GROUP_INVITE_MS) to EXIT_MS
            Phase.SOLO_INVITE -> (CLAP_MS + GROUP_INVITE_MS + EXIT_MS) to SOLO_INVITE_MS
            Phase.DONE -> return 1f
        }
        return ((elapsedMs - start).toFloat() / length.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Alpha of the celebration-row figures during the exit: 1
     * until EXITING, easing linearly to 0 across it, 0 from
     * SOLO_INVITE on. All six row copies fade — the five leave for
     * good, and the keeper's row copy melts into its solo-position
     * copy (see [keeperSoloAlpha]) so the handover is a dissolve,
     * never a cut.
     */
    fun leavingAlpha(elapsedMs: Long): Float = when (phaseAt(elapsedMs)) {
        Phase.EXITING -> 1f - phaseProgress(elapsedMs)
        Phase.SOLO_INVITE, Phase.DONE -> 0f
        else -> 1f
    }

    /**
     * Alpha of the keeper standing in the regular dancer position:
     * 0 until EXITING, rising to 1 across it (the mirror of
     * [leavingAlpha]), 1 from SOLO_INVITE on — so when the next
     * song starts and normal playback rendering takes over at the
     * very same spot, nothing jumps.
     */
    fun keeperSoloAlpha(elapsedMs: Long): Float = when (phaseAt(elapsedMs)) {
        Phase.EXITING -> phaseProgress(elapsedMs)
        Phase.SOLO_INVITE, Phase.DONE -> 1f
        else -> 0f
    }

    /**
     * One complete invite gesture — lean in and return — fitted
     * into [phaseLengthMs]: the ceremony's gestures run about
     * 2 s each, so [DancerActivity.invitePose]'s 4 s bow cycle is
     * played at double rate, keeping its exact shape and
     * amplitudes (and the robot's half amplitude). At the phase
     * boundaries the lean is 0, so consecutive segments join
     * without a posture snap.
     */
    fun invitePose(
        profile: DanceMotion.Profile,
        elapsedInPhaseMs: Long,
        phaseLengthMs: Long,
    ): DanceMotion.Pose {
        if (phaseLengthMs <= 0L) return DancerActivity.invitePose(0L, profile)
        val scaled = elapsedInPhaseMs.coerceAtLeast(0L) *
            DancerActivity.INVITE_PERIOD_MS / phaseLengthMs
        return DancerActivity.invitePose(scaled, profile)
    }

    /**
     * Which of the six dancers keeps the stage: the one the next
     * song's orderer chose. [dancerKey] is the stored per-orderer
     * choice (see Prefs.dancerFor); an unset or unknown key falls
     * back to the default dancer (Amy, index 0), the same
     * fallback Dancers.byKey applies. Resolution is by key only —
     * custom display names (v1.0.21) never affect who dances.
     */
    fun keeperIndex(dancerKey: String?): Int =
        DancerKeys.ALL.indexOf(dancerKey).takeIf { it >= 0 } ?: 0

    /** What the driver should do when a segment boundary is
     *  crossed and the ceremony's lock is re-checked. */
    enum class BoundaryAction {
        /** The locked next song is still the queue's current song:
         *  carry on with the ceremony. */
        CONTINUE,

        /** The locked song is gone but another song now heads the
         *  queue: drop the ceremony and start that song at once. */
        START_CURRENT,

        /** The queue has no current song at all anymore: drop the
         *  ceremony and fall back to the idle path (filler / home). */
        END_TO_IDLE,
    }

    /**
     * The interruption rule (v1.0.23): the ceremony is locked to
     * the next song chosen when it started. Queue edits that only
     * touch the *waiting* list (delete / promote / new orders)
     * never disturb it. If, at a segment boundary, the locked song
     * is no longer the queue's current song, the ceremony aborts
     * at that boundary and the driver re-judges by whatever heads
     * the queue then ([BoundaryAction.START_CURRENT] /
     * [BoundaryAction.END_TO_IDLE]).
     */
    fun boundaryAction(lockedStillCurrent: Boolean, hasCurrent: Boolean): BoundaryAction =
        when {
            lockedStillCurrent -> BoundaryAction.CONTINUE
            hasCurrent -> BoundaryAction.START_CURRENT
            else -> BoundaryAction.END_TO_IDLE
        }
}
