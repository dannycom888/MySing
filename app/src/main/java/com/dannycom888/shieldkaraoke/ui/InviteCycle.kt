package com.dannycom888.shieldkaraoke.ui

import kotlin.math.floor

/**
 * Frame stepping for the invite and clap animations (v1.0.50;
 * the invite half was designed for the folded v1.0.49 and ships
 * here together with the bow).
 *
 * Until v1.0.48 the invite was ONE still frame per dancer plus a
 * slow whole-body bow ([DancerActivity.invitePose]), and the
 * applause row clapped with a still frame and a bouncing pose.
 * The user asked for the gestures themselves to move: each
 * dancer now has a six-frame invite sequence (drawn as one
 * chained gesture: the arm rises and extends into the "please"
 * pose), two bow frames continuing it (half-bent, then fully
 * bowed — "邀请时，他们能弯腰吗"), and a two-frame clap (hands
 * apart / hands together). This object decides WHICH frame is on
 * screen at a given clock reading — a pure function of the
 * clock, like [PoseCycle] — so pausing freezes the gesture
 * mid-frame exactly like it freezes the dance, and every caller
 * (solo intro invite, ceremony group invite, keeper solo
 * invite, applause rows) steps in lockstep off its own clock.
 */
object InviteCycle {

    /** Frames in the drawn invite gesture proper. */
    const val INVITE_COUNT = 6

    /** Bow frames after the gesture: half-bent, fully bowed. */
    const val BOW_COUNT = 2

    /** Total drawn frames in one figure's invite chain. */
    const val CHAIN_FRAMES = INVITE_COUNT + BOW_COUNT

    /**
     * The chain the invite plays, as indices into a figure's
     * combined frame list (invite frames 0..5, then bow frames
     * at 6 and 7): out through the gesture, into the bow, and
     * the fully-bowed frame TWICE — the ping-pong below then
     * shows it for three consecutive steps (arrive, hold, start
     * back), the "多停一拍" at the bottom of the bow, before
     * the chain melts back to rest.
     */
    val CHAIN: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6, 7, 7)

    /**
     * How long each chain step stays up, in ms of the caller's
     * clock. The ping-pong cycle is 2 × (9 − 1) = 16 steps, so
     * one full out-and-back gesture takes 16 × 250 ms = 4.0 s —
     * the same period as the old whole-body bow
     * ([DancerActivity.INVITE_PERIOD_MS]), which the frames now
     * replace: the frames carry the gesture AND the bow, the
     * whole-body pose stays neutral. 250 ms also makes the
     * ceremony's 2 s invite segments play the gesture exactly
     * out to the bow's peak (8 steps) instead of dying mid-arm.
     */
    const val FRAME_MS = 250L

    /** The chain position at step [step] (ping-pong over [CHAIN]). */
    private fun chainAt(step: Long): Int {
        val n = CHAIN.size
        val cycle = 2L * (n - 1)
        val pos = ((step % cycle) + cycle) % cycle
        return CHAIN[if (pos < n) pos.toInt() else (cycle - pos).toInt()]
    }

    /**
     * Which invite-chain frame is on screen at [elapsedMs]: the
     * gesture goes out and comes back instead of snapping from
     * the extended pose to the rest pose at the wrap. The result
     * indexes the figure's combined list (invite 0..5, bow 0..1
     * at 6..7).
     */
    fun frameIndex(elapsedMs: Long): Int {
        val step = floor(elapsedMs.coerceAtLeast(0) / FRAME_MS.toDouble()).toLong()
        return chainAt(step)
    }

    /**
     * The cross-fade pair for the invite chain at [elapsedMs]:
     * the same shape as [PoseCycle.blend] — the current step's
     * frame fades in over the first [PoseCycle.FADE_FRACTION] of
     * its step, the rest of the step is the current frame alone —
     * with both indices mapped through the ping-pong chain, so
     * the fade melts between gesture neighbours in whichever
     * direction the gesture is travelling. Where the chain holds
     * a frame (the bow's peak), previous == current and the
     * caller draws the single frame.
     */
    fun blend(elapsedMs: Long): PoseCycle.Blend {
        val t = elapsedMs.coerceAtLeast(0).toDouble()
        val step = floor(t / FRAME_MS).toLong()
        val phase = (t - step * FRAME_MS) / FRAME_MS // 0..1 inside the step
        val alpha = (phase / PoseCycle.FADE_FRACTION).coerceIn(0.0, 1.0).toFloat()
        return PoseCycle.Blend(chainAt(step - 1), chainAt(step), alpha)
    }

    /**
     * The blend a profile actually gets: humans cross-fade
     * ([blend]); the ROBOT returns null — his changes are hard
     * stepped cuts, the same exemption he has from the dance
     * cross-fade (v1.0.10) and the outro settle (v1.0.22). A null
     * here is the hard-cut marker: the caller draws
     * [frameIndex] alone.
     */
    fun blendFor(
        profile: DanceMotion.Profile,
        elapsedMs: Long,
    ): PoseCycle.Blend? =
        if (profile == DanceMotion.Profile.ROBOT) null else blend(elapsedMs)
}

/**
 * The two-frame clap (v1.0.50): frame 0 = hands apart (the open,
 * about-to-clap position), frame 1 = hands together. Alternating
 * the pair at a natural hand-clap rate is the whole animation —
 * the figures were drawn for exactly this, bodies still, only
 * the hands moving, so the caller pairs it with a neutral pose
 * (the old still-frame bounce, [DanceMotion.clapPose], is
 * retired from the stage: its 300 ms period would drift against
 * the frames' own alternation).
 */
object ClapCycle {

    /**
     * How long each clap frame stays up: 375 ms — the middle of
     * the natural-clap band the user set (350–400 ms per frame),
     * so a full apart→together→apart cycle takes 750 ms, about
     * 80 claps a minute: a brisk but unhurried applause.
     */
    const val FRAME_MS = 375L

    /** Which clap frame is up at [elapsedMs]: 0 (apart) for the
     *  first [FRAME_MS], 1 (together) for the next, alternating.
     *  Pure function of the clock — pausing freezes the hands. */
    fun frameAt(elapsedMs: Long): Int {
        val step = floor(elapsedMs.coerceAtLeast(0) / FRAME_MS.toDouble()).toLong()
        return (step % 2L).toInt()
    }
}
