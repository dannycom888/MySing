package com.dannycom888.shieldkaraoke.audio

/**
 * Which audio source an accompaniment request becomes (v1.0.47),
 * kept pure so the matrix is unit-testable. Decoupled from the
 * player builds on purpose: a paired accompaniment FILE needs no
 * audio processor at all, so it must be reachable on every build
 * — including the bare plain-retry lifeboat, where the realtime
 * processor does not exist.
 *
 *  - accompaniment off → the original file, no processing;
 *  - accompaniment on + a paired file exists → the FILE (already
 *    vocal-free; running it through the cancel processor too
 *    would only damage it);
 *  - accompaniment on + no file → the original through the
 *    realtime crossover cancellation (Plan B), where the build
 *    offers it.
 *
 * The on/off intent itself comes from the caller: at song start
 * it is [com.dannycom888.shieldkaraoke.queue.AccompanimentPolicy]'s
 * per-source default (ordered songs on, filler off); afterwards
 * the singer's manual toggle rules for that song only. This
 * object never decides intent — only what the intent plays.
 */
object PlaybackSourcePolicy {

    data class Choice(
        /** Play the paired accompaniment file instead of the original. */
        val useAccompanimentFile: Boolean,
        /** Run the original through the realtime vocal-cancel processor. */
        val useRealtimeCancel: Boolean,
    )

    fun choose(hasAccompanimentFile: Boolean, accompanimentOn: Boolean): Choice = when {
        !accompanimentOn -> Choice(useAccompanimentFile = false, useRealtimeCancel = false)
        hasAccompanimentFile -> Choice(useAccompanimentFile = true, useRealtimeCancel = false)
        else -> Choice(useAccompanimentFile = false, useRealtimeCancel = true)
    }
}
